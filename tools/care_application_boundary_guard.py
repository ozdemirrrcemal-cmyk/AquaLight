#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
TESTS = ROOT / "app/src/test/java/com/aqua/aqualight"

contract = APP / "application/care/MaintenanceOperations.kt"
adapter = APP / "data/care/DefaultMaintenanceOperations.kt"
owner_scope = APP / "data/user/OwnerScopedDataOperation.kt"
owner_scope_test = TESTS / "data/user/UserDataScopeTest.kt"
view_model = APP / "ui/tabs/maintenance/MaintenanceViewModel.kt"
ui_model = APP / "ui/tabs/maintenance/model/CareTaskUi.kt"
text_contract = APP / "ui/tabs/maintenance/text/MaintenanceTextResolver.kt"
presentation_catalog = APP / "ui/tabs/maintenance/text/CareTaskTypeCatalog.kt"
data_model = APP / "data/care/model/CareTask.kt"
production = APP / "composition/AquaViewModelFactory.kt"
smoke = ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/Stage3SmokeAppContainer.kt"
test_file = TESTS / "ui/tabs/maintenance/MaintenanceViewModelBoundaryTest.kt"
obsolete_repository = APP / "data/care/MaintenanceRepository.kt"
obsolete_text = APP / "data/care/MaintenanceTextResolver.kt"

required = (
    contract,
    adapter,
    owner_scope,
    owner_scope_test,
    view_model,
    ui_model,
    text_contract,
    presentation_catalog,
    data_model,
    production,
    smoke,
    test_file,
)

errors: list[str] = []

for path in required:
    if not path.is_file():
        errors.append(f"missing required file: {path.relative_to(ROOT)}")

for path in (obsolete_repository, obsolete_text):
    if path.exists():
        errors.append(f"obsolete care path remains: {path.relative_to(ROOT)}")

if contract.is_file():
    text = contract.read_text(encoding="utf-8")
    if "package com.aqua.aqualight.application.care" not in text:
        errors.append("maintenance contract is outside application layer")
    if "com.aqua.aqualight.data." in text or "import android." in text:
        errors.append("maintenance application contract imports data/platform types")

if adapter.is_file():
    text = adapter.read_text(encoding="utf-8")
    if text.count("withCurrentOwnerScope") < 9:
        errors.append("every maintenance command must remain pinned to one owner scope")
    if "tank.toDataTank(ownerUid)" not in text:
        errors.append("Smart Care generation must carry the captured owner UID")
    if "ownerUid = \"\"" in text:
        errors.append("Smart Care adapter must not create ownerless tank models")

if owner_scope.is_file():
    text = owner_scope.read_text(encoding="utf-8")
    for token in (
        "UserDataScope.requireCurrentUid()",
        "UserDataScope.withOwnerUid(ownerUid)",
    ):
        if token not in text:
            errors.append(f"owner scope implementation is missing: {token}")

if owner_scope_test.is_file():
    text = owner_scope_test.read_text(encoding="utf-8")
    if "currentOwnerOperationCapturesAndPropagatesOneOwner" not in text:
        errors.append("immutable owner operation scope lacks regression coverage")

for path in (view_model, ui_model):
    if not path.is_file():
        continue
    text = path.read_text(encoding="utf-8")
    forbidden = (
        "import com.aqua.aqualight.data.care.",
        "MaintenanceRepository",
        "CareTaskSnapshotSource",
        "CareTaskSnapshotStatus",
        "CareTaskSnapshotType",
    )
    for token in forbidden:
        if token in text:
            errors.append(f"{path.relative_to(ROOT)} contains forbidden token: {token}")

if view_model.is_file():
    text = view_model.read_text(encoding="utf-8")
    if "MaintenanceOperations" not in text:
        errors.append("MaintenanceViewModel does not depend on MaintenanceOperations")
    if "CompletedCareActivityInput" not in text or "ManualCareTaskInput" not in text:
        errors.append("MaintenanceViewModel does not use typed care command inputs")

ui_root = APP / "ui"
if ui_root.is_dir():
    for path in ui_root.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        if "import com.aqua.aqualight.data.care.model." in text:
            errors.append(f"care data model import leaked into UI: {path.relative_to(ROOT)}")
        if "import com.aqua.aqualight.data.care.catalog." in text:
            errors.append(f"care data catalog import leaked into UI: {path.relative_to(ROOT)}")
        for malformed in (
            "CareTaskSnapshotSource",
            "CareTaskSnapshotStatus",
            "CareTaskSnapshotType",
        ):
            if malformed in text:
                errors.append(f"malformed care type remains in {path.relative_to(ROOT)}: {malformed}")

for path in (production, smoke):
    if path.is_file():
        text = path.read_text(encoding="utf-8")
        if "DefaultMaintenanceOperations" not in text:
            errors.append(f"missing maintenance operations binding in {path.relative_to(ROOT)}")
        if "AndroidMaintenanceTextResolver" not in text:
            errors.append(f"missing maintenance text binding in {path.relative_to(ROOT)}")
        if "DefaultMaintenanceRepository" in text:
            errors.append(f"obsolete maintenance repository binding in {path.relative_to(ROOT)}")

if test_file.is_file():
    text = test_file.read_text(encoding="utf-8")
    for token in (
        "FakeMaintenanceOperations",
        "CompletedCareActivityInput",
        "ManualCareTaskInput",
        "syncSmartCareTasks",
    ):
        if token not in text:
            errors.append(f"maintenance fake test is missing coverage token: {token}")


def enum_values(path: Path, enum_name: str) -> set[str]:
    if not path.is_file():
        return set()
    text = path.read_text(encoding="utf-8")
    match = re.search(rf"enum class {enum_name}\s*\{{(.*?)\}}", text, re.S)
    if not match:
        return set()
    return {
        token
        for token in re.findall(r"\b[A-Z][A-Z0-9_]*\b", match.group(1))
    }


application_types = enum_values(contract, "CareTaskType")
data_types = enum_values(data_model, "CareTaskType")
if application_types and data_types and application_types != data_types:
    errors.append(
        "application/data CareTaskType enums differ: "
        f"application-only={sorted(application_types - data_types)}, "
        f"data-only={sorted(data_types - application_types)}"
    )

if presentation_catalog.is_file() and application_types:
    catalog_text = presentation_catalog.read_text(encoding="utf-8")
    catalog_types = set(re.findall(r"CareTaskType\.([A-Z][A-Z0-9_]*)", catalog_text))
    if catalog_types != application_types:
        errors.append(
            "presentation CareTaskType catalog coverage differs: "
            f"missing={sorted(application_types - catalog_types)}, "
            f"extra={sorted(catalog_types - application_types)}"
        )

for temporary_path in (
    ROOT / "tools/care_ui_model_migration.py",
    ROOT / ".github/workflows/care_ui_model_migration.yml",
    ROOT / "tools/care_ui_reference_cleanup.py",
    ROOT / ".github/workflows/care_ui_reference_cleanup.yml",
):
    if temporary_path.exists():
        errors.append(f"one-shot migration artifact remains: {temporary_path.relative_to(ROOT)}")

if errors:
    print("Care application boundary guard failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Care application boundary guard passed.")
