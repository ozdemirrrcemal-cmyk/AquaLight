#!/usr/bin/env python3
from pathlib import Path

ROOTS = (
    Path("app/src/main/java/com/aqua/aqualight/ui"),
    Path("app/src/test/java/com/aqua/aqualight/ui"),
)

REPLACEMENTS = {
    "CareTaskSnapshotSource": "CareTaskSource",
    "CareTaskSnapshotStatus": "CareTaskStatus",
    "CareTaskSnapshotType": "CareTaskType",
    "com.aqua.aqualight.data.care.catalog.CareTaskTypeCatalog":
        "com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTypeCatalog",
    "com.aqua.aqualight.data.care.catalog.CareTaskTypeDefinition":
        "com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTypeDefinition",
}

changed = []
for root in ROOTS:
    if not root.is_dir():
        continue
    for path in root.rglob("*.kt"):
        original = path.read_text(encoding="utf-8")
        updated = original
        for old, new in REPLACEMENTS.items():
            updated = updated.replace(old, new)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed.append(str(path))

Path("tools/care_ui_reference_cleanup.py").unlink(missing_ok=True)
Path(".github/workflows/care_ui_reference_cleanup.yml").unlink(missing_ok=True)

print("Cleaned care UI references:")
for item in changed:
    print(f"- {item}")
