#!/usr/bin/env python3
"""Enforce Dosing ownership, shared visual boundaries and inward dependency direction."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight"
APPLICATION_DEVICES_ROOT = JAVA_ROOT / "application/devices"
APPLICATION_DOSING_ROOT = APPLICATION_DEVICES_ROOT / "dosing"
DATA_DEVICES_ROOT = JAVA_ROOT / "data/devices"
DATA_DOSING_ROOT = DATA_DEVICES_ROOT / "dosing"
UI_DOSING_ROOT = JAVA_ROOT / "ui/tabs/devices/detail/dosing"
SHARED_DOSING_VISUAL_ROOT = JAVA_ROOT / "ui/common/devicevisual/dosing"

REQUIRED_UI_FILES = (
    "root/DeviceDosingRootFragment.kt",
    "root/DeviceDosingRootViewModel.kt",
    "root/DosingCatalogScreen.kt",
    "presentation/model/DosingWeekday.kt",
    "presentation/pump/DosingPumpSection.kt",
)
REQUIRED_SHARED_UI_FILES = (
    "DosingDeviceIdentityVisual.kt",
    "DosingDeviceVisualViewBinder.kt",
    "DosingPumpDeviceCompose.kt",
    "DosingPumpHeadMarker.kt",
    "DosingPumpIndicatorDrawing.kt",
    "DosingPumpPalette.kt",
)
REQUIRED_DATA_FILES = (
    "DosingChannelSlotFactory.kt",
    "v1/DeviceDosingV1StateOwner.kt",
    "v1/DeviceDosingV1ProductionRuntime.kt",
)

UI_FORBIDDEN_PATTERNS = (
    (re.compile(r"\bcom\.aqua\.aqualight\.data\."), "data-layer dependency"),
    (re.compile(r"\bcom\.aqua\.aqualight\.platform\."), "platform dependency"),
    (re.compile(r"\borg\.json\."), "JSON dependency"),
    (re.compile(r"\bruntime\.modules\b"), "runtime-module dependency"),
    (re.compile(r"\bDeviceDosingV1[A-Za-z0-9_]*\b"), "Dosing v1 wire type"),
    (re.compile(r"\bexpectedRevision\b"), "firmware revision"),
    (re.compile(r"\bchannelKey\b"), "firmware channel key"),
    (re.compile(r"\blowLevelActive\b"), "firmware low-level signal"),
    (
        re.compile(
            r'["\'](?:BAD_REQUEST|INVALID_VALUE|MISSING_FIELD|NOT_FOUND|'
            r'MODULE_NOT_AVAILABLE|FEATURE_NOT_AVAILABLE|DEVICE_BUSY|STORAGE_ERROR|'
            r'HARDWARE_ERROR|UNAUTHORIZED|INTERNAL_ERROR)["\']'
        ),
        "firmware error code",
    ),
)

APPLICATION_FORBIDDEN_REFERENCE = re.compile(
    r"\b(?:android(?:x)?\.|org\.json\.|"
    r"com\.aqua\.aqualight\.(?:composition|data|platform|ui)\.)"
)
DATA_FORBIDDEN_REFERENCE = re.compile(
    r"\bcom\.aqua\.aqualight\.ui\."
)
SHARED_VISUAL_FORBIDDEN_REFERENCE = re.compile(
    r"\bcom\.aqua\.aqualight\.ui\.tabs\.devices\.detail\.dosing\."
)
DOSING_DECLARATION = re.compile(
    r"^[ \t]*(?:(?:public|internal|private|protected|data|sealed|enum|value|fun|abstract|open|final)\s+)*"
    r"(?:class|interface|object)\s+([A-Za-z0-9_]*Dosing[A-Za-z0-9_]*)\b",
    re.MULTILINE,
)
DOSING_STATE_OWNER_DECLARATION = re.compile(
    r"^[ \t]*(?:(?:public|internal|private|protected|data|sealed|open|final)\s+)*"
    r"class\s+([A-Za-z0-9_]*Dosing[A-Za-z0-9_]*StateOwner)\b",
    re.MULTILINE,
)
CANONICAL_STATE_OWNER = Path("v1/DeviceDosingV1StateOwner.kt")

# DeviceChannelSlot is one shared closed catalog algebra. Its Dosing variant is identity/shape only;
# behavior remains forbidden outside application/devices/dosing.
APPLICATION_OUTSIDE_ALLOWLIST = {
    Path("DeviceChannelSlots.kt"): {"DeviceDosingChannelSlot"},
}


def kotlin_files(root: Path) -> list[Path]:
    if not root.is_dir():
        return []
    return sorted(root.rglob("*.kt"))


def relative(path: Path, repository_root: Path) -> str:
    return path.relative_to(repository_root).as_posix()


def expected_package(path: Path, source_root: Path) -> str:
    parent = path.parent.relative_to(source_root).as_posix().replace("/", ".")
    return f"com.aqua.aqualight.{parent}"


def declared_dosing_types(source: str) -> set[str]:
    return {match.group(1) for match in DOSING_DECLARATION.finditer(source)}


def require_tokens(
    path: Path,
    repository_root: Path,
    errors: list[str],
    *tokens: str,
) -> None:
    source = path.read_text(encoding="utf-8", errors="ignore")
    for token in tokens:
        if token not in source:
            errors.append(
                f"{relative(path, repository_root)}: required production Dosing token is missing: "
                f"{token}"
            )


def validate_production_cutover(repository_root: Path, source_root: Path) -> list[str]:
    errors: list[str] = []
    owner_factory = source_root / "composition/OwnerViewModelFactory.kt"
    owner_graph = source_root / "composition/OwnerDependencyGraph.kt"
    app_container = source_root / "composition/AppContainer.kt"
    data_dosing_root = source_root / "data/devices/dosing"
    debug_device_root = repository_root / "app/src/debug/java/com/aqua/aqualight/debug/devices"
    release_smoke_container = (
        repository_root
        / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt"
    )
    pin_path = repository_root / "protocol/fixtures/aql_android_dosing_v1_pin.json"

    if owner_factory.is_file():
        source = owner_factory.read_text(encoding="utf-8", errors="ignore")
        if "UnavailableDeviceDosing" in source:
            errors.append(
                f"{relative(owner_factory, repository_root)}: fail-closed Dosing binding is forbidden "
                "in production composition"
            )
        require_tokens(
            owner_factory,
            repository_root,
            errors,
            "graph.dosingOperations",
            "channelNavigationOperations = dosing.navigationOperations",
            "channelOperations = dosing.channelOperations",
            "graph.dosingOperations.calibrationOperations",
        )

    if owner_graph.is_file():
        require_tokens(
            owner_graph,
            repository_root,
            errors,
            "val dosingOperations: OwnerDosingOperations",
            "DeviceDosingV1ProductionRuntime(",
            "SharedPreferencesDeviceDosingLowLevelAlertLedger.create(",
            "notificationDispatch = notificationDispatchUseCase",
            "dosingOperations = createDosingOperations(dependencies)",
            "registerOwnerScopedResource",
        )

    if app_container.is_file():
        require_tokens(
            app_container,
            repository_root,
            errors,
            "internal fun interface OwnerDependencyGraphAccess",
            "DefaultAppContainer(\n    context: Context\n) : AppContainer, OwnerDependencyGraphAccess",
            "override fun requireActiveOwnerGraph(): OwnerDependencyGraph",
        )

    for path in kotlin_files(data_dosing_root):
        source = path.read_text(encoding="utf-8", errors="ignore")
        if "UnavailableDeviceDosing" in source:
            errors.append(
                f"{relative(path, repository_root)}: obsolete fail-closed Dosing implementation "
                "must be removed after production cutover"
            )

    if pin_path.is_file():
        try:
            pin = json.loads(pin_path.read_text(encoding="utf-8"))
            production_wiring = pin["contract"]["productionWiring"]
        except (json.JSONDecodeError, KeyError, TypeError) as error:
            errors.append(
                f"{relative(pin_path, repository_root)}: invalid Dosing production pin: {error}"
            )
        else:
            if production_wiring is not True:
                errors.append(
                    f"{relative(pin_path, repository_root)}: contract.productionWiring must be true"
                )

    if debug_device_root.is_dir():
        for path in kotlin_files(debug_device_root):
            if path.name.startswith("DebugFixtureDosing"):
                errors.append(
                    f"{relative(path, repository_root)}: Dosing debug fixture implementation is "
                    "forbidden after physical-device cutover"
                )
            source = path.read_text(encoding="utf-8", errors="ignore")
            if "DebugFixtureDosingStateStore" in source:
                errors.append(
                    f"{relative(path, repository_root)}: parallel Dosing fixture state is forbidden"
                )
        fixture_catalog = debug_device_root / "DebugDeviceFixtureCatalog.kt"
        if fixture_catalog.is_file():
            fixture_source = fixture_catalog.read_text(encoding="utf-8", errors="ignore")
            exclusion = ".filterNot { product -> product.family == DeviceFamily.DOSING }"
            if exclusion not in fixture_source:
                errors.append(
                    f"{relative(fixture_catalog, repository_root)}: Dosing products must be excluded "
                    "from installable debug fixtures"
                )
        fixture_container = debug_device_root / "DebugDeviceFixtureAppContainer.kt"
        if fixture_container.is_file():
            fixture_source = fixture_container.read_text(encoding="utf-8", errors="ignore")
            for forbidden in (
                "DebugFixtureDosing",
                "DeviceDosingRootViewModel",
                "ActiveOwnerDependencyGraphResolver",
            ):
                if forbidden in fixture_source:
                    errors.append(
                        f"{relative(fixture_container, repository_root)}: Dosing/debug composition "
                        f"must not contain {forbidden}"
                    )
            if "OwnerDependencyGraphAccess" not in fixture_source:
                errors.append(
                    f"{relative(fixture_container, repository_root)}: debug decorators must share "
                    "the production owner graph"
                )

    if release_smoke_container.is_file():
        smoke_source = release_smoke_container.read_text(encoding="utf-8", errors="ignore")
        for forbidden in ("UnavailableDeviceDosing", "DeviceDosingRootViewModel"):
            if forbidden in smoke_source:
                errors.append(
                    f"{relative(release_smoke_container, repository_root)}: release smoke must not "
                    f"carry an alternate Dosing path: {forbidden}"
                )

    return errors


def validate_repository(repository_root: Path = ROOT) -> list[str]:
    source_root = repository_root / "app/src/main/java/com/aqua/aqualight"
    application_devices_root = source_root / "application/devices"
    application_dosing_root = application_devices_root / "dosing"
    data_devices_root = source_root / "data/devices"
    data_dosing_root = data_devices_root / "dosing"
    ui_dosing_root = source_root / "ui/tabs/devices/detail/dosing"
    shared_dosing_visual_root = source_root / "ui/common/devicevisual/dosing"
    errors: list[str] = []

    for directory, label in (
        (application_dosing_root, "application Dosing root"),
        (data_dosing_root, "data Dosing root"),
        (ui_dosing_root, "UI Dosing root"),
        (shared_dosing_visual_root, "shared Dosing visual root"),
    ):
        if not directory.is_dir():
            errors.append(f"{relative(directory, repository_root)}: missing {label}")

    if errors:
        return errors

    for required in REQUIRED_UI_FILES:
        path = ui_dosing_root / required
        if not path.is_file():
            errors.append(f"{relative(path, repository_root)}: required feature-owned UI file is missing")

    for required in REQUIRED_SHARED_UI_FILES:
        path = shared_dosing_visual_root / required
        if not path.is_file():
            errors.append(f"{relative(path, repository_root)}: required shared Dosing visual file is missing")

    for required in REQUIRED_DATA_FILES:
        path = data_dosing_root / required
        if not path.is_file():
            errors.append(f"{relative(path, repository_root)}: required Dosing data file is missing")

    for path in sorted(ui_dosing_root.glob("*.kt")):
        errors.append(
            f"{relative(path, repository_root)}: Dosing UI files must live in a screen/feature package"
        )

    for package_root in (
        application_dosing_root,
        data_dosing_root,
        ui_dosing_root,
        shared_dosing_visual_root,
    ):
        for path in kotlin_files(package_root):
            source = path.read_text(encoding="utf-8", errors="ignore")
            package = expected_package(path, source_root)
            if not re.search(rf"^package\s+{re.escape(package)}\s*$", source, re.MULTILINE):
                errors.append(
                    f"{relative(path, repository_root)}: package must match its canonical directory: "
                    f"{package}"
                )

    for ui_root in (ui_dosing_root, shared_dosing_visual_root):
        for path in kotlin_files(ui_root):
            source = path.read_text(encoding="utf-8", errors="ignore")
            for pattern, label in UI_FORBIDDEN_PATTERNS:
                if pattern.search(source):
                    errors.append(
                        f"{relative(path, repository_root)}: Dosing UI must not own {label}"
                    )

    for path in kotlin_files(shared_dosing_visual_root):
        source = path.read_text(encoding="utf-8", errors="ignore")
        if SHARED_VISUAL_FORBIDDEN_REFERENCE.search(source):
            errors.append(
                f"{relative(path, repository_root)}: shared Dosing visuals must not depend on "
                "feature-owned Dosing UI"
            )

    for path in kotlin_files(application_dosing_root):
        source = path.read_text(encoding="utf-8", errors="ignore")
        if APPLICATION_FORBIDDEN_REFERENCE.search(source):
            errors.append(
                f"{relative(path, repository_root)}: Dosing application depends on an outer layer"
            )
        if "DeviceDosingV1" in source:
            errors.append(
                f"{relative(path, repository_root)}: Dosing application must not depend on v1 wire types"
            )

    for path in kotlin_files(data_dosing_root):
        source = path.read_text(encoding="utf-8", errors="ignore")
        if DATA_FORBIDDEN_REFERENCE.search(source):
            errors.append(f"{relative(path, repository_root)}: Dosing data must not depend on UI")

    state_owners: list[tuple[Path, str]] = []
    for path in kotlin_files(data_dosing_root):
        source = path.read_text(encoding="utf-8", errors="ignore")
        state_owners.extend(
            (path.relative_to(data_dosing_root), match.group(1))
            for match in DOSING_STATE_OWNER_DECLARATION.finditer(source)
        )
    if state_owners != [(CANONICAL_STATE_OWNER, "DeviceDosingV1StateOwner")]:
        rendered = ", ".join(f"{path}:{name}" for path, name in state_owners) or "none"
        errors.append(
            "data/devices/dosing must contain exactly one canonical Dosing state owner: "
            f"{rendered}"
        )

    for path in kotlin_files(application_devices_root):
        if path.is_relative_to(application_dosing_root):
            continue
        source = path.read_text(encoding="utf-8", errors="ignore")
        declarations = declared_dosing_types(source)
        if not declarations:
            continue
        allowed = APPLICATION_OUTSIDE_ALLOWLIST.get(path.relative_to(application_devices_root), set())
        unexpected = declarations - allowed
        if unexpected:
            errors.append(
                f"{relative(path, repository_root)}: Dosing application declarations belong below "
                f"application/devices/dosing: {', '.join(sorted(unexpected))}"
            )

    for path in kotlin_files(data_devices_root):
        if path.is_relative_to(data_dosing_root):
            continue
        source = path.read_text(encoding="utf-8", errors="ignore")
        declarations = declared_dosing_types(source)
        if declarations:
            errors.append(
                f"{relative(path, repository_root)}: Dosing data declarations belong below "
                f"data/devices/dosing: {', '.join(sorted(declarations))}"
            )

    legacy_runtime = data_devices_root / "runtime/modules/dosing"
    if legacy_runtime.exists():
        errors.append(
            f"{relative(legacy_runtime, repository_root)}: parallel legacy Dosing runtime is forbidden"
        )

    errors.extend(validate_production_cutover(repository_root, source_root))
    return errors


def main() -> int:
    errors = validate_repository()
    if errors:
        print("Dosing architecture guard failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1
    print("Dosing architecture guard passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
