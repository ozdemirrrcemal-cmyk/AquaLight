#!/usr/bin/env python3
"""Protect the shared device-root UI entry, header, navigation and resource architecture."""
from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

DEVICES_VIEW_MODEL = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/DevicesViewModel.kt"
)
DEVICES_FRAGMENT = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/DevicesFragment.kt"
)
ROUTE_RESOLVER = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/route/DeviceRouteResolver.kt"
)
NAV_DEVICES = Path("app/src/main/res/navigation/nav_devices.xml")
DOSING_FRAGMENT = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/root/"
    "DeviceDosingRootFragment.kt"
)
COOLING_FRAGMENT = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/root/"
    "DeviceCoolingRootFragment.kt"
)
COOLING_VIEW_MODEL = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/root/"
    "DeviceCoolingRootViewModel.kt"
)
COOLING_UI_ROOT = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling"
)
LAYOUT_ROOT = Path("app/src/main/res/layout")
DOSING_LAYOUT = LAYOUT_ROOT / "fragment_device_dosing_root.xml"
COOLING_LAYOUT = LAYOUT_ROOT / "fragment_device_cooling_root.xml"

HARD_CODED_ANDROID_TEXT = re.compile(
    r'android:text="(?!@string/|@plurals/|@\{|\?)[^\"]+"'
)
RAW_COLOR = re.compile(r'android:(?:background|textColor|tint)="#[0-9A-Fa-f]{3,8}"')
RAW_TEXT_SIZE = re.compile(r'android:textSize="[0-9.]+sp"')
HARD_CODED_COMPOSE_TEXT = re.compile(r'\bText\s*\(\s*"[^"$]+"')
HARD_CODED_CONTENT_DESCRIPTION = re.compile(r'\bcontentDescription\s*=\s*"[^"$]+"')

COOLING_UI_FORBIDDEN = (
    "import com.aqua.aqualight.data.",
    "import com.aqua.aqualight.platform.",
    "DevicesRepository",
    "DeviceSnapshot",
    "runtimeModules()",
    "connectRuntime(",
)


def _read(repository_root: Path, relative_path: Path, errors: list[str]) -> str:
    path = repository_root / relative_path
    if not path.is_file():
        errors.append(f"{relative_path}: required shared device-root UI file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def _require(
    relative_path: Path,
    source: str,
    errors: list[str],
    token: str,
    reason: str,
) -> None:
    if token not in source:
        errors.append(f"{relative_path}: {reason}: {token}")


def validate_header_contract(
    relative_path: Path,
    source: str,
    *,
    family_string: str,
    settings_description: str,
    current_destination: str,
    directions_action: str,
) -> list[str]:
    errors: list[str] = []
    required = (
        ("binding.appHeader.setupAquaHeader(", "root header must use the shared AquaHeader binder"),
        ("config = AquaHeaderConfig(", "root header must use the shared AquaHeader config"),
        ("titleOverride = state.title.ifBlank {", "root title must remain repository-owned with a resource fallback"),
        (f"getString(R.string.{family_string})", "root fallback title must come from String resources"),
        ("findNavController().navigateUp()", "root back behavior must use the shared navigation host"),
        (
            "statusIcon = state.connectionVisualState.toWifiHeaderStatusIcon(requireContext())",
            "root connection status must use the shared device-presence header presentation",
        ),
        ("AquaHeaderAction(", "settings must remain a shared AquaHeader action"),
        ("iconRes = R.drawable.ic_settings", "settings action must use the shared settings icon"),
        (
            f"R.string.{settings_description}",
            "settings accessibility copy must come from String resources",
        ),
        ("enabled = state.contentEnabled", "settings action must follow the root availability gate"),
        (
            "if (!viewModel.uiState.value.contentEnabled) return",
            "settings navigation must fail closed when root content is unavailable",
        ),
        (
            f"navController.currentDestination?.id != R.id.{current_destination}",
            "settings navigation must verify the current root destination",
        ),
        (directions_action, "settings navigation must use generated Safe Args directions"),
    )
    for token, reason in required:
        _require(relative_path, source, errors, token, reason)

    for forbidden, reason in (
        ("import com.aqua.aqualight.data.", "device data infrastructure must not leak into a root Fragment"),
        ("import com.aqua.aqualight.platform.", "platform infrastructure must not leak into a root Fragment"),
        ("MaterialToolbar", "device roots must not construct a parallel toolbar"),
        ("setSupportActionBar", "device roots must not construct a parallel action bar"),
        ('titleOverride = "', "device-root titles must not be hard-coded"),
        ('contentDescription = "', "device-root accessibility copy must not be hard-coded"),
    ):
        if forbidden in source:
            errors.append(f"{relative_path}: {reason}: {forbidden}")
    return errors


