#!/usr/bin/env python3
from pathlib import Path
import re

ROOTS = (
    Path("app/src/main/java/com/aqua/aqualight/ui"),
    Path("app/src/test/java/com/aqua/aqualight/ui"),
)

IMPORT_MAPPING = {
    "import com.aqua.aqualight.data.care.model.CareTask":
        "import com.aqua.aqualight.application.care.CareTaskSnapshot",
    "import com.aqua.aqualight.data.care.model.CareTaskSource":
        "import com.aqua.aqualight.application.care.CareTaskSource",
    "import com.aqua.aqualight.data.care.model.CareTaskStatus":
        "import com.aqua.aqualight.application.care.CareTaskStatus",
    "import com.aqua.aqualight.data.care.model.CareTaskType":
        "import com.aqua.aqualight.application.care.CareTaskType",
    "import com.aqua.aqualight.data.care.MaintenanceRepository":
        "import com.aqua.aqualight.application.care.MaintenanceOperations",
    "import com.aqua.aqualight.data.care.MaintenanceTextResolver":
        "import com.aqua.aqualight.ui.tabs.maintenance.text.MaintenanceTextResolver",
}

TYPE_MAPPING = {
    "CareTask": "CareTaskSnapshot",
    "MaintenanceRepository": "MaintenanceOperations",
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
            updated = re.sub(rf"\b{old_type}\b", new_type, updated)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed.append(str(path))

Path("tools/care_ui_model_migration.py").unlink(missing_ok=True)
Path(".github/workflows/care_ui_model_migration.yml").unlink(missing_ok=True)

print("Migrated care UI model references:")
for item in changed:
    print(f"- {item}")
