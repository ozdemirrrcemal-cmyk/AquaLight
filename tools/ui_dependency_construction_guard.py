#!/usr/bin/env python3
"""Fail CI when UI/ViewModel code owns concrete infrastructure construction.

Stage 3 requires every Fragment and ViewModel to receive already-wired
collaborators. The allow-list is deliberately empty: concrete repositories,
stores, managers, Firebase clients and provisioning platform objects belong in
composition/data/platform code, never under the UI source tree.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
UI_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight/ui"

if not UI_ROOT.exists():
    raise SystemExit(f"UI source root is missing: {UI_ROOT}")

# These are construction/access signatures, not broad type-name bans. UI may
# consume stable model types while later roadmap stages progressively refine
# package boundaries, but it may not open infrastructure or vendor SDKs.
FORBIDDEN = {
    "UserPreferencesManager.create(": "user preferences must resolve through AppContainer",
    "CareTaskDataStoreManager.create(": "care storage must resolve through composition",
    "AquariumTankDataStoreManager(": "tank storage must resolve through composition",
    "DevicesRepositoryProvider.get(": "device repository provider must resolve through composition",
    "TankDeviceAssignmentRepositoryProvider.get(": "assignment provider must resolve through composition",
    "AuthRepository.create(": "auth repository must resolve through AppContainer",
    "LogoutManager.create(": "logout manager must resolve through AppContainer",
    "AccountDeletionManager.create(": "account deletion must use an application boundary",
    "GoogleSignInClientFactory.create(": "Google identity client must resolve through AppContainer",
    "AqlBleProvisioningScanner(": "BLE scanner must resolve through the feature factory",
    "AqlBleProvisioningAddressResolver(": "BLE address resolver must resolve through composition",
    "AqlBleProvisioningGattClient(": "GATT client must resolve through composition",
    "AqlProvisioningHandoffSaver(": "provisioning transaction saver must resolve through composition",
    "AqlProvisioningDraftStore.": "provisioning draft storage must use an injected boundary",
    "UserDataScope.requireCurrentUid(": "owner identity must be injected",
    "UserDataScope.currentUid(": "owner identity must be injected",
    "FirebaseAuth.getInstance()": "Firebase Auth must stay behind an adapter",
    "FirebaseFirestore.getInstance()": "Firestore must stay behind an adapter",
    "Firebase.firestore": "Firestore must stay behind an adapter",
    "Firebase.storage": "Firebase Storage must stay behind an adapter",
    "FieldValue.serverTimestamp()": "Firestore values must stay behind an adapter",
}

FIREBASE_IMPORT = re.compile(r"^import\s+com\.google\.firebase\.", re.MULTILINE)
ANDROID_VIEWMODEL = re.compile(r"\bclass\s+\w+ViewModel\b[\s\S]{0,250}:\s*AndroidViewModel\b")
APPLICATION_CTOR = re.compile(r"\bclass\s+\w+ViewModel\s*\([^)]*\bApplication\b")

errors: list[str] = []
scanned = 0

for path in sorted(UI_ROOT.rglob("*.kt")):
    scanned += 1
    text = path.read_text(encoding="utf-8", errors="ignore")
    relative = path.relative_to(ROOT)

    for token, reason in FORBIDDEN.items():
        if token in text:
            errors.append(f"{relative}: {reason}: {token}")

    if FIREBASE_IMPORT.search(text):
        errors.append(f"{relative}: Firebase SDK imports are forbidden in UI")

    if path.name.endswith("ViewModel.kt"):
        if "import android.app.Application" in text:
            errors.append(f"{relative}: ViewModel must not import android.app.Application")
        if "import androidx.lifecycle.AndroidViewModel" in text:
            errors.append(f"{relative}: ViewModel must not import AndroidViewModel")
        if ANDROID_VIEWMODEL.search(text) or APPLICATION_CTOR.search(text):
            errors.append(f"{relative}: ViewModel must receive constructor dependencies from a factory")
        if "getApplication<" in text:
            errors.append(f"{relative}: ViewModel must not resolve Android Application")

if errors:
    print("UI dependency construction guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print(f"UI dependency construction guard passed ({scanned} Kotlin files scanned).")
