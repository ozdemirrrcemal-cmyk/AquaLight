#!/usr/bin/env python3
"""AquaLight architecture, owner-isolation and CI-variant guard.

The application is unreleased. Legacy persistence, compatibility fallbacks,
production configuration in public CI, dual-read paths and version-suffixed
storage are therefore forbidden.
"""

from pathlib import Path
import re
import sys
from collections.abc import Iterable

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight"
GUARDED_DIRS = [
    SOURCE_ROOT / "app",
    SOURCE_ROOT / "base",
    SOURCE_ROOT / "data",
]
FORBIDDEN_IMPORT = re.compile(
    r"^import\s+com\.aqua\.aqualight\.ui(?:\.|$)",
    re.MULTILINE,
)

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


def require_tokens(
    relative_path: str,
    text: str,
    requirements: Iterable[tuple[str, str]],
) -> None:
    for token, reason in requirements:
        require(relative_path, text, token, reason)


def forbid_tokens(
    relative_path: str,
    text: str,
    prohibitions: Iterable[tuple[str, str]],
) -> None:
    for token, reason in prohibitions:
        forbid(relative_path, text, token, reason)


for guarded_dir in GUARDED_DIRS:
    if not guarded_dir.exists():
        continue
    for kotlin_file in guarded_dir.rglob("*.kt"):
        text = kotlin_file.read_text(encoding="utf-8", errors="ignore")
        if FORBIDDEN_IMPORT.search(text):
            errors.append(
                f"{kotlin_file.relative_to(ROOT)}: "
                "data/app/base layer must not import com.aqua.aqualight.ui.*"
            )

manifest_path = "app/src/main/AndroidManifest.xml"
manifest = ROOT / manifest_path
if manifest.exists():
    manifest_text = manifest.read_text(encoding="utf-8", errors="ignore")
    require(
        manifest_path,
        manifest_text,
        'android:allowBackup="false"',
        "Android cloud backup must remain disabled",
    )
    for receiver in (
        "com.aqua.aqualight.ui.tabs.maintenance.reminder.CareTaskReminderReceiver",
        "com.aqua.aqualight.ui.tabs.maintenance.reminder.CareTaskBootReceiver",
    ):
        forbid(
            manifest_path,
            manifest_text,
            receiver,
            "receiver must not point to a UI package",
        )

assignment_path = (
    "app/src/main/java/com/aqua/aqualight/data/aquarium/devices/"
    "TankDeviceAssignmentStore.kt"
)
assignment_store = read(assignment_path)
forbid_tokens(
    assignment_path,
    assignment_store,
    (
        (token, "legacy assignment persistence is forbidden")
        for token in (
            "SharedPreferences",
            "getSharedPreferences",
            "org.json",
            "JSONArray",
            "JSONObject",
            "tank_device_assignments_v2",
            "KEY_ASSIGNMENTS_JSON",
        )
    ),
)
require_tokens(
    assignment_path,
    assignment_store,
    (
        (
            'fileName = "tank_device_assignments.pb"',
            "assignment Proto DataStore must remain authoritative",
        ),
        (
            "ReplaceFileCorruptionHandler",
            "corrupt assignment Proto must recover to an empty fail-closed authority",
        ),
    ),
)

known_path = "app/src/main/java/com/aqua/aqualight/data/devices/store/DeviceKnownStore.kt"
known_store = read(known_path)
forbid_tokens(
    known_path,
    known_store,
    (
        (token, "legacy known-device persistence is forbidden")
        for token in (
            "androidx.datastore.preferences",
            "preferencesDataStore",
            "org.json",
            "JSONArray",
            "JSONObject",
            "aql_known_devices_v2",
        )
    ),
)
require_tokens(
    known_path,
    known_store,
    (
        (
            'fileName = "known_devices.pb"',
            "known-device Proto DataStore must remain authoritative",
        ),
        ("ownerUid: String", "known-device storage must be owner-bound"),
        (
            "ReplaceFileCorruptionHandler",
            "corrupt known-device Proto must recover to an empty fail-closed authority",
        ),
    ),
)

