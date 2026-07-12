#!/usr/bin/env python3
"""AquaLight architecture and owner-isolation guard.

Fails CI when lower layers depend on UI packages or when removed legacy device
persistence/session paths are reintroduced. The app is unreleased, therefore
there is intentionally no migration, dual-read, fallback, or version-suffixed
legacy storage path.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight"
GUARDED_DIRS = [
    SOURCE_ROOT / "app",
    SOURCE_ROOT / "base",
    SOURCE_ROOT / "data",
]
FORBIDDEN_IMPORT = re.compile(r"^import\s+com\.aqua\.aqualight\.ui(?:\.|$)", re.MULTILINE)

errors: list[str] = []


def read(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.exists():
        errors.append(f"{relative_path}: required production architecture file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def forbid(relative_path: str, text: str, token: str, reason: str) -> None:
    if token in text:
        errors.append(f"{relative_path}: {reason}: {token}")


def require(relative_path: str, text: str, token: str, reason: str) -> None:
    if token not in text:
        errors.append(f"{relative_path}: {reason}: {token}")


for guarded_dir in GUARDED_DIRS:
    if not guarded_dir.exists():
        continue
    for kotlin_file in guarded_dir.rglob("*.kt"):
        text = kotlin_file.read_text(encoding="utf-8", errors="ignore")
        if FORBIDDEN_IMPORT.search(text):
            rel = kotlin_file.relative_to(ROOT)
            errors.append(f"{rel}: data/app/base layer must not import com.aqua.aqualight.ui.*")

manifest = ROOT / "app/src/main/AndroidManifest.xml"
if manifest.exists():
    manifest_text = manifest.read_text(encoding="utf-8", errors="ignore")
    old_receivers = [
        "com.aqua.aqualight.ui.tabs.maintenance.reminder.CareTaskReminderReceiver",
        "com.aqua.aqualight.ui.tabs.maintenance.reminder.CareTaskBootReceiver",
    ]
    for receiver in old_receivers:
        if receiver in manifest_text:
            errors.append(
                f"{manifest.relative_to(ROOT)}: receiver still points to UI package: {receiver}"
            )

assignment_path = (
    "app/src/main/java/com/aqua/aqualight/data/aquarium/devices/"
    "TankDeviceAssignmentStore.kt"
)
assignment_store = read(assignment_path)
for token in (
    "SharedPreferences",
    "getSharedPreferences",
    "org.json",
    "JSONArray",
    "JSONObject",
    "tank_device_assignments_v2",
    "KEY_ASSIGNMENTS_JSON",
):
    forbid(assignment_path, assignment_store, token, "legacy assignment persistence is forbidden")
require(
    assignment_path,
    assignment_store,
    'fileName = "tank_device_assignments.pb"',
    "assignment Proto DataStore must remain authoritative",
)

known_path = "app/src/main/java/com/aqua/aqualight/data/devices/store/DeviceKnownStore.kt"
known_store = read(known_path)
for token in (
    "androidx.datastore.preferences",
    "preferencesDataStore",
    "org.json",
    "JSONArray",
    "JSONObject",
    "aql_known_devices_v2",
):
    forbid(known_path, known_store, token, "legacy known-device persistence is forbidden")
require(
    known_path,
    known_store,
    'fileName = "known_devices.pb"',
    "known-device Proto DataStore must remain authoritative",
)
require(
    known_path,
    known_store,
    "ownerUid: String",
    "known-device storage must be owner-bound",
)

credential_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/store/DeviceCredentialStore.kt"
)
credential_store = read(credential_path)
for token in (
    "aql_device_credentials_v2",
    "fun clearAll(",
    ".clear()",
):
    forbid(credential_path, credential_store, token, "global credential storage/cleanup is forbidden")
require(
    credential_path,
    credential_store,
    "ownerUid: String",
    "credential storage must be owner-bound",
)
require(
    credential_path,
    credential_store,
    "suspend fun clearOwner()",
    "credential cleanup must target one owner",
)

provider_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/repository/"
    "DevicesRepositoryProvider.kt"
)
provider = read(provider_path)
for token in (
    "Context?",
    "context: Context?",
    "DevicesRepository()",
):
    forbid(provider_path, provider, token, "context-free device repository fallback is forbidden")
require(
    provider_path,
    provider,
    "UserDataScope.requireCurrentUid()",
    "device repository provider must resolve an authenticated owner",
)

app_path = "app/src/main/java/com/aqua/aqualight/app/AquaApp.kt"
app_bootstrap = read(app_path)
for token in (
    "AqlWsTokenProvider.install",
    "DeviceCredentialStore(this)",
):
    forbid(app_path, app_bootstrap, token, "process-global token provider is forbidden")

repository_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/repository/DevicesRepository.kt"
)
devices_repository = read(repository_path)
for token in (
    "clearTokenAsync",
    "runCatching {\n                    repository.forgetDevice",
    "registryStore.upsertAll(filterIgnoredDevices(discoveredDevices))",
):
    forbid(repository_path, devices_repository, token, "fire-and-forget or discovery registration is forbidden")
require(
    repository_path,
    devices_repository,
    "knownStore?.forgetDevice(deviceUid)",
    "forgotten devices must be durably ignored before leaving the registry",
)
require(
    repository_path,
    devices_repository,
    "registryStore.updateExistingAll(",
    "LAN discovery must only update devices already registered for the owner",
)

registry_path = "app/src/main/java/com/aqua/aqualight/data/devices/store/DeviceRegistryStore.kt"
registry_store = read(registry_path)
require(
    registry_path,
    registry_store,
    "fun updateExistingAll(",
    "registry must expose an atomic registered-only discovery update",
)
require(
    registry_path,
    registry_store,
    "val previous = acc[incoming.deviceUid] ?: return@fold acc",
    "late discovery must not create or resurrect a device",
)

session_path = "app/src/main/java/com/aqua/aqualight/data/auth/OwnerSessionCoordinator.kt"
session_coordinator = read(session_path)
require(
    session_path,
    session_coordinator,
    "OwnerSessionStateMachine",
    "owner transitions must remain generation-controlled",
)
require(
    session_path,
    session_coordinator,
    "repairOwnerAssignments()",
    "stale assignment repair must run during owner startup",
)

proto_dir = ROOT / "app/src/main/proto"
for required_proto in ("tank_device_assignments.proto", "known_devices.proto"):
    if not (proto_dir / required_proto).exists():
        errors.append(f"app/src/main/proto/{required_proto}: required Proto source is missing")

legacy_tokens = (
    "tank_device_assignments_v2",
    "aql_known_devices_v2",
    "aql_device_credentials_v2",
)
for source_file in (ROOT / "app/src/main").rglob("*"):
    if not source_file.is_file() or source_file.suffix not in {".kt", ".java", ".xml", ".proto"}:
        continue
    text = source_file.read_text(encoding="utf-8", errors="ignore")
    for token in legacy_tokens:
        if token in text:
            errors.append(
                f"{source_file.relative_to(ROOT)}: legacy storage identifier is forbidden: {token}"
            )

if errors:
    print("Architecture guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print(
    "Architecture guard passed: layer boundaries, Proto authority, owner isolation, "
    "registered-only discovery, and non-global credential/session rules are intact."
)