def validate_resource_usage(relative_path: Path, source: str) -> list[str]:
    errors: list[str] = []
    for pattern, reason in (
        (HARD_CODED_ANDROID_TEXT, "Cooling/root XML must use String resources"),
        (RAW_COLOR, "Cooling/root XML must use central color resources"),
        (RAW_TEXT_SIZE, "Cooling/root XML must use central dimension/text resources"),
    ):
        if pattern.search(source):
            errors.append(f"{relative_path}: {reason}")

    for forbidden, reason in (
        ("MaterialToolbar", "device-root layouts must not define a parallel toolbar"),
        ("androidx.appcompat.widget.Toolbar", "device-root layouts must not define a parallel toolbar"),
    ):
        if forbidden in source:
            errors.append(f"{relative_path}: {reason}: {forbidden}")
    return errors


def validate_layout_contract(relative_path: Path, source: str) -> list[str]:
    errors: list[str] = []
    for token, reason in (
        ('android:background="@color/background_color"', "root surface must use the central background resource"),
        ('layout="@layout/layout_aqua_header"', "root layout must include the shared AquaHeader layout"),
        ('android:id="@+id/appHeader"', "root header id must stay canonical"),
    ):
        _require(relative_path, source, errors, token, reason)
    errors.extend(validate_resource_usage(relative_path, source))
    return errors


def validate_cooling_feature_boundaries(repository_root: Path) -> list[str]:
    errors: list[str] = []
    cooling_root = repository_root / COOLING_UI_ROOT
    if not cooling_root.is_dir():
        return [f"{COOLING_UI_ROOT}: Cooling UI root is missing"]

    for path in sorted(cooling_root.rglob("*.kt")):
        source = path.read_text(encoding="utf-8", errors="ignore")
        relative_path = path.relative_to(repository_root)
        for forbidden in COOLING_UI_FORBIDDEN:
            if forbidden in source:
                errors.append(
                    f"{relative_path}: Cooling UI bypasses shared application boundaries: {forbidden}"
                )
        for pattern, reason in (
            (HARD_CODED_COMPOSE_TEXT, "Cooling Compose copy must use String resources"),
            (
                HARD_CODED_CONTENT_DESCRIPTION,
                "Cooling accessibility copy must use String resources",
            ),
        ):
            if pattern.search(source):
                errors.append(f"{relative_path}: {reason}")

    layout_root = repository_root / LAYOUT_ROOT
    if layout_root.is_dir():
        for path in sorted(layout_root.glob("*cooling*.xml")):
            if path == repository_root / COOLING_LAYOUT:
                continue
            source = path.read_text(encoding="utf-8", errors="ignore")
            errors.extend(validate_resource_usage(path.relative_to(repository_root), source))
    return errors


