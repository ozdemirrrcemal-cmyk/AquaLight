#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"

contract = APP / "application/aquarium/AquariumTankOperations.kt"
adapter = APP / "data/aquarium/DefaultAquariumTankOperations.kt"
view_model = APP / "ui/tabs/aquarium/AquariumTankViewModel.kt"
production = APP / "composition/AquaViewModelFactory.kt"
smoke = ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/Stage3SmokeAppContainer.kt"

def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")

errors = []
for path in (contract, adapter, view_model, production, smoke):
    if not path.is_file():
        errors.append(f"missing required file: {path.relative_to(ROOT)}")

if contract.is_file():
    text = read(contract)
    if "package com.aqua.aqualight.application.aquarium" not in text:
        errors.append("aquarium contract is outside application layer")
    if "com.aqua.aqualight.data." in text or "android." in text:
        errors.append("aquarium application contract imports data/platform types")

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