credential_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/store/"
    "DeviceCredentialStore.kt"
)
credential_store = read(credential_path)
forbid_tokens(
    credential_path,
    credential_store,
    (
        (token, "global credential storage/cleanup is forbidden")
        for token in (
            "aql_device_credentials_v2",
            "fun clearAll(",
            ".clear()",
        )
    ),
)
require_tokens(
    credential_path,
    credential_store,
    (
        ("ownerUid: String", "credential storage must be owner-bound"),
        ("suspend fun clearOwner()", "credential cleanup must target one owner"),
        (
            "suspend fun stageToken(",
            "provisioning credentials must use two-phase persistence",
        ),
        (
            "suspend fun commitStagedToken(",
            "staged credentials need an explicit commit boundary",
        ),
        (
            "suspend fun discardStagedTokens()",
            "process death must discard uncommitted credentials",
        ),
        (
            "suspend fun retainTokensFor(",
            "orphan credentials must be reconciled against durable devices",
        ),
    ),
)

provider_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/repository/"
    "DevicesRepositoryProvider.kt"
)
provider = read(provider_path)
forbid_tokens(
    provider_path,
    provider,
    (
        (token, "context-free device repository fallback is forbidden")
        for token in ("Context?", "context: Context?", "DevicesRepository()")
    ),
)
require(
    provider_path,
    provider,
    "UserDataScope.requireCurrentUid()",
    "device repository provider must resolve an authenticated owner",
)

app_path = "app/src/main/java/com/aqua/aqualight/app/AquaApp.kt"
app_bootstrap = read(app_path)
forbid_tokens(
    app_path,
    app_bootstrap,
    (
        (token, "process-global token provider is forbidden")
        for token in ("AqlWsTokenProvider.install", "DeviceCredentialStore(this)")
    ),
)

repository_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/repository/"
    "DevicesRepository.kt"
)
devices_repository = read(repository_path)
forbid_tokens(
    repository_path,
    devices_repository,
    (
        (token, "fire-and-forget or discovery registration is forbidden")
        for token in (
            "clearTokenAsync",
            "runCatching {\n                    repository.forgetDevice",
            "registryStore.upsertAll(filterIgnoredDevices(discoveredDevices))",
        )
    ),
)
require_tokens(
    repository_path,
    devices_repository,
    (
        (
            "knownStore?.forgetDevice(deviceUid)",
            "forgotten devices must be durably ignored before leaving the registry",
        ),
        (
            "registryStore.updateExistingAll(",
            "LAN discovery must only update devices already registered for the owner",
        ),
    ),
)

registry_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/store/"
    "DeviceRegistryStore.kt"
)
registry_store = read(registry_path)
require_tokens(
    registry_path,
    registry_store,
    (
        (
            "fun updateExistingAll(",
            "registry must expose an atomic registered-only discovery update",
        ),
        (
            "val previous = acc[incoming.deviceUid] ?: return@fold acc",
            "late discovery must not create or resurrect a device",
        ),
    ),
)

session_path = (
    "app/src/main/java/com/aqua/aqualight/data/auth/"
    "OwnerSessionCoordinator.kt"
)
session_coordinator = read(session_path)
require_tokens(
    session_path,
    session_coordinator,
    (
        ("OwnerSessionStateMachine", "owner transitions must remain generation-controlled"),
        (
            "repairOwnerAssignments()",
            "stale assignment repair must run during owner startup",
        ),
        (
            "repairOrphanedTankTasks(normalizedOwnerUid)",
            "stale tank care data must be reconciled during owner startup",
        ),
        (
            "rollbackPendingRegistrationsForOwner(normalizedOwnerUid)",
            "owner startup must finish residual in-process provisioning rollback",
        ),
        (
            "credentialStore.discardStagedTokens()",
            "owner startup must roll back process-killed credential staging",
        ),
        (
            "credentialStore.retainTokensFor(",
            "owner startup must remove credentials without a durable device",
        ),
    ),
)

provisioning_saver_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/provisioning/repository/"
    "AqlProvisioningHandoffSaver.kt"
)
provisioning_saver = read(provisioning_saver_path)
require_tokens(
    provisioning_saver_path,
    provisioning_saver,
    (
        (
            "rollbackPendingRegistrationsForOwner(",
            "session teardown must be able to roll back all provisioning transactions",
        ),
        (
            "credentialStore.stageToken(",
            "provisioning must not overwrite committed credentials before completion",
        ),
        (
            "credentialStore.commitStagedToken(",
            "provisioning must explicitly commit its verified credential",
        ),
        (
            "pendingRegistry.registerIfAbsent(",
            "duplicate provisioning transactions must be rejected atomically",
        ),
    ),
)

