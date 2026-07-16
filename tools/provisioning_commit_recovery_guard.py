#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
ANDROID_TESTS = ROOT / "app/src/androidTest/java/com/aqua/aqualight"

store = APP / "data/devices/provisioning/store/ProvisioningCommitRecoveryStore.kt"
progress = APP / "data/devices/provisioning/DefaultProvisioningProgressOperations.kt"
owner_session = APP / "data/auth/OwnerSessionCoordinator.kt"
user_data_cleaner = APP / "data/user/UserDataCleaner.kt"
test = (
    ANDROID_TESTS
    / "data/devices/provisioning/store/ProvisioningCommitRecoveryStoreInstrumentedTest.kt"
)

errors: list[str] = []
for path in (store, progress, owner_session, user_data_cleaner, test):
    if not path.is_file():
        errors.append(f"missing provisioning commit recovery file: {path.relative_to(ROOT)}")

if store.is_file():
    text = store.read_text(encoding="utf-8")
    for token in (
        "EncryptedSharedPreferences.create",
        "MasterKey.KeyScheme.AES256_GCM",
        "KnownDeviceProtoMapper.toStored",
        "StoredKnownDevice.parseFrom",
        "class DefaultProvisioningCommitRecoveryTarget",
        "DeviceKnownStore(",
        ").saveSnapshot(snapshot)",
        "DeviceCredentialStore(",
        ").saveToken(",
        "recoveryTarget.saveSnapshot",
        "recoveryTarget.saveRuntimeToken",
        "suspend fun record",
        "suspend fun recoverOwner",
        "aql_provisioning_commit_recovery",
    ):
        if token not in text:
            errors.append(f"commit recovery store is missing invariant: {token}")
    for forbidden in (
        "context.getSharedPreferences(",
        "PreferenceManager.getDefaultSharedPreferences",
    ):
        if forbidden in text:
            errors.append(f"commit recovery journal may not use plaintext storage: {forbidden}")

if progress.is_file():
    text = progress.read_text(encoding="utf-8")
    for token in (
        "ProvisioningCommitRecoveryStore(appContext)",
        "preparedRuntimeTokens",
        "commitRecoveryStore.record",
        "clearCommitRecoveryRecord",
    ):
        if token not in text:
            errors.append(f"progress adapter is missing durable commit behavior: {token}")
    if text.find("commitRecoveryStore.record") > text.find("handoffSaver.commitPreparedRegistration"):
        errors.append("commit journal must be durable before snapshot/token commit begins")

if owner_session.is_file():
    text = owner_session.read_text(encoding="utf-8")
    rollback = text.find("rollbackPendingRegistrationsForOwner")
    recover = text.find("recoverOwner(normalizedOwnerUid)")
    discard = text.find("credentialStore.discardStagedTokens()")
    if min(rollback, recover, discard) < 0:
        errors.append("owner startup is missing rollback/recovery/discard sequencing")
    elif not rollback < recover < discard:
        errors.append(
            "owner startup must roll back RAM-only work, recover durable commits, "
            "then discard unrelated staged tokens"
        )

if user_data_cleaner.is_file():
    text = user_data_cleaner.read_text(encoding="utf-8")
    for token in (
        "PROVISIONING_SESSIONS",
        "clearProvisioningData(targetOwnerUid)",
        "rollbackPendingRegistrationsForOwner(ownerUid)",
        "AqlProvisioningDraftStore(",
        "AqlProvisioningQrSecretStore(",
        ").clearOwner()",
        "ProvisioningCommitRecoveryStore(appContext)",
        ".clearOwner(ownerUid)",
        "One or more provisioning data cleanup operations failed.",
    ):
        if token not in text:
            errors.append(f"account deletion does not clear provisioning data: {token}")

if test.is_file():
    text = test.read_text(encoding="utf-8")
    for token in (
        "journalRecoversVerifiedSnapshotAndTokenIdempotently",
        "anotherOwnerCannotRecoverOrConsumeTheJournal",
        "ProvisioningCommitRecoveryTarget",
        "aql_provisioning_commit_recovery.xml",
        "assertFalse(encryptedContent.contains(RUNTIME_TOKEN))",
        "assertEquals(0, recoveryStore.recoverOwner(ownerUid))",
    ):
        if token not in text:
            errors.append(f"commit recovery instrumentation coverage is missing: {token}")

for source_root in (APP / "application", APP / "ui"):
    if not source_root.exists():
        continue
    for kotlin_file in source_root.rglob("*.kt"):
        text = kotlin_file.read_text(encoding="utf-8", errors="ignore")
        if "ProvisioningCommitRecoveryStore" in text:
            errors.append(
                f"commit recovery implementation leaked outside data/composition: "
                f"{kotlin_file.relative_to(ROOT)}"
            )

if errors:
    print("Provisioning commit recovery guard failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Provisioning commit recovery guard passed.")
