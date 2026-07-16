#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
TESTS = ROOT / "app/src/test/java/com/aqua/aqualight"

contract = APP / "application/aquarium/AquariumTankOperations.kt"
adapter = APP / "data/aquarium/DefaultAquariumTankOperations.kt"
adapter_test = TESTS / "data/aquarium/DefaultAquariumTankOperationsMapperTest.kt"
owner_scope = APP / "data/user/OwnerScopedDataOperation.kt"
owner_scope_test = TESTS / "data/user/UserDataScopeTest.kt"
view_model = APP / "ui/tabs/aquarium/AquariumTankViewModel.kt"
production = APP / "composition/AquaViewModelFactory.kt"
smoke = ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/Stage3SmokeAppContainer.kt"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


errors = []
for path in (
    contract,
    adapter,
    adapter_test,
    owner_scope,
    owner_scope_test,
    view_model,
    production,
    smoke,
):
    if not path.is_file():
        errors.append(f"missing required file: {path.relative_to(ROOT)}")

if contract.is_file():
    text = read(contract)
    if "package com.aqua.aqualight.application.aquarium" not in text:
        errors.append("aquarium contract is outside application layer")
    if "com.aqua.aqualight.data." in text or "android." in text:
        errors.append("aquarium application contract imports data/platform types")

if adapter.is_file():
    text = read(adapter)
    if text.count("withCurrentOwnerScope") < 2:
        errors.append(
            "aquarium deletion and reminder mutation must remain pinned to one owner scope"
        )
    for token in (
        "toApplicationSnapshot",
        "toDataDraft",
        "toApplicationResult",
    ):
        if token not in text:
            errors.append(f"aquarium adapter mapping is missing: {token}")

if adapter_test.is_file():
    text = read(adapter_test)
    for token in (
        "saved tank maps every UI-facing field without owner leakage",
        "application draft maps nested values to persistence draft",
        "delete result keeps public stages and hides throwable details",
    ):
        if token not in text:
            errors.append(f"aquarium adapter mapping test is missing: {token}")

if owner_scope.is_file():
    text = read(owner_scope)
    for token in (
        "UserDataScope.requireCurrentUid()",
        "UserDataScope.withOwnerUid(ownerUid)",
    ):
        if token not in text:
            errors.append(f"owner scope implementation is missing: {token}")

if owner_scope_test.is_file():
    text = read(owner_scope_test)
    if "currentOwnerOperationCapturesAndPropagatesOneOwner" not in text:
        errors.append("immutable owner operation scope lacks regression coverage")

if view_model.is_file():
    text = read(view_model)
    forbidden = (
        "import com.aqua.aqualight.data.",
        "AquariumTankDataStoreManager",
        "CareTaskDataStoreManager",
        "TankDeviceAssignmentRepository",
        "OwnerTankDataCleaner",
        "import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank",
        "import com.aqua.aqualight.data.aquarium.model.TankDraft",
        "import com.aqua.aqualight.data.aquarium.model.TankMaterialSelection",
        "import com.aqua.aqualight.data.aquarium.model.TankPlantTag",
        "import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock",
    )
    for token in forbidden:
        if token in text:
            errors.append(f"AquariumTankViewModel contains forbidden dependency: {token}")
    if "AquariumTankOperations" not in text:
        errors.append("AquariumTankViewModel does not depend on AquariumTankOperations")

for path in (production, smoke):
    if path.is_file():
        text = read(path)
        if "DefaultAquariumTankOperations" not in text:
            errors.append(f"missing aquarium binding in {path.relative_to(ROOT)}")

if errors:
    print("Aquarium application boundary guard failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Aquarium application boundary guard passed.")
