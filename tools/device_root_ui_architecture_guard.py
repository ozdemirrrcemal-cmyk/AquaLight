#!/usr/bin/env python3
"""Protect the shared device-root UI entry, header, navigation and resource architecture."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
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
NAV_AQUARIUM = Path("app/src/main/res/navigation/nav_aquarium.xml")
MAIN_LAYOUT = Path("app/src/main/res/layout/activity_main.xml")
DOSING_FRAGMENT = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/root/"
    "DeviceDosingRootFragment.kt"
)
COOLING_FRAGMENT = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/presentation/root/"
    "DeviceCoolingRootFragment.kt"
)
COOLING_VIEW_MODEL = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/presentation/root/"
    "DeviceCoolingRootViewModel.kt"
)
COOLING_AVAILABILITY = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/presentation/common/"
    "CoolingConnectionAvailability.kt"
)
COOLING_UI_ROOT = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling"
)
COOLING_PRESENTATION_ROOT = COOLING_UI_ROOT / "presentation"
COOLING_PRESENTATION_AREAS = frozenset(
    {
        "automatic",
        "common",
        "dashboard",
        "history",
        "manual",
        "program",
        "root",
        "settings",
        "status",
    }
)
MAIN_SOURCE_ROOT = Path("app/src/main/java")
LAYOUT_ROOT = Path("app/src/main/res/layout")
DOSING_LAYOUT = LAYOUT_ROOT / "fragment_device_dosing_root.xml"
LIGHT_FRAGMENT = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/root/"
    "DeviceLightRootFragment.kt"
)
LIGHT_VIEW_MODEL = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/root/"
    "DeviceLightRootViewModel.kt"
)
LIGHT_LAYOUT = LAYOUT_ROOT / "fragment_device_light_root.xml"
LIGHT_LIBRARY_FRAGMENT = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/library/"
    "DeviceLightLibraryFragment.kt"
)
LIGHT_LIBRARY_LAYOUT = LAYOUT_ROOT / "fragment_device_light_library.xml"
LIGHT_QUICK_SETUP_FRAGMENT = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/quicksetup/"
    "DeviceLightQuickSetupFragment.kt"
)
LIGHT_QUICK_SETUP_LAYOUT = LAYOUT_ROOT / "fragment_device_light_quick_setup.xml"
LIGHT_APPLICATION_ROOT = Path(
    "app/src/main/java/com/aqua/aqualight/application/devices/light"
)
LIGHT_DATA_ROOT = Path("app/src/main/java/com/aqua/aqualight/data/devices/light")
LIGHT_UI_ROOT = Path("app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light")
LIGHT_PRESENTATION_ROOT = LIGHT_UI_ROOT / "presentation"
LIGHT_APPLICATION_AREAS = frozenset(
    {
        "adaptation",
        "automatic",
        "custom",
        "dashboard",
        "library",
        "manual",
        "quicksetup",
        "system",
    }
)
LIGHT_DATA_AREAS = LIGHT_APPLICATION_AREAS
LIGHT_PRESENTATION_AREAS = frozenset(
    {
        "adaptation",
        "automatic",
        "common",
        "custom",
        "library",
        "manual",
        "quicksetup",
        "root",
        "settings",
        "system",
    }
)
LIGHT_AUTOMATIC_AREAS = frozenset({"editor", "preset", "programs"})
LIGHT_RUNTIME_PROVIDER = Path(
    "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/"
    "DeviceRuntimeModuleProvider.kt"
)
COOLING_LAYOUT = LAYOUT_ROOT / "fragment_device_cooling_root.xml"
TIMER_FRAGMENT = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/timer/presentation/root/"
    "DeviceTimerRootFragment.kt"
)
TIMER_VIEW_MODEL = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/timer/presentation/root/"
    "DeviceTimerRootViewModel.kt"
)
TIMER_CHANNEL_CARD = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/timer/presentation/dashboard/"
    "DeviceTimerChannelCard.kt"
)
TIMER_CHANNEL_SCREEN = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/timer/presentation/channel/"
    "DeviceTimerChannelScreen.kt"
)
TIMER_UI_ROOT = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/timer"
)
TIMER_LAYOUT = LAYOUT_ROOT / "fragment_device_timer_root.xml"

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

COOLING_ROOT_PACKAGE_IMPORT = (
    "import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root."
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


def validate_layout_contract(
    relative_path: Path,
    source: str,
    *,
    background_owned_by_shell: bool = False,
) -> list[str]:
    errors: list[str] = []
    background_token = 'android:background="@color/background_color"'
    required = (
        ('layout="@layout/layout_aqua_header"', "root layout must include the shared AquaHeader layout"),
        ('android:id="@+id/appHeader"', "root header id must stay canonical"),
    )
    if background_owned_by_shell:
        if background_token in source:
            errors.append(
                f"{relative_path}: shell-owned root must inherit the central background "
                "instead of painting a duplicate surface"
            )
    else:
        required = (
            (background_token, "root surface must use the central background resource"),
            *required,
        )
    for token, reason in required:
        _require(relative_path, source, errors, token, reason)
    errors.extend(validate_resource_usage(relative_path, source))
    return errors


def validate_timer_control_surface(
    layout_source: str,
    fragment_source: str,
    view_model_source: str,
    channel_card_source: str | None = None,
    channel_screen_source: str | None = None,
) -> list[str]:
    """Require the Timer dashboard while preserving its application-owned control boundary."""
    errors: list[str] = []
    try:
        root = ET.fromstring(layout_source)
    except ET.ParseError as error:
        return [f"{TIMER_LAYOUT}: Timer root layout is invalid XML: {error}"]

    child_tags = [child.tag for child in root]
    if (
        len(root) != 2
        or sum(tag.endswith("include") for tag in child_tags) != 1
        or sum(tag.endswith("ComposeView") for tag in child_tags) != 1
    ):
        errors.append(
            f"{TIMER_LAYOUT}: Timer root must contain the shared header and one ComposeView body"
        )

    for token, reason in (
        (
            'android:id="@+id/timerDashboardCompose"',
            "Timer dashboard ComposeView id must stay canonical finder-owned",
        ),
        (
            "setupDashboardContent()",
            "Timer root must install its dashboard composition",
        ),
        (
            "collectAsStateWithLifecycle()",
            "Timer dashboard must collect state with lifecycle awareness",
        ),
        (
            "DeviceTimerDashboardScreen(",
            "Timer root must render the firmware-backed dashboard",
        ),
        (
            "onChannelClick = ::openChannel",
            "Timer dashboard channels must enter the channel-scoped control surface",
        ),
        (
            "onPowerClick = viewModel::togglePower",
            "Timer power must send the direct persistent manual command",
        ),
    ):
        source = layout_source if token.startswith("android:id") else fragment_source
        _require(TIMER_LAYOUT if source is layout_source else TIMER_FRAGMENT, source, errors, token, reason)

    for forbidden in (
        "tvProductName",
        "tvDeviceUid",
        "tvConnectionStatus",
        "tvIp",
        "tvFirmware",
        "tvModel",
        "tvPrimaryCount",
        "tvFeatures",
        "tvPrimarySection",
        "tvSecondarySection",
    ):
        if forbidden in layout_source or forbidden in fragment_source:
            errors.append(
                f"{TIMER_LAYOUT}: removed Timer overview data must not return: {forbidden}"
            )

    for token, reason in (
        (
            "private val timerControlOperations: DeviceTimerControlOperations",
            "Timer control state must enter through the application boundary",
        ),
        (
            "private val controlSurfacePreparationOperations: "
            "DeviceControlSurfacePreparationOperations",
            "Timer restore must use the shared preparation boundary",
        ),
        (
            "timerControlOperations.currentControl(deviceUid)",
            "Timer must read only authoritative application snapshots",
        ),
        (
            "timerControlOperations.observeControl(deviceUid)",
            "Timer must observe the central application projection",
        ),
        (
            "family = OwnerDeviceFamily.TIMER",
            "Timer preparation must use its exact application family",
        ),
        (
            "contentEnabled = rootAvailable && controlAvailable && "
            "!surfacePreparationPending",
            "Timer interactions must remain fail closed during preparation",
        ),
        (
            "showBlockingPreparation = surfacePreparationPending",
            "Timer root must expose the shared blocking preparation state",
        ),
        (
            "data class DeviceTimerControlUiState(",
            "Timer firmware-independent snapshots must be mapped to presentation state",
        ),
        (
            "timerControlOperations.executePowerMutation(deviceUid, slotId, mutation)",
            "Timer power mutations must use the shared mode-aware application operation",
        ),
        (
            "fun togglePower(slotId: String)",
            "Timer dashboard power must derive ON or OFF from authoritative channel state",
        ),
        (
            "pendingChannelSlotIds",
            "Timer channel mutations must expose derived pending presentation state",
        ),
    ):
        _require(TIMER_VIEW_MODEL, view_model_source, errors, token, reason)

    for forbidden in (
        "import com.aqua.aqualight.data.devices",
        "runtime.modules.timer",
        "DevicesRepository",
        "DeviceTimerRuntimeRepository",
        "DeviceTimerRuntimeStateStore",
    ):
        if forbidden in view_model_source or forbidden in fragment_source:
            errors.append(
                f"{TIMER_VIEW_MODEL}: Timer UI bypasses application boundaries: {forbidden}"
            )
    if "openManualControl" in fragment_source:
        errors.append(
            f"{TIMER_FRAGMENT}: Timer power must not retain a navigation path to timed control"
        )

    if channel_card_source is not None:
        for token, reason in (
            (
                "onClick = actions.onChannelClick",
                "Timer channel details must be owned by the arrow action",
            ),
            (
                ".size(AquaTimerDashboardGeometry.metadataActionSize)",
                "Timer detail arrow must retain a full touch target",
            ),
            (
                "R.string.device_timer_program_power_turn_on_description",
                "Timer power accessibility copy must distinguish program-preserving action",
            ),
        ):
            _require(TIMER_CHANNEL_CARD, channel_card_source, errors, token, reason)
        surface_match = re.search(
            r"AquaDeviceCardSurface\(\s*modifier\s*=\s*(.*?)\n\s*\)\s*\{",
            channel_card_source,
            re.DOTALL,
        )
        if surface_match is None or ".clickable(" in surface_match.group(1):
            errors.append(
                f"{TIMER_CHANNEL_CARD}: Timer card surface must remain non-clickable; "
                "only power and arrow actions may handle taps"
            )
    if (
        channel_screen_source is not None
        and channel_screen_source.count("TimerDivider(colors)") < 3
    ):
        errors.append(
            f"{TIMER_CHANNEL_SCREEN}: Timer channel settings must separate all four rows "
            "with the central TimerDivider"
        )
    return errors


def validate_timer_feature_boundaries(repository_root: Path) -> list[str]:
    errors: list[str] = []
    timer_root = repository_root / TIMER_UI_ROOT
    if not timer_root.is_dir():
        return [f"{TIMER_UI_ROOT}: Timer UI root is missing"]

    has_shared_card_surface = False
    has_central_timer_style = False
    for path in sorted(timer_root.rglob("*.kt")):
        source = path.read_text(encoding="utf-8", errors="ignore")
        relative_path = path.relative_to(repository_root)
        has_shared_card_surface = has_shared_card_surface or "AquaDeviceCardSurface" in source
        has_central_timer_style = has_central_timer_style or (
            "com.aqua.aqualight.ui.common.timer" in source
        )
        for forbidden in (
            "import com.aqua.aqualight.data.devices",
            "runtime.modules.timer",
            "DevicesRepository",
            "DeviceTimerRuntimeRepository",
            "DeviceTimerRuntimeStateStore",
        ):
            if forbidden in source:
                errors.append(
                    f"{relative_path}: Timer UI bypasses application boundaries: {forbidden}"
                )
        for pattern, reason in (
            (HARD_CODED_COMPOSE_TEXT, "Timer Compose copy must use String resources"),
            (
                HARD_CODED_CONTENT_DESCRIPTION,
                "Timer accessibility copy must use String resources",
            ),
        ):
            if pattern.search(source):
                errors.append(f"{relative_path}: {reason}")
        if "Color(0x" in source:
            errors.append(
                f"{relative_path}: Timer UI colors must come from the central style contract"
            )

    if not has_shared_card_surface:
        errors.append(f"{TIMER_UI_ROOT}: Timer dashboard must use AquaDeviceCardSurface")
    if not has_central_timer_style:
        errors.append(f"{TIMER_UI_ROOT}: Timer dashboard must use its central style contract")
    return errors


def validate_cooling_feature_boundaries(repository_root: Path) -> list[str]:
    errors: list[str] = []
    cooling_root = repository_root / COOLING_UI_ROOT
    if not cooling_root.is_dir():
        return [f"{COOLING_UI_ROOT}: Cooling UI root is missing"]

    presentation_root = repository_root / COOLING_PRESENTATION_ROOT
    if not presentation_root.is_dir():
        errors.append(
            f"{COOLING_PRESENTATION_ROOT}: Cooling presentation root is missing"
        )

    for path in sorted(cooling_root.rglob("*.kt")):
        source = path.read_text(encoding="utf-8", errors="ignore")
        relative_path = path.relative_to(repository_root)
        relative_to_cooling = path.relative_to(cooling_root)
        if (
            len(relative_to_cooling.parts) < 3
            or relative_to_cooling.parts[0] != "presentation"
        ):
            errors.append(
                f"{relative_path}: Cooling presentation code must live below "
                f"{COOLING_PRESENTATION_ROOT}"
            )
        elif relative_to_cooling.parts[1] not in COOLING_PRESENTATION_AREAS:
            errors.append(
                f"{relative_path}: Unknown Cooling presentation area: "
                f"{relative_to_cooling.parts[1]}"
            )

        presentation_area = (
            relative_to_cooling.parts[1]
            if len(relative_to_cooling.parts) >= 3
            and relative_to_cooling.parts[0] == "presentation"
            else None
        )
        if (
            presentation_area != "root"
            and COOLING_ROOT_PACKAGE_IMPORT in source
        ):
            errors.append(
                f"{relative_path}: Cooling presentation areas must consume shared models "
                "from presentation.common instead of depending on presentation.root"
            )

        package_match = re.search(r"^package\s+([\w.]+)", source, re.MULTILINE)
        expected_package = ".".join(
            path.parent.relative_to(repository_root / MAIN_SOURCE_ROOT).parts
        )
        if package_match is None or package_match.group(1) != expected_package:
            actual_package = package_match.group(1) if package_match else "<missing>"
            errors.append(
                f"{relative_path}: Package must match Cooling presentation path: "
                f"expected {expected_package}, found {actual_package}"
            )

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


def validate_light_feature_boundaries(repository_root: Path) -> list[str]:
    """Keep every Light destination in its vertical slice and one central runtime owner."""
    errors: list[str] = []

    for root, expected_areas, label in (
        (LIGHT_APPLICATION_ROOT, LIGHT_APPLICATION_AREAS, "application"),
        (LIGHT_DATA_ROOT, LIGHT_DATA_AREAS, "data"),
        (LIGHT_PRESENTATION_ROOT, LIGHT_PRESENTATION_AREAS, "presentation"),
    ):
        absolute_root = repository_root / root
        if not absolute_root.is_dir():
            errors.append(f"{root}: Light {label} root is missing")
            continue
        actual_areas = {path.name for path in absolute_root.iterdir() if path.is_dir()}
        for missing in sorted(expected_areas - actual_areas):
            errors.append(f"{root / missing}: required Light {label} package is missing")
        for unexpected in sorted(actual_areas - expected_areas):
            errors.append(f"{root / unexpected}: unexpected Light {label} package")

    automatic_root = repository_root / LIGHT_PRESENTATION_ROOT / "automatic"
    if automatic_root.is_dir():
        actual_automatic_areas = {
            path.name for path in automatic_root.iterdir() if path.is_dir()
        }
        for missing in sorted(LIGHT_AUTOMATIC_AREAS - actual_automatic_areas):
            errors.append(
                f"{LIGHT_PRESENTATION_ROOT / 'automatic' / missing}: "
                "required Automatic destination package is missing"
            )
        for unexpected in sorted(actual_automatic_areas - LIGHT_AUTOMATIC_AREAS):
            errors.append(
                f"{LIGHT_PRESENTATION_ROOT / 'automatic' / unexpected}: "
                "unexpected Automatic destination package"
            )
        for path in automatic_root.glob("*.kt"):
            errors.append(
                f"{path.relative_to(repository_root)}: Automatic presentation files must live "
                "in programs, editor or preset"
            )

    for source_root in (LIGHT_APPLICATION_ROOT, LIGHT_DATA_ROOT, LIGHT_PRESENTATION_ROOT):
        absolute_root = repository_root / source_root
        if not absolute_root.is_dir():
            continue
        for path in sorted(
            source_path
            for pattern in ("*.kt", "*.java")
            for source_path in absolute_root.rglob(pattern)
        ):
            source = path.read_text(encoding="utf-8", errors="ignore")
            package_match = re.search(r"^package\s+([\w.]+)", source, re.MULTILINE)
            expected_package = ".".join(
                path.parent.relative_to(repository_root / MAIN_SOURCE_ROOT).parts
            )
            if package_match is None or package_match.group(1) != expected_package:
                actual_package = package_match.group(1) if package_match else "<missing>"
                errors.append(
                    f"{path.relative_to(repository_root)}: Package must match Light path: "
                    f"expected {expected_package}, found {actual_package}"
                )

    legacy_roots = (
        LIGHT_UI_ROOT / "presentation/menu",
        Path("app/src/main/java/com/aqua/aqualight/ui/common/light"),
        LIGHT_APPLICATION_ROOT / "control",
        LIGHT_APPLICATION_ROOT / "preset",
        LIGHT_DATA_ROOT / "control",
    )
    for legacy_root in legacy_roots:
        if (repository_root / legacy_root).exists():
            errors.append(f"{legacy_root}: legacy Light package must not return")

    provider = _read(repository_root, LIGHT_RUNTIME_PROVIDER, errors)
    for token, reason in (
        (
            "private val lightStateOwner = DeviceLightRuntimeStateOwner()",
            "Light must construct exactly one owner-scoped runtime state owner",
        ),
        (
            "DeviceLightRuntimeRepository(commandGateway, lightStateOwner)",
            "main Light runtime must share the central state owner",
        ),
        (
            "DeviceLightTemperatureProtectionRuntimeRepository(commandGateway, lightStateOwner)",
            "Light protection runtime must share the central state owner",
        ),
        (
            "DeviceLightThermalRuntimeRepository(commandGateway, lightStateOwner)",
            "Light thermal runtime must share the central state owner",
        ),
    ):
        _require(LIGHT_RUNTIME_PROVIDER, provider, errors, token, reason)

    production_owner_constructions: list[tuple[Path, int]] = []
    main_source_root = repository_root / MAIN_SOURCE_ROOT
    if main_source_root.is_dir():
        for path in main_source_root.rglob("*.kt"):
            source = path.read_text(encoding="utf-8", errors="ignore")
            construction_count = len(
                re.findall(r"\bDeviceLightRuntimeStateOwner\s*\(", source)
            )
            if construction_count:
                production_owner_constructions.append(
                    (path.relative_to(repository_root), construction_count)
                )
            if "DeviceLightThermalRuntimeStateOwner" in source:
                errors.append(
                    f"{path.relative_to(repository_root)}: parallel Light thermal state owner "
                    "must not return"
                )
    if production_owner_constructions != [(LIGHT_RUNTIME_PROVIDER, 1)]:
        errors.append(
            "Light production must construct exactly one state owner only in "
            f"{LIGHT_RUNTIME_PROVIDER}; found {production_owner_constructions}"
        )
    return errors


def validate_repository(repository_root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    devices_view_model = _read(repository_root, DEVICES_VIEW_MODEL, errors)
    devices_fragment = _read(repository_root, DEVICES_FRAGMENT, errors)
    route_resolver = _read(repository_root, ROUTE_RESOLVER, errors)
    nav_devices = _read(repository_root, NAV_DEVICES, errors)
    nav_aquarium = _read(repository_root, NAV_AQUARIUM, errors)
    main_layout = _read(repository_root, MAIN_LAYOUT, errors)
    dosing_fragment = _read(repository_root, DOSING_FRAGMENT, errors)
    light_fragment = _read(repository_root, LIGHT_FRAGMENT, errors)
    light_view_model = _read(repository_root, LIGHT_VIEW_MODEL, errors)
    light_library_fragment = _read(repository_root, LIGHT_LIBRARY_FRAGMENT, errors)
    light_quick_setup_fragment = _read(
        repository_root,
        LIGHT_QUICK_SETUP_FRAGMENT,
        errors,
    )
    cooling_fragment = _read(repository_root, COOLING_FRAGMENT, errors)
    cooling_view_model = _read(repository_root, COOLING_VIEW_MODEL, errors)
    cooling_availability = _read(repository_root, COOLING_AVAILABILITY, errors)
    dosing_layout = _read(repository_root, DOSING_LAYOUT, errors)
    light_layout = _read(repository_root, LIGHT_LAYOUT, errors)
    light_library_layout = _read(repository_root, LIGHT_LIBRARY_LAYOUT, errors)
    light_quick_setup_layout = _read(repository_root, LIGHT_QUICK_SETUP_LAYOUT, errors)
    cooling_layout = _read(repository_root, COOLING_LAYOUT, errors)
    timer_fragment = _read(repository_root, TIMER_FRAGMENT, errors)
    timer_view_model = _read(repository_root, TIMER_VIEW_MODEL, errors)
    timer_channel_card = _read(repository_root, TIMER_CHANNEL_CARD, errors)
    timer_channel_screen = _read(repository_root, TIMER_CHANNEL_SCREEN, errors)
    timer_layout = _read(repository_root, TIMER_LAYOUT, errors)

    for token, reason in (
        (
            "com.aqua.aqualight.ui.main.AquaAppShellLayout",
            "main navigation must remain hosted by the shared app shell",
        ),
        (
            'android:background="@color/background_color"',
            "the app shell must own the central navigation background",
        ),
    ):
        _require(MAIN_LAYOUT, main_layout, errors, token, reason)

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
        (
            'android:id="@+id/action_deviceLightRootFragment_to_deviceLightLibraryFragment"',
            "Light Library action must remain in the shared devices navigation graph",
        ),
        (
            'app:destination="@id/deviceLightLibraryFragment"',
            "Light Library action must target the canonical destination",
        ),
        (
            'android:id="@+id/action_deviceLightRootFragment_to_deviceLightQuickSetupFragment"',
            "Light Quick setup action must remain in the shared devices navigation graph",
        ),
        (
            'app:destination="@id/deviceLightQuickSetupFragment"',
            "Light Quick setup action must target the canonical destination",
        ),
    ):
        _require(NAV_DEVICES, nav_devices, errors, token, reason)

    for token, reason in (
        (
            'android:id="@+id/action_deviceLightRootFragment_to_deviceLightLibraryFragment"',
            "Aquarium navigation must expose the Light Library action",
        ),
        (
            'android:id="@+id/action_deviceLightRootFragment_to_deviceLightQuickSetupFragment"',
            "Aquarium navigation must expose the Light Quick setup action",
        ),
        (
            'app:destination="@id/deviceLightQuickSetupFragment"',
            "Aquarium navigation must target the canonical Light Quick setup destination",
        ),
    ):
        _require(NAV_AQUARIUM, nav_aquarium, errors, token, reason)

    light_dashboard_destinations = (
        "DeviceLightManualControlFragment",
        "DeviceLightAutomaticProgramsFragment",
        "DeviceLightAutomaticProgramEditorFragment",
        "DeviceLightCustomCurveFragment",
        "DeviceLightAdaptationFragment",
        "DeviceLightSystemFragment",
    )
    for destination in light_dashboard_destinations:
        action_token = (
            'android:id="@+id/action_deviceLightRootFragment_to_'
            f'{destination[0].lower()}{destination[1:]}"'
        )
        destination_token = (
            'app:destination="@id/'
            f'{destination[0].lower()}{destination[1:]}"'
        )
        _require(
            NAV_DEVICES,
            nav_devices,
            errors,
            action_token,
            f"Light dashboard action must remain in the devices graph: {destination}",
        )
        _require(
            NAV_DEVICES,
            nav_devices,
            errors,
            destination_token,
            f"Light dashboard action must target its canonical destination: {destination}",
        )
        _require(
            NAV_AQUARIUM,
            nav_aquarium,
            errors,
            action_token,
            f"Light dashboard action must remain in the aquarium graph: {destination}",
        )
        _require(
            LIGHT_FRAGMENT,
            light_fragment,
            errors,
            f"actionDeviceLightRootFragmentTo{destination}(",
            f"Light dashboard navigation must use Safe Args: {destination}",
        )

    errors.extend(
        validate_header_contract(
            LIGHT_FRAGMENT,
            light_fragment,
            family_string="device_family_light",
            settings_description="device_light_open_settings_description",
            current_destination="deviceLightRootFragment",
            directions_action="actionDeviceLightRootFragmentToDeviceLightSettingsFragment(",
        )
    )
    for token, reason in (
        (
            "binding.appHeader.setupAquaHeader(",
            "Light Library must use the shared AquaHeader binder",
        ),
        (
            "config = AquaHeaderConfig(",
            "Light Library must use the shared AquaHeader config",
        ),
        (
            "getString(R.string.device_light_library_title)",
            "Light Library title must come from String resources",
        ),
        (
            "findNavController().navigateUp()",
            "Light Library back behavior must use the shared navigation host",
        ),
    ):
        _require(LIGHT_LIBRARY_FRAGMENT, light_library_fragment, errors, token, reason)

    for forbidden, reason in (
        ("MaterialToolbar", "Light Library must not construct a parallel toolbar"),
        ("setSupportActionBar", "Light Library must not construct a parallel action bar"),
        ('titleOverride = "', "Light Library title must not be hard-coded"),
    ):
        if forbidden in light_library_fragment:
            errors.append(f"{LIGHT_LIBRARY_FRAGMENT}: {reason}: {forbidden}")
    for token, reason in (
        (
            "binding.appHeader.setupAquaHeader(",
            "Light Quick setup must use the shared AquaHeader binder",
        ),
        (
            "config = AquaHeaderConfig(",
            "Light Quick setup must use the shared AquaHeader config",
        ),
        (
            "getString(R.string.device_menu_quick_setup_title)",
            "Light Quick setup title must come from String resources",
        ),
        (
            "findNavController().navigateUp()",
            "Light Quick setup back behavior must use the shared navigation host",
        ),
    ):
        _require(
            LIGHT_QUICK_SETUP_FRAGMENT,
            light_quick_setup_fragment,
            errors,
            token,
            reason,
        )

    for forbidden, reason in (
        ("MaterialToolbar", "Light Quick setup must not construct a parallel toolbar"),
        ("setSupportActionBar", "Light Quick setup must not construct a parallel action bar"),
        ('titleOverride = "', "Light Quick setup title must not be hard-coded"),
    ):
        if forbidden in light_quick_setup_fragment:
            errors.append(f"{LIGHT_QUICK_SETUP_FRAGMENT}: {reason}: {forbidden}")
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
            TIMER_FRAGMENT,
            timer_fragment,
            family_string="device_family_timer",
            settings_description="device_timer_open_settings_description",
            current_destination="deviceTimerRootFragment",
            directions_action="actionDeviceTimerRootFragmentToDeviceTimerSettingsFragment(",
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
            "private val lightControlOperations: DeviceLightControlOperations",
            "Light control state must enter through the application boundary",
        ),
        (
            "private val controlSurfacePreparationOperations: "
            "DeviceControlSurfacePreparationOperations",
            "Light restore must use the shared preparation boundary",
        ),
        (
            "lightControlOperations.currentControl(deviceUid)",
            "Light must read only the central authoritative application snapshot",
        ),
        (
            "lightControlOperations.observeControl(deviceUid)",
            "Light must observe the central authoritative application projection",
        ),
        (
            "family = OwnerDeviceFamily.LIGHT",
            "Light preparation must use its exact application family",
        ),
        (
            "contentEnabled = surfaceAvailable && !surfacePreparationPending",
            "Light interactions must remain fail closed during preparation",
        ),
        (
            "showBlockingPreparation = surfacePreparationPending",
            "Light root must expose the shared blocking preparation state",
        ),
    ):
        _require(LIGHT_VIEW_MODEL, light_view_model, errors, token, reason)

    for token, reason in (
        (
            "viewModel.surfaceUnavailableEvents.collect",
            "Light root must leave an unavailable destination with a typed error",
        ),
        (
            "DeviceMenuUnavailableMessageMapper.messageRes(reason)",
            "Light root must use the shared unavailable reason presentation",
        ),
    ):
        _require(LIGHT_FRAGMENT, light_fragment, errors, token, reason)

    for forbidden in (
        "import com.aqua.aqualight.data.devices",
        "runtime.modules.light",
        "DevicesRepository",
        "DeviceLightRuntimeRepository",
        "DeviceLightRuntimeStateOwner",
    ):
        if forbidden in light_view_model or forbidden in light_fragment:
            errors.append(
                f"{LIGHT_VIEW_MODEL}: Light UI bypasses application boundaries: {forbidden}"
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
        ("isCoolingContentAvailable()", "Cooling root must use the shared availability policy"),
        (
            "DeviceConnectionVisualState",
            "Cooling root header state must use the shared connection presentation",
        ),
        ("contentEnabled", "Cooling root must expose one availability gate to the UI shell"),
    ):
        _require(COOLING_VIEW_MODEL, cooling_view_model, errors, token, reason)

    for token, reason in (
        (
            "OwnerDeviceFamily.COOLING",
            "Cooling availability policy must validate the application-level family",
        ),
        (
            "OwnerDeviceAvailability.REACHABLE",
            "Cooling availability policy must use the application-level reachability state",
        ),
        (
            "DeviceRootCatalogState.VALID",
            "Cooling availability policy must fail closed on an invalid commercial catalog",
        ),
    ):
        _require(COOLING_AVAILABILITY, cooling_availability, errors, token, reason)

    errors.extend(
        validate_layout_contract(
            DOSING_LAYOUT,
            dosing_layout,
            background_owned_by_shell=True,
        )
    )
    errors.extend(
        validate_layout_contract(
            LIGHT_LAYOUT,
            light_layout,
            background_owned_by_shell=True,
        )
    )
    errors.extend(
        validate_layout_contract(
            LIGHT_LIBRARY_LAYOUT,
            light_library_layout,
        )
    )
    errors.extend(
        validate_layout_contract(
            LIGHT_QUICK_SETUP_LAYOUT,
            light_quick_setup_layout,
        )
    )
    errors.extend(
        validate_layout_contract(
            COOLING_LAYOUT,
            cooling_layout,
            background_owned_by_shell=True,
        )
    )
    errors.extend(validate_layout_contract(TIMER_LAYOUT, timer_layout))
    errors.extend(
        validate_timer_control_surface(
            timer_layout,
            timer_fragment,
            timer_view_model,
            timer_channel_card,
            timer_channel_screen,
        )
    )
    errors.extend(validate_timer_feature_boundaries(repository_root))
    errors.extend(validate_cooling_feature_boundaries(repository_root))
    errors.extend(validate_light_feature_boundaries(repository_root))
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