transaction_registry_test_path = (
    "app/src/test/java/com/aqua/aqualight/data/devices/provisioning/repository/"
    "AqlProvisioningTransactionRegistryTest.kt"
)
transaction_registry_test = read(transaction_registry_test_path)
require_tokens(
    transaction_registry_test_path,
    transaction_registry_test,
    (
        (
            "concurrent duplicate registration has exactly one winner",
            "provisioning transaction registration needs a concurrency regression test",
        ),
        (
            "stale transaction cannot remove current transaction",
            "provisioning transaction removal must remain identity-safe",
        ),
        (
            "same device uid is independent between owners",
            "provisioning transactions must remain owner-isolated",
        ),
    ),
)

provisioning_view_model_path = (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/add/"
    "DeviceProvisioningProgressViewModel.kt"
)
provisioning_view_model = read(provisioning_view_model_path)
require(
    provisioning_view_model_path,
    provisioning_view_model,
    "fun requestExit()",
    "provisioning exit must be a rollback-aware operation",
)

provisioning_fragment_path = (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/add/"
    "DeviceProvisioningProgressFragment.kt"
)
provisioning_fragment = read(provisioning_fragment_path)
require(
    provisioning_fragment_path,
    provisioning_fragment,
    "viewModel.requestExit()",
    "provisioning back navigation must pass through rollback",
)

for proto_store_path, reason in (
    (
        "app/src/main/java/com/aqua/aqualight/data/aquarium/store/"
        "AquariumTankDataStoreManager.kt",
        "corrupt aquarium Proto must recover to an empty fail-closed authority",
    ),
    (
        "app/src/main/java/com/aqua/aqualight/data/care/"
        "CareTaskDataStoreManager.kt",
        "corrupt care-task Proto must recover to an empty fail-closed authority",
    ),
):
    require(
        proto_store_path,
        read(proto_store_path),
        "ReplaceFileCorruptionHandler",
        reason,
    )

user_cleaner_path = (
    "app/src/main/java/com/aqua/aqualight/data/user/"
    "UserDataCleaner.kt"
)
user_cleaner = read(user_cleaner_path)
require_tokens(
    user_cleaner_path,
    user_cleaner,
    (
        (
            "Step.DEVICE_ASSIGNMENTS",
            "account cleanup must attempt assignment removal independently",
        ),
        (
            "Step.KNOWN_DEVICES",
            "account cleanup must attempt known-device removal independently",
        ),
        (
            "Step.DEVICE_CREDENTIALS",
            "account cleanup must attempt credential removal independently",
        ),
    ),
)

credential_instrumentation_path = (
    "app/src/androidTest/java/com/aqua/aqualight/data/devices/store/"
    "DeviceCredentialStoreInstrumentedTest.kt"
)
credential_instrumentation = read(credential_instrumentation_path)
require_tokens(
    credential_instrumentation_path,
    credential_instrumentation,
    (
        (
            "stagedTokenDoesNotOverwriteCommittedTokenUntilCommit",
            "two-phase credentials require an Android storage test",
        ),
        (
            "processRestartCleanupDiscardsOnlyStagedTokens",
            "process-restart credential recovery requires an Android storage test",
        ),
        (
            "orphanReconciliationKeepsOnlyDurableDeviceCredentials",
            "orphan credential reconciliation requires an Android storage test",
        ),
        (
            "sameDeviceCredentialIsIsolatedBetweenOwners",
            "credential owner isolation requires an Android storage test",
        ),
    ),
)

corruption_instrumentation_path = (
    "app/src/androidTest/java/com/aqua/aqualight/data/recovery/"
    "ProtoCorruptionRecoveryInstrumentedTest.kt"
)
corruption_instrumentation = read(corruption_instrumentation_path)
require(
    corruption_instrumentation_path,
    corruption_instrumentation,
    "allAuthoritativeProtoStoresRecoverFailClosedAndReportRecovery",
    "authoritative Proto corruption recovery requires an Android DataStore test",
)

emulator_workflow_path = ".github/workflows/android_emulator_tests.yml"
emulator_workflow = read(emulator_workflow_path)
require_tokens(
    emulator_workflow_path,
    emulator_workflow,
    (
        ("connectedDebugAndroidTest", "instrumentation tests must run in CI"),
        (
            "bash tools/verify_uninstall_clears_data.sh",
            "emulator CI must verify uninstall/reinstall data removal",
        ),
        (
            "api-level: [27, 35]",
            "emulator CI must cover min and modern Android APIs",
        ),
        (
            "android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d",
            "emulator CI action must remain pinned to its reviewed v2.38.0 commit",
        ),
    ),
)

