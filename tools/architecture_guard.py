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
    if 'android:allowBackup="false"' not in manifest_text:
        errors.append(
            f"{manifest.relative_to(ROOT)}: Android cloud backup must remain disabled"
        )
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
require(
    assignment_path,
    assignment_store,
    "ReplaceFileCorruptionHandler",
    "corrupt assignment Proto must recover to an empty fail-closed authority",
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
require(
    known_path,
    known_store,
    "ReplaceFileCorruptionHandler",
    "corrupt known-device Proto must recover to an empty fail-closed authority",
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
for token, reason in (
    ("suspend fun stageToken(", "provisioning credentials must use two-phase persistence"),
    ("suspend fun commitStagedToken(", "staged credentials need an explicit commit boundary"),
    ("suspend fun discardStagedTokens()", "process death must discard uncommitted credentials"),
    ("suspend fun retainTokensFor(", "orphan credentials must be reconciled against durable devices"),
):
    require(credential_path, credential_store, token, reason)

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
for token, reason in (
    (
        "private val startupAppearanceSync = CompletableDeferred<Unit>()",
        "release-smoke appearance checks require an explicit application bootstrap barrier",
    ),
    (
        "internal suspend fun awaitStartupAppearanceSyncForProcess()",
        "release-smoke must be able to await authoritative appearance synchronization",
    ),
):
    require(app_path, app_bootstrap, token, reason)

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
require(
    session_path,
    session_coordinator,
    "repairOrphanedTankTasks(normalizedOwnerUid)",
    "stale tank care data must be reconciled during owner startup",
)
require(
    session_path,
    session_coordinator,
    "rollbackPendingRegistrationsForOwner(normalizedOwnerUid)",
    "owner startup must finish residual in-process provisioning rollback",
)
require(
    session_path,
    session_coordinator,
    "credentialStore.discardStagedTokens()",
    "owner startup must roll back process-killed credential staging",
)
require(
    session_path,
    session_coordinator,
    "credentialStore.retainTokensFor(",
    "owner startup must remove credentials without a durable device",
)

provisioning_saver_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/provisioning/repository/"
    "AqlProvisioningHandoffSaver.kt"
)
provisioning_saver = read(provisioning_saver_path)
for token, reason in (
    ("rollbackPendingRegistrationsForOwner(", "session teardown must be able to roll back all provisioning transactions"),
    ("credentialStore.stageToken(", "provisioning must not overwrite committed credentials before completion"),
    ("credentialStore.commitStagedToken(", "provisioning must explicitly commit its verified credential"),
    ("pendingRegistry.registerIfAbsent(", "duplicate provisioning transactions must be rejected atomically"),
):
    require(provisioning_saver_path, provisioning_saver, token, reason)

transaction_registry_test_path = (
    "app/src/test/java/com/aqua/aqualight/data/devices/provisioning/repository/"
    "AqlProvisioningTransactionRegistryTest.kt"
)
transaction_registry_test = read(transaction_registry_test_path)
for token, reason in (
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
):
    require(transaction_registry_test_path, transaction_registry_test, token, reason)

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
    proto_store = read(proto_store_path)
    require(proto_store_path, proto_store, "ReplaceFileCorruptionHandler", reason)

user_cleaner_path = "app/src/main/java/com/aqua/aqualight/data/user/UserDataCleaner.kt"
user_cleaner = read(user_cleaner_path)
for token, reason in (
    ("Step.DEVICE_ASSIGNMENTS", "account cleanup must attempt assignment removal independently"),
    ("Step.KNOWN_DEVICES", "account cleanup must attempt known-device removal independently"),
    ("Step.DEVICE_CREDENTIALS", "account cleanup must attempt credential removal independently"),
):
    require(user_cleaner_path, user_cleaner, token, reason)

credential_instrumentation_path = (
    "app/src/androidTest/java/com/aqua/aqualight/data/devices/store/"
    "DeviceCredentialStoreInstrumentedTest.kt"
)
credential_instrumentation = read(credential_instrumentation_path)
for token, reason in (
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
):
    require(
        credential_instrumentation_path,
        credential_instrumentation,
        token,
        reason,
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
for token, reason in (
    ("connectedDebugAndroidTest", "instrumentation tests must run in CI"),
    (
        "bash tools/verify_uninstall_clears_data.sh",
        "emulator CI must verify uninstall/reinstall data removal",
    ),
    (
        "CleanInstallSmokeActivity",
        "emulator CI must inspect first-launch state inside the non-debuggable candidate",
    ),
    (
        "verify_clean_install_evidence.py",
        "emulator CI must publish fail-closed clean-install evidence",
    ),
    (
        "UpgradeInstallSmokeActivity",
        "emulator CI must exercise a real package over-install",
    ),
    (
        "verify_upgrade_install_evidence.py",
        "emulator CI must publish fail-closed upgrade-install evidence",
    ),
    (
        "verify_stage14_junit_evidence.py",
        "emulator CI must publish named instrumentation evidence",
    ),
    ("api-level: [27, 36]", "emulator CI must cover minimum and target Android APIs"),
    (
        "android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d",
        "emulator CI action must remain pinned to its reviewed v2.38.0 commit",
    ),
    (
        'cmdline-tools-version: "15859902"',
        "emulator CI must pin the reviewed stable Android command-line tools",
    ),
    (
        'readlink -f "$(command -v sdkmanager)"',
        "emulator CI must resolve and validate the configured SDK manager binary",
    ),
    (
        "system-images;android-36;default;x86_64",
        "emulator CI must install the stable Android 16 system image",
    ),
    (
        "channel: stable",
        "emulator CI must use the stable SDK package channel",
    ),
    (
        "system-images/android-36/default/x86_64/package.xml",
        "emulator CI must verify the installed system-image package metadata",
    ),
    (
        "api-level: ${{ matrix.api-level }}",
        "emulator CI must bind evidence to the literal stable API matrix",
    ),
    (
        "target: default",
        "emulator CI must use the last-known-good default image target",
    ),
):
    require(emulator_workflow_path, emulator_workflow, token, reason)

android_setup_action = (
    "uses: android-actions/setup-android@"
    "9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407"
)
cmdline_tools_pin = 'cmdline-tools-version: "15859902"'
for workflow_file in sorted((ROOT / ".github/workflows").glob("*.yml")):
    workflow_text = workflow_file.read_text(encoding="utf-8", errors="ignore")
    setup_count = workflow_text.count(android_setup_action)
    if setup_count == 0:
        continue
    relative_workflow = str(workflow_file.relative_to(ROOT))
    pin_count = workflow_text.count(cmdline_tools_pin)
    if pin_count != setup_count:
        errors.append(
            f"{relative_workflow}: each Android SDK setup must use the reviewed "
            f"command-line tools pin {cmdline_tools_pin}"
        )
    forbid(
        relative_workflow,
        workflow_text,
        "cmdline-tools;latest",
        "mutable Android command-line tools packages are forbidden in CI",
    )
    forbid(
        relative_workflow,
        workflow_text,
        "sdkmanager --update",
        "unbounded Android SDK updates are forbidden in CI",
    )

clean_install_activity_path = (
    "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/"
    "CleanInstallSmokeActivity.kt"
)
clean_install_activity = read(clean_install_activity_path)
for token, reason in (
    (
        "ApplicationInfo.FLAG_DEBUGGABLE",
        "the candidate must prove it is non-debuggable from inside its sandbox",
    ),
    (
        "ApplicationInfo.FLAG_ALLOW_BACKUP",
        "the candidate must prove backup is disabled from inside its sandbox",
    ),
    (
        "FirebaseAuth.getInstance().currentUser == null",
        "clean install must prove no Firebase owner session exists",
    ),
    (
        "userPrivateProjectionFields",
        "clean install must inspect the complete private user projection",
    ),
    (
        "known_devices.pb",
        "clean install must inspect durable known and ignored devices",
    ),
    (
        "aquarium_tanks.pb",
        "clean install must inspect durable tank state",
    ),
    (
        "tank_device_assignments.pb",
        "clean install must inspect durable assignment state",
    ),
    (
        "care_tasks.pb",
        "clean install must inspect durable Care Task state",
    ),
    (
        "tankCareIntegrityEntries",
        "clean install must inspect pending Tank/Care compensation state",
    ),
    (
        "encryptedOwnerEntries",
        "clean install must inspect encrypted credential and recovery stores",
    ),
):
    require(clean_install_activity_path, clean_install_activity, token, reason)

upgrade_install_activity_path = (
    "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/"
    "UpgradeInstallSmokeActivity.kt"
)
upgrade_install_activity = read(upgrade_install_activity_path)
release_smoke_manifest_path = "app/src/releaseSmoke/AndroidManifest.xml"
release_smoke_manifest = read(release_smoke_manifest_path)
require(
    release_smoke_manifest_path,
    release_smoke_manifest,
    'android:configChanges="locale|layoutDirection"',
    "the locale seeder must not be interrupted by a test-only configuration restart",
)
for token, reason in (
    (
        "class UpgradeInstallSmokeActivity : AppCompatActivity()",
        "API 33+ application locales require an active AppCompat activity delegate",
    ),
    (
        "override fun onPostResume()",
        "application-locale validation must start after the AppCompat activity is resumed",
    ),
    (
        "same-commit-lower-version-code",
        "the first-release upgrade baseline mode must remain explicit",
    ),
    (
        "signerUnchanged",
        "upgrade validation must compare APK signer identity",
    ),
    (
        "versionCodeIncreased",
        "upgrade validation must prove versionCode monotonicity",
    ),
    (
        "processRecreated",
        "package replacement must start a new application process",
    ),
    (
        "processNonce",
        "process recreation must use a collision-resistant process identity",
    ),
    (
        "StartupAppearanceCache",
        "supported durable appearance state must survive over-install",
    ),
    (
        "awaitStartupAppearanceSyncForProcess",
        "upgrade validation must inspect appearance only after application bootstrap",
    ),
    (
        "userSettings.updateThemeMode",
        "upgrade validation must seed the production theme preference boundary",
    ),
    (
        "userSettings.updateLanguage",
        "upgrade validation must seed the production application-locale boundary",
    ),
    (
        "userPreferences.userPrefsFlow.first()",
        "upgrade validation must inspect authoritative encrypted appearance preferences",
    ),
    (
        "AppLanguageController.current()",
        "upgrade validation must inspect the Android/AppCompat locale source of truth",
    ),
    (
        "awaitAppearancePreserved()",
        "upgrade validation must await bounded appearance convergence on API 33+",
    ),
    (
        "failed checks:",
        "upgrade appearance failures must identify the source that did not converge",
    ),
    (
        'const val APPEARANCE_THEME = "light"',
        "upgrade validation must seed a non-default theme value",
    ),
    (
        "discardStagedTokens",
        "staged runtime credentials must not survive recovery",
    ),
    (
        "retainTokensFor(emptyList())",
        "orphaned committed credentials must be removed after upgrade",
    ),
):
    require(upgrade_install_activity_path, upgrade_install_activity, token, reason)

release_smoke_runner_path = "tools/run_release_smoke.sh"
release_smoke_runner = read(release_smoke_runner_path)
for token, reason in (
    (
        'adb install -r "$SMOKE_APK"',
        "the upgrade gate must replace the installed lower-version package",
    ),
    (
        "release-smoke-upgrade-baseline-apk-path.txt",
        "the upgrade gate must install the separately built lower-version APK",
    ),
    (
        "verify_upgrade_install_evidence.py",
        "the upgrade gate must fail closed on its machine-readable evidence",
    ),
    (
        "process-recreation-instrumentation",
        "rotation and process recreation must publish named JUnit evidence",
    ),
    (
        "tank-care-corruption-instrumentation",
        "Tank/Care corruption must publish named JUnit evidence",
    ),
    (
        "junit-api-${API_LEVEL}",
        "instrumentation JUnit XML must be retained independently for each API",
    ),
    (
        "connectedDebugAndroidTest --rerun-tasks",
        "sequential API runs must execute instrumentation on each emulator",
    ),
    (
        "verify_force_stop_evidence.py",
        "force-stop recovery must publish independent machine-readable evidence",
    ),
    (
        "verify_accessibility_evidence.py",
        "visual accessibility profiles must publish independent evidence",
    ),
    (
        "settings put global hide_error_dialogs 1",
        "unrelated emulator process dialogs must not cover release-smoke evidence",
    ),
    (
        "ORIGINAL_HIDE_ERROR_DIALOGS",
        "the original emulator error-dialog policy must be retained for restoration",
    ),
    (
        "restore_setting global hide_error_dialogs",
        "the emulator error-dialog policy must be restored after validation",
    ),
    (
        "android.intent.action.CLOSE_SYSTEM_DIALOGS",
        "stale Android system dialogs must be dismissed before marker inspection",
    ),
    (
        'resource-id="android:id/aerr_',
        "marker polling must detect Android error dialogs that survived suppression",
    ),
):
    require(release_smoke_runner_path, release_smoke_runner, token, reason)

junit_contract_path = "config/commercial/stage14-junit-evidence-contract.json"
junit_contract = read(junit_contract_path)
for token, reason in (
    (
        '"accessibility-instrumentation"',
        "touch-target accessibility must have an instrumentation contract",
    ),
    (
        '"accessibility-unit"',
        "large-font and touch-target policies must have a unit contract",
    ),
    (
        '"rapid-account-switch-unit"',
        "rapid account switching must have an explicit commercial test contract",
    ),
    (
        '"process-recreation-instrumentation"',
        "process recreation must have an instrumentation contract",
    ),
    (
        '"permission-permanent-denial-unit"',
        "permanent-denial policy must have an automated contract",
    ),
    (
        '"tank-care-corruption-instrumentation"',
        "Tank/Care corruption must have an instrumentation contract",
    ),
    (
        '"websocket-account-cleanup-unit"',
        "WebSocket and account cleanup must have an explicit contract",
    ),
):
    require(junit_contract_path, junit_contract, token, reason)

junit_verifier_path = "tools/verify_stage14_junit_evidence.py"
junit_verifier = read(junit_verifier_path)
for token, reason in (
    (
        "JUnit testcase did not pass",
        "failed or skipped named tests must fail the commercial gate",
    ),
    (
        "must contain exactly one passing",
        "missing or duplicate named tests must fail the commercial gate",
    ),
    (
        "release-smoke",
        "release evidence must include the releaseSmoke unit-test variant",
    ),
    (
        "SUPPORTED_API_LEVELS = (27, 36)",
        "instrumentation evidence must remain bound to API 27 and API 36",
    ),
):
    require(junit_verifier_path, junit_verifier, token, reason)

process_safe_instrumentation_path = (
    "app/src/androidTest/java/com/aqua/aqualight/ui/common/feedback/"
    "ProcessSafeFeedbackInstrumentedTest.kt"
)
process_safe_instrumentation = read(process_safe_instrumentation_path)
for token, reason in (
    (
        "feedbackAndCareSheetsSurviveActivityRecreationWithArgumentsIntact",
        "Care UI must survive real Activity recreation",
    ),
    (
        "tankEditorSurvivesActivityRecreationWithArgumentsIntact",
        "Tank UI must survive real Activity recreation",
    ),
):
    require(
        process_safe_instrumentation_path,
        process_safe_instrumentation,
        token,
        reason,
    )

force_stop_verifier_path = "tools/verify_force_stop_evidence.py"
force_stop_verifier = read(force_stop_verifier_path)
for token, reason in (
    (
        "ACCOUNT_DELETION_PROCESS_DEATH_PREPARED",
        "force-stop evidence must prove each durable checkpoint was prepared",
    ),
    (
        "pid-(\\d+)-to-(\\d+)",
        "force-stop evidence must prove recovery used a new process",
    ),
    (
        '"scenarioCount": len(scenario_evidence)',
        "force-stop evidence must record the complete recovery matrix",
    ),
):
    require(force_stop_verifier_path, force_stop_verifier, token, reason)

accessibility_verifier_path = "tools/verify_accessibility_evidence.py"
accessibility_verifier = read(accessibility_verifier_path)
for token, reason in (
    (
        '"large-font-light"',
        "accessibility evidence must retain the 200% light profile",
    ),
    (
        '"rtl-dark"',
        "accessibility evidence must retain the RTL dark profile",
    ),
    (
        "iconAccessibilityPassedInsideCandidate",
        "the candidate must attest its icon-label scan",
    ),
    (
        "largeFontClippingCheckPassedInsideCandidate",
        "the candidate must attest its 200% font clipping scan",
    ),
    (
        "screenshot set mismatch",
        "missing or unknown visual evidence must fail closed",
    ),
):
    require(
        accessibility_verifier_path,
        accessibility_verifier,
        token,
        reason,
    )

uninstall_test_path = "tools/verify_uninstall_clears_data.sh"
uninstall_test = read(uninstall_test_path)
for token, reason in (
    ("adb uninstall", "the uninstall smoke test must remove the application package"),
    ("known_devices.pb", "the uninstall smoke test must cover durable known devices"),
    ("device_credentials.xml", "the uninstall smoke test must cover encrypted credentials"),
):
    require(uninstall_test_path, uninstall_test, token, reason)

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

detekt_gradle_path = "gradle/aqualight-detekt.gradle"
detekt_gradle = read(detekt_gradle_path)
for token, reason in (
    (
        "aqualight-detekt-advisory.yml",
        "Detekt must retain an all-default-rules advisory analysis",
    ),
    (
        "advisory-debt-baseline.json",
        "Detekt advisory debt must use a versioned inventory",
    ),
    (
        "verify_detekt_policy.py",
        "Detekt blocker and advisory evidence must be fail-closed",
    ),
    (
        'tasks.register("verifyDetektPolicy", Exec)',
        "Detekt policy verification must be a first-class Gradle gate",
    ),
    (
        "dependsOn(detektPolicyGate)",
        "Android lint/check tasks must not bypass the Detekt policy gate",
    ),
):
    require(detekt_gradle_path, detekt_gradle, token, reason)

detekt_policy_path = "config/detekt/aqualight-detekt.yml"
detekt_policy = read(detekt_policy_path)
for token, reason in (
    ("maxIssues: 0", "Detekt blocker findings must remain zero"),
    ("warningsAsErrors: true", "Detekt configuration warnings must fail closed"),
    ("potential-bugs:", "Detekt potential-bug rules must remain enabled"),
):
    require(detekt_policy_path, detekt_policy, token, reason)

android_workflow_path = ".github/workflows/android.yml"
android_workflow = read(android_workflow_path)
for token, reason in (
    (
        ":app:verifyDetektPolicy",
        "PR CI must enforce zero blocker findings and zero new Detekt debt",
    ),
    (
        "verify_stage14_junit_evidence.py",
        "PR CI must materialize named unit-test evidence",
    ),
    (
        "rapid-account-switch-unit",
        "PR CI must prove rapid account switching",
    ),
    (
        "accessibility-unit",
        "PR CI must prove automated accessibility policies",
    ),
    (
        "python3 tools/ws_v1_commercial_closure_guard.py",
        "PR CI must reject WS v1 migration residue and parallel runtime paths",
    ),
    (
        "websocket-account-cleanup-unit",
        "PR CI must prove WebSocket and account cleanup",
    ),
):
    require(android_workflow_path, android_workflow, token, reason)

release_quality_path = "tools/release_pipeline/guard_quality.sh"
release_quality = read(release_quality_path)
for token, reason in (
    (
        ":app:verifyDetektPolicy",
        "commercial release quality must enforce the Detekt policy gate",
    ),
    (
        "release-smoke=app/build/test-results/testReleaseSmokeUnitTest",
        "commercial behavior evidence must cover the releaseSmoke variant",
    ),
    (
        "release=app/build/test-results/testReleaseUnitTest",
        "commercial behavior evidence must cover the release variant",
    ),
    (
        "verify_stage14_junit_evidence.py",
        "commercial release quality must materialize named behavior evidence",
    ),
    (
        "ws_v1_commercial_closure_guard.py",
        "commercial release quality must enforce WS v1 closure",
    ),
):
    require(release_quality_path, release_quality, token, reason)

release_workflow_path = ".github/workflows/android_release.yml"
release_workflow = read(release_workflow_path)
for token, reason in (
    (
        "gh api --paginate --slurp",
        "the controlled release must query the complete paginated issue inventory",
    ),
    (
        "verify_release_blockers.py",
        "the controlled release must fail closed on critical, high and untriaged issues",
    ),
    (
        "AQL_STAGE14_MANUAL_ACCEPTANCE_BASE64",
        "manual acceptance must come from the protected release environment",
    ),
    (
        "verify_manual_acceptance.py",
        "signed candidate physical gates must be machine-verified",
    ),
    (
        "AquaLight-Candidate-",
        "the signed candidate must be archived before physical acceptance",
    ),
    (
        "candidate_run_id",
        "finalization must select one immutable candidate workflow run",
    ),
    (
        "release_candidate_manifest.py verify",
        "finalization must rehash the selected candidate before acceptance",
    ),
    (
        "AquaLight-Pre-Security-Quality-",
        "final evidence must retain the verified quality artifact",
    ),
    (
        "AquaLight-Instrumentation-",
        "final evidence must retain the verified API 27/36 artifact",
    ),
):
    require(release_workflow_path, release_workflow, token, reason)

blocker_verifier_path = "tools/verify_release_blockers.py"
blocker_verifier = read(blocker_verifier_path)
for token, reason in (
    (
        '"severity:critical"',
        "the issue inventory must recognize the canonical critical label",
    ),
    (
        '"severity:high"',
        "the issue inventory must recognize the canonical high label",
    ),
    (
        'RELEASE_BLOCKER_LABEL = "release:blocker"',
        "the issue inventory must recognize the canonical release-blocker label",
    ),
    (
        '"untriagedIssuesBlockRelease": True',
        "missing issue triage must fail closed",
    ),
):
    require(blocker_verifier_path, blocker_verifier, token, reason)

manual_verifier_path = "tools/verify_manual_acceptance.py"
manual_verifier = read(manual_verifier_path)
for token, reason in (
    (
        '"signed-apk-clean-install"',
        "manual evidence must cover signed APK clean install and launch",
    ),
    (
        '"authentication-account-isolation"',
        "manual evidence must cover real-device account isolation",
    ),
    (
        '"process-restart-reboot"',
        "manual evidence must cover force-stop and a physical reboot",
    ),
    (
        '"permission-connectivity-resilience"',
        "manual evidence must cover permission and connectivity resilience",
    ),
    (
        '"critical-end-to-end"',
        "manual evidence must cover the signed candidate critical path",
    ),
    (
        '"candidateApproval"',
        "manual evidence must bind the candidate run and artifact digests",
    ),
    (
        '"production-release-environment-secret"',
        "manual evidence must retain its protected-environment source",
    ),
):
    require(manual_verifier_path, manual_verifier, token, reason)

final_evidence_path = "tools/generate_stage14_final_evidence.py"
final_evidence = read(final_evidence_path)
for token, reason in (
    (
        "QUALITY_STAGE14_JSON",
        "final evidence must enforce the exact named quality set",
    ),
    (
        "INSTRUMENTATION_STAGE14_JSON",
        "final evidence must enforce the exact API 27/36 set",
    ),
    (
        '"release-mapping"',
        "final evidence must retain the production obfuscation mapping",
    ),
    (
        '"manual-acceptance"',
        "final evidence must retain protected manual acceptance",
    ),
    (
        '"approved-for-archive"',
        "final evidence must expose an explicit archive decision",
    ),
):
    require(final_evidence_path, final_evidence, token, reason)

materialize_path = "tools/release_pipeline/materialize_evidence.sh"
materialize = read(materialize_path)
for token, reason in (
    (
        "generate_stage14_final_evidence.py",
        "the supply-chain stage must generate the complete final manifest",
    ),
    (
        "manualAcceptanceSha256",
        "the release identity must bind protected manual evidence",
    ),
    (
        "candidate_run_id",
        "the release identity must bind the accepted candidate run",
    ),
    (
        "releaseBlockerInventorySha256",
        "the release identity must bind the final blocker inventory",
    ),
):
    require(materialize_path, materialize, token, reason)

final_archive_path = "tools/release_pipeline/verify_final_archive.sh"
final_archive = read(final_archive_path)
for token, reason in (
    (
        "approved-for-archive",
        "final archive must require the explicit final decision",
    ),
    (
        "Final artifact order does not match the Stage 14 policy",
        "final archive must reverify the complete artifact contract",
    ),
    (
        "Artifact SHA-256 mismatch",
        "final archive must rehash every retained evidence file",
    ),
    (
        "release_candidate_manifest.py verify",
        "final archive must reverify the immutable candidate manifest",
    ),
):
    require(final_archive_path, final_archive, token, reason)

pr_workflow_path = ".github/workflows/codeql.yml"
pr_workflow = read(pr_workflow_path)
for token, reason in (
    ("python3 tools/navigation_guard.py", "PR validation must enforce navigation contracts"),
    (
        "python3 tools/ws_v1_commercial_closure_guard.py",
        "PR validation must enforce WS v1 commercial closure",
    ),
    ("testReleaseUnitTest", "PR validation must compile and run Release unit tests"),
    ("lintRelease", "PR validation must run Release lint"),
    ("assembleRelease", "PR validation must exercise minification and release packaging"),
):
    require(pr_workflow_path, pr_workflow, token, reason)

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