def validate_repository(repository_root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    devices_view_model = _read(repository_root, DEVICES_VIEW_MODEL, errors)
    devices_fragment = _read(repository_root, DEVICES_FRAGMENT, errors)
    route_resolver = _read(repository_root, ROUTE_RESOLVER, errors)
    nav_devices = _read(repository_root, NAV_DEVICES, errors)
    dosing_fragment = _read(repository_root, DOSING_FRAGMENT, errors)
    cooling_fragment = _read(repository_root, COOLING_FRAGMENT, errors)
    cooling_view_model = _read(repository_root, COOLING_VIEW_MODEL, errors)
    dosing_layout = _read(repository_root, DOSING_LAYOUT, errors)
    cooling_layout = _read(repository_root, COOLING_LAYOUT, errors)

    for token, reason in (
        (
            "private val menuOpenUseCase: DeviceMenuOpenUseCase",
            "device clicks must enter through the shared menu-open application use-case",
        ),
        (
            "menuOpenUseCase.resolve(deviceUid)",
            "device clicks must prove access through the shared menu-open application use-case",
        ),
        (
            "routeResolver.resolve(result.access)",
            "approved device access must be mapped by the central route resolver",
        ),
    ):
        _require(DEVICES_VIEW_MODEL, devices_view_model, errors, token, reason)

    for forbidden in (
        "DeviceMenuAccessOperations",
        "DeviceControlSurfacePreparationOperations",
        "menuAccessOperations.resolve(deviceUid)",
        "controlSurfacePreparationOperations.prepare(",
    ):
        if forbidden in devices_view_model:
            errors.append(
                f"{DEVICES_VIEW_MODEL}: device click bypasses the shared menu-open boundary: {forbidden}"
            )

    for token, reason in (
        (
            "OwnerDeviceFamily.COOLING -> DeviceRoute(",
            "Cooling family routing must remain in the central route resolver",
        ),
        (
            "target = DeviceRouteTarget.COOLING_ROOT",
            "Cooling must resolve to the canonical root target",
        ),
    ):
        _require(ROUTE_RESOLVER, route_resolver, errors, token, reason)

    for token, reason in (
        (
            "DeviceRouteTarget.COOLING_ROOT ->",
            "DevicesFragment must consume the canonical Cooling route target",
        ),
        (
            "actionDevicesFragmentToDeviceCoolingRootFragment(",
            "Cooling root navigation must use generated Safe Args directions",
        ),
    ):
        _require(DEVICES_FRAGMENT, devices_fragment, errors, token, reason)

    for token, reason in (
        (
            'android:id="@+id/action_devicesFragment_to_deviceCoolingRootFragment"',
            "Cooling root action must remain in the shared devices navigation graph",
        ),
        (
            'app:destination="@id/deviceCoolingRootFragment"',
            "Cooling root action must target the canonical destination",
        ),
        (
            'android:id="@+id/action_deviceCoolingRootFragment_to_deviceCoolingSettingsFragment"',
            "Cooling settings action must remain in the shared devices navigation graph",
        ),
        (
            'app:destination="@id/deviceCoolingSettingsFragment"',
            "Cooling settings action must target the canonical destination",
        ),
    ):
        _require(NAV_DEVICES, nav_devices, errors, token, reason)

    errors.extend(
        validate_header_contract(
            DOSING_FRAGMENT,
            dosing_fragment,
            family_string="device_family_dosing",
            settings_description="device_dosing_open_settings_description",
            current_destination="deviceDosingRootFragment",
            directions_action="actionDeviceDosingRootFragmentToDeviceDosingSettingsFragment(",
        )
    )
    errors.extend(
        validate_header_contract(
            COOLING_FRAGMENT,
            cooling_fragment,
            family_string="device_family_cooling",
            settings_description="device_cooling_open_settings_description",
            current_destination="deviceCoolingRootFragment",
            directions_action="actionDeviceCoolingRootFragmentToDeviceCoolingSettingsFragment(",
        )
    )

    for token, reason in (
        (
            "private val operations: DeviceRootOperations",
            "Cooling root state must come through the shared root application boundary",
        ),
        ("operations.current(deviceUid)", "Cooling root must read the central root snapshot"),
        ("operations.connect(deviceUid)", "Cooling root connection must remain repository-owned"),
        ("operations.observe(deviceUid)", "Cooling root must observe the central root snapshot"),
        (
            "OwnerDeviceFamily.COOLING",
            "Cooling root availability must validate the application-level family",
        ),
        (
            "OwnerDeviceAvailability.REACHABLE",
            "Cooling root availability must use the application-level reachability state",
        ),
        (
            "DeviceRootCatalogState.VALID",
            "Cooling root availability must fail closed on an invalid commercial catalog",
        ),
        (
            "DeviceConnectionVisualState",
            "Cooling root header state must use the shared connection presentation",
        ),
        ("contentEnabled", "Cooling root must expose one availability gate to the UI shell"),
    ):
        _require(COOLING_VIEW_MODEL, cooling_view_model, errors, token, reason)

    errors.extend(validate_layout_contract(DOSING_LAYOUT, dosing_layout))
    errors.extend(validate_layout_contract(COOLING_LAYOUT, cooling_layout))
    errors.extend(validate_cooling_feature_boundaries(repository_root))
    return errors


def main() -> int:
    errors = validate_repository()
    if errors:
        print("Device root UI architecture guard failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1
    print("Device root UI architecture guard passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