uninstall_test_path = "tools/verify_uninstall_clears_data.sh"
uninstall_test = read(uninstall_test_path)
require_tokens(
    uninstall_test_path,
    uninstall_test,
    (
        ("adb uninstall", "the uninstall smoke test must remove the application package"),
        (
            "known_devices.pb",
            "the uninstall smoke test must cover durable known devices",
        ),
        (
            "device_credentials.xml",
            "the uninstall smoke test must cover encrypted credentials",
        ),
    ),
)

for backup_rules_path in (
    "app/src/main/res/xml/backup_rules.xml",
    "app/src/main/res/xml/data_extraction_rules.xml",
):
    backup_rules = read(backup_rules_path)
    for excluded_path in (
        'domain="file" path="datastore/"',
        'domain="sharedpref" path="device_credentials.xml"',
    ):
        require(
            backup_rules_path,
            backup_rules,
            excluded_path,
            "device registry and credential data must remain excluded from backup/transfer",
        )

public_ci_path = ".github/workflows/codeql.yml"
public_ci = read(public_ci_path)
require_tokens(
    public_ci_path,
    public_ci,
    (
        (
            "python3 tools/navigation_guard.py",
            "PR validation must enforce navigation contracts",
        ),
        (
            "testReleaseSmokeUnitTest",
            "public PR validation must compile and run Release Smoke unit tests",
        ),
        (
            "lintReleaseSmoke",
            "public PR validation must run Release Smoke lint",
        ),
        (
            "assembleReleaseSmoke",
            "public PR validation must exercise minification without production configuration",
        ),
    ),
)
forbid_tokens(
    public_ci_path,
    public_ci,
    (
        (
            "testReleaseUnitTest",
            "public PR validation must not compile the production Release variant",
        ),
        (
            "./gradlew lintRelease ",
            "public PR validation must not run production Release lint",
        ),
        (
            "./gradlew assembleRelease ",
            "public PR validation must not package the production Release variant",
        ),
        (
            "AQL_FIREBASE_PRODUCTION_CONFIG_BASE64",
            "public PR validation must not receive the production Firebase configuration",
        ),
    ),
)

release_workflow_path = ".github/workflows/android_release.yml"
release_workflow = read(release_workflow_path)
require_tokens(
    release_workflow_path,
    release_workflow,
    (
        (
            "environment: production-release",
            "production builds must remain environment-protected",
        ),
        (
            "AQL_FIREBASE_DEBUG_CONFIG_BASE64",
            "protected release must receive the real Debug Firebase config",
        ),
        (
            "AQL_FIREBASE_STAGING_CONFIG_BASE64",
            "protected release must receive the real Staging Firebase config",
        ),
        (
            "AQL_FIREBASE_PRODUCTION_CONFIG_BASE64",
            "protected release must receive the Production Firebase config",
        ),
        (
            "bash tools/release_pipeline/build_release.sh",
            "production release must use the controlled build script",
        ),
    ),
)

release_script_path = "tools/release_pipeline/build_release.sh"
release_script = read(release_script_path)
require_tokens(
    release_script_path,
    release_script,
    (
        (
            ":app:verifyFirebaseProductionEnvironmentIsolation",
            "production build must verify Firebase project separation before packaging",
        ),
        ("bundleRelease", "production release must produce a Play Store AAB"),
    ),
)

proto_dir = ROOT / "app/src/main/proto"
for required_proto in ("tank_device_assignments.proto", "known_devices.proto"):
    if not (proto_dir / required_proto).exists():
        errors.append(
            f"app/src/main/proto/{required_proto}: required Proto source is missing"
        )

legacy_tokens = (
    "tank_device_assignments_v2",
    "aql_known_devices_v2",
    "aql_device_credentials_v2",
)
for source_file in (ROOT / "app/src/main").rglob("*"):
    if not source_file.is_file() or source_file.suffix not in {
        ".kt",
        ".java",
        ".xml",
        ".proto",
    }:
        continue
    text = source_file.read_text(encoding="utf-8", errors="ignore")
    for token in legacy_tokens:
        if token in text:
            errors.append(
                f"{source_file.relative_to(ROOT)}: "
                f"legacy storage identifier is forbidden: {token}"
            )

if errors:
    print("Architecture guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print(
    "Architecture guard passed: layer boundaries, Proto authority, owner isolation, "
    "registered-only discovery, non-global credential/session rules, and public-versus-"
    "production CI variant separation are intact."
)
