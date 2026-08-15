#!/usr/bin/env python3
"""Enforce Dosing package ownership and inward dependency direction."""

from __future__ import annotations

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

REQUIRED_UI_FILES = (
    "root/DeviceDosingRootFragment.kt",
    "root/DeviceDosingRootViewModel.kt",
    "root/DosingCatalogScreen.kt",
    "presentation/model/DosingWeekday.kt",
    "presentation/pump/DosingPumpDeviceCompose.kt",
    "presentation/pump/DosingPumpIndicatorDrawing.kt",
    "presentation/pump/DosingPumpPalette.kt",
    "presentation/pump/DosingPumpSection.kt",
)
REQUIRED_DATA_FILES = (
    "DosingChannelSlotFactory.kt",
)

UI_FORBIDDEN_PATTERNS = (
    (re.compile(r"^import\s+com\.aqua\.aqualight\.data\.", re.MULTILINE), "data import"),
    (re.compile(r"^import\s+com\.aqua\.aqualight\.platform\.", re.MULTILINE), "platform import"),
    (re.compile(r"^import\s+org\.json\.", re.MULTILINE), "JSON import"),
    (re.compile(r"\bruntime\.modules\b"), "runtime-module dependency"),
    (re.compile(r"\bDeviceDosingV1[A-Za-z0-9_]*\b"), "Dosing v1 wire type"),
    (re.compile(r"\bexpectedRevision\b"), "firmware revision"),
    (re.compile(r"\bchannelKey\b"), "firmware channel key"),
)

APPLICATION_FORBIDDEN_IMPORT = re.compile(
    r"^import\s+(?:android(?:x)?\.|org\.json\.|"
    r"com\.aqua\.aqualight\.(?:composition|data|platform|ui)\.)",
    re.MULTILINE,
)
DATA_FORBIDDEN_IMPORT = re.compile(
    r"^import\s+com\.aqua\.aqualight\.ui\.",
    re.MULTILINE,
)
DOSING_DECLARATION = re.compile(
    r"^[ \t]*(?:(?:public|internal|private|protected|data|sealed|enum|value|fun|abstract|open|final)\s+)*"
    r"(?:class|interface|object)\s+([A-Za-z0-9_]*Dosing[A-Za-z0-9_]*)\b",
    re.MULTILINE,
)

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


def validate_repository(repository_root: Path = ROOT) -> list[str]:
    source_root = repository_root / "app/src/main/java/com/aqua/aqualight"
    application_devices_root = source_root / "application/devices"
    application_dosing_root = application_devices_root / "dosing"
    data_devices_root = source_root / "data/devices"
    data_dosing_root = data_devices_root / "dosing"
    ui_dosing_root = source_root / "ui/tabs/devices/detail/dosing"
    errors: list[str] = []

    for directory, label in (
        (application_dosing_root, "application Dosing root"),
        (data_dosing_root, "data Dosing root"),
        (ui_dosing_root, "UI Dosing root"),
    ):
        if not directory.is_dir():
            errors.append(f"{relative(directory, repository_root)}: missing {label}")

    if errors:
        return errors

    for required in REQUIRED_UI_FILES:
        path = ui_dosing_root / required
        if not path.is_file():
            errors.append(f"{relative(path, repository_root)}: required feature-owned UI file is missing")

    for required in REQUIRED_DATA_FILES:
        path = data_dosing_root / required
        if not path.is_file():
            errors.append(f"{relative(path, repository_root)}: required Dosing data file is missing")

    for path in sorted(ui_dosing_root.glob("*.kt")):
        errors.append(
            f"{relative(path, repository_root)}: Dosing UI files must live in a screen/feature package"
        )

    for package_root in (application_dosing_root, data_dosing_root, ui_dosing_root):
        for path in kotlin_files(package_root):
            source = path.read_text(encoding="utf-8", errors="ignore")
            package = expected_package(path, source_root)
            if not re.search(rf"^package\s+{re.escape(package)}\s*$", source, re.MULTILINE):
                errors.append(
                    f"{relative(path, repository_root)}: package must match its canonical directory: "
                    f"{package}"
                )

    for path in kotlin_files(ui_dosing_root):
        source = path.read_text(encoding="utf-8", errors="ignore")
        for pattern, label in UI_FORBIDDEN_PATTERNS:
            if pattern.search(source):
                errors.append(
                    f"{relative(path, repository_root)}: Dosing UI must not own {label}"
                )

    for path in kotlin_files(application_dosing_root):
        source = path.read_text(encoding="utf-8", errors="ignore")
        if APPLICATION_FORBIDDEN_IMPORT.search(source):
            errors.append(
                f"{relative(path, repository_root)}: Dosing application depends on an outer layer"
            )
        if "DeviceDosingV1" in source:
            errors.append(
                f"{relative(path, repository_root)}: Dosing application must not depend on v1 wire types"
            )

    for path in kotlin_files(data_dosing_root):
        source = path.read_text(encoding="utf-8", errors="ignore")
        if DATA_FORBIDDEN_IMPORT.search(source):
            errors.append(f"{relative(path, repository_root)}: Dosing data must not import UI")

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
