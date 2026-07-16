#!/usr/bin/env python3
from pathlib import Path
import re

ROOTS = (
    Path("app/src/main/java/com/aqua/aqualight/ui"),
    Path("app/src/test/java/com/aqua/aqualight/ui"),
)

TYPE_MAPPING = {
    "SavedAquariumTank": "AquariumTankSnapshot",
    "SavedAquariumMaterial": "AquariumMaterialSelection",
    "SavedAquariumPlant": "AquariumPlantTag",
    "SavedAquariumLivestock": "AquariumLivestock",
    "TankDraft": "AquariumTankDraft",
    "TankMaterialSelection": "AquariumMaterialSelection",
    "TankPlantTag": "AquariumPlantTag",
}

IMPORT_MAPPING = {
    f"import com.aqua.aqualight.data.aquarium.model.{old}":
        f"import com.aqua.aqualight.application.aquarium.{new}"
    for old, new in TYPE_MAPPING.items()
}

changed = []
for root in ROOTS:
    if not root.is_dir():
        continue
    for path in root.rglob("*.kt"):
        original = path.read_text(encoding="utf-8")
        updated = original
        for old_import, new_import in IMPORT_MAPPING.items():
            updated = updated.replace(old_import, new_import)
        for old_type, new_type in TYPE_MAPPING.items():
            updated = re.sub(rf"\b{re.escape(old_type)}\b", new_type, updated)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed.append(str(path))

# One-shot migration tooling must not remain in the branch tree.
Path("tools/aquarium_ui_model_migration.py").unlink(missing_ok=True)
Path(".github/workflows/aquarium_ui_model_migration.yml").unlink(missing_ok=True)

print("Migrated aquarium UI model references:")
for item in changed:
    print(f"- {item}")
