#!/usr/bin/env python3
"""Fail CI when Stage 9 feedback/media architecture or recovery contracts regress."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
AQUA_APP = APP / "app/AquaApp.kt"
APP_CONTAINER = APP / "composition/AppContainer.kt"
RELEASE_SMOKE_CONTAINER = (
    ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt"
)
FEEDBACK_FRAGMENT = APP / "ui/tabs/settings/feedback/FeedbackFragment.kt"
AQUARIUM_STORE = APP / "data/aquarium/store/AquariumTankDataStoreManager.kt"
REPOSITORY = APP / "data/feedback/FirebaseFeedbackSubmissionOperations.kt"
JOURNAL = APP / "data/feedback/FeedbackSubmissionJournalStore.kt"
PROCESSOR = APP / "platform/media/FeedbackMediaProcessor.kt"
APP_MEDIA_STORAGE = APP / "platform/media/AppMediaStorage.kt"
COORDINATOR = APP / "ui/common/media/MediaFlowCoordinatorViewModel.kt"
MEDIA_RECOVERY = APP / "data/media/AppMediaRecoveryManager.kt"
OWNER_SESSION = APP / "data/auth/OwnerSessionCoordinator.kt"
CREATE_TANK_VIEW_MODEL = APP / "ui/tabs/aquarium/create/CreateTankViewModel.kt"
JOURNAL_INSTRUMENTED_TEST = (
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/data/feedback/"
    / "FeedbackSubmissionJournalStoreInstrumentedTest.kt"
)
MEDIA_STORAGE_INSTRUMENTED_TEST = (
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/platform/media/"
    / "AppMediaStorageInstrumentedTest.kt"
)
CREATE_TANK_VIEW_MODEL_TEST = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/ui/tabs/aquarium/create/"
    / "CreateTankViewModelTest.kt"
)

REQUIRED = (
    APP / "application/feedback/FeedbackSubmissionOperations.kt",
    REPOSITORY,
    JOURNAL,
    APP / "platform/media/FeedbackImagePolicy.kt",
    PROCESSOR,
    APP_MEDIA_STORAGE,
    COORDINATOR,
    MEDIA_RECOVERY,
    APP / "ui/tabs/settings/feedback/FeedbackViewModel.kt",
    CREATE_TANK_VIEW_MODEL,
    ROOT / "docs/stage9-feedback-media-contract.md",
    ROOT / "docs/stage9-commercial-gap-closure.md",
    ROOT / "app/src/test/java/com/aqua/aqualight/data/feedback/FirebaseFeedbackSubmissionOperationsTest.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/platform/media/FeedbackImagePolicyTest.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/feedback/FeedbackViewModelTest.kt",
    CREATE_TANK_VIEW_MODEL_TEST,
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/platform/media/FeedbackMediaProcessorInstrumentedTest.kt",
    MEDIA_STORAGE_INSTRUMENTED_TEST,
    JOURNAL_INSTRUMENTED_TEST,
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/ui/common/media/MediaFlowCoordinatorInstrumentedTest.kt",
)

OBSOLETE = (
    APP / "ui/tabs/aquarium/photo/TankPhotoFlowCoordinator.kt",
    APP / "data/aquarium/photo/TankPhotoStorage.kt",
    APP / "data/feedback/FeedbackOrphanStore.kt",
)

errors: list[str] = []

for path in REQUIRED:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required Stage 9 file is missing")

for path in OBSOLETE:
    if path.exists():
        errors.append(f"{path.relative_to(ROOT)}: obsolete Stage 9 implementation must stay removed")

if FEEDBACK_FRAGMENT.is_file():
    text = FEEDBACK_FRAGMENT.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "BitmapFactory",
        "Bitmap.createScaledBitmap",
        "FileOutputStream",
        "FirebaseFirestore",
        "FirebaseStorage",
        "FeedbackSubmissionCallback",
        "AndroidFeedbackMediaProcessor",
        "openInputStream(",
        "openAssetFileDescriptor(",
    ):
        if token in text:
            errors.append(
                f"{FEEDBACK_FRAGMENT.relative_to(ROOT)}: UI must not own platform/heavy work: {token}"
            )
    for token in ("FeedbackViewModel", "container.feedbackMediaProcessor"):
        if token not in text:
            errors.append(
                f"{FEEDBACK_FRAGMENT.relative_to(ROOT)}: feedback UI boundary missing: {token}"
            )

if APP_CONTAINER.is_file():
    text = APP_CONTAINER.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "val feedbackMediaProcessor: FeedbackMediaProcessor",
        "AndroidFeedbackMediaProcessor(appContext)",
    ):
        if token not in text:
            errors.append(
                f"{APP_CONTAINER.relative_to(ROOT)}: feedback media composition binding missing: {token}"
            )

if RELEASE_SMOKE_CONTAINER.is_file():
    text = RELEASE_SMOKE_CONTAINER.read_text(encoding="utf-8", errors="ignore")
    if "override val feedbackMediaProcessor: FeedbackMediaProcessor" not in text:
        errors.append(
            f"{RELEASE_SMOKE_CONTAINER.relative_to(ROOT)}: release-smoke composition parity missing"
        )
    if "context = appContext" not in text:
        errors.append(
            f"{RELEASE_SMOKE_CONTAINER.relative_to(ROOT)}: tank media composition context missing"
        )

PHOTO_CONSUMERS = (
    APP / "ui/tabs/settings/profile/EditProfileFragment.kt",
    APP / "ui/tabs/aquarium/create/steps/TankPhotoFragment.kt",
    APP / "ui/tabs/aquarium/detail/settings/TankSettingsBasicFragment.kt",
)
for path in PHOTO_CONSUMERS:
    if not path.is_file():
        continue
    text = path.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "MediaFlowCoordinatorViewModel",
        "prepareCropIntent",
        "authenticatedOwnerIdentity.requireOwnerUid()",
        "CancellationException",
        "throw cancellation",
    ):
        if token not in text:
            errors.append(
                f"{path.relative_to(ROOT)}: shared bounded/cancellation-safe media contract missing: {token}"
            )
    for token in ("FileProvider", "File.createTempFile", "UCrop.Options()"):
        if token in text:
            errors.append(
                f"{path.relative_to(ROOT)}: duplicated media ownership is forbidden: {token}"
            )

if AQUARIUM_STORE.is_file():
    store = AQUARIUM_STORE.read_text(encoding="utf-8", errors="ignore")
    if "AppMediaStorage" not in store or "AppMediaScope.TANK" not in store:
        errors.append(
            f"{AQUARIUM_STORE.relative_to(ROOT)}: aquarium persistence must use AppMediaStorage"
        )
    if "TankPhotoStorage" in store:
        errors.append(
            f"{AQUARIUM_STORE.relative_to(ROOT)}: legacy tank photo storage reference is forbidden"
        )

if AQUA_APP.is_file():
    text = AQUA_APP.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "feedbackSubmissionOperations.cleanupOrphans()",
        "AppMediaRecoveryManager(this@AquaApp).reconcileActiveOwner()",
    ):
        if token not in text:
            errors.append(
                f"{AQUA_APP.relative_to(ROOT)}: process-start recovery missing: {token}"
            )

if OWNER_SESSION.is_file():
    text = OWNER_SESSION.read_text(encoding="utf-8", errors="ignore")
    if "AppMediaRecoveryManager(appContext).reconcileOwner(normalizedOwnerUid)" not in text:
        errors.append(
            f"{OWNER_SESSION.relative_to(ROOT)}: owner-session media recovery barrier missing"
        )

if REPOSITORY.is_file():
    text = REPOSITORY.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "withContext(dispatcher)",
        "transactionMutex.withLock",
        "journalStore.put",
        "reservePending",
        "commitPending",
        "resolveForCleanup",
        "ownerUid = entry.ownerUid",
        "TRANSACTION_PENDING",
        "TRANSACTION_COMMITTED",
        "TRANSACTION_ABORTED",
        "runTransaction",
        "FIELD_USER_ID",
        "screenshotStore.delete",
        "suspendCoroutine",
        "throwIfCancellation",
    ):
        if token not in text:
            errors.append(
                f"{REPOSITORY.relative_to(ROOT)}: commercial transaction contract missing: {token}"
            )
    if "suspendCancellableCoroutine" in text:
        errors.append(
            f"{REPOSITORY.relative_to(ROOT)}: non-cancellable Firebase Task must not use cancellable await"
        )
    if ".get(Source.SERVER)" in text:
        errors.append(
            f"{REPOSITORY.relative_to(ROOT)}: get-then-delete cleanup is race-prone; use atomic fence"
        )

if JOURNAL.is_file():
    text = JOURNAL.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "FeedbackSubmissionJournalStore",
        "PendingFeedbackUpload",
        "ownerUid",
        ".commit()",
        "feedback_submission_journal_v2",
    ):
        if token not in text:
            errors.append(
                f"{JOURNAL.relative_to(ROOT)}: durable owner-aware journal contract missing: {token}"
            )
    if ".apply()" in text:
        errors.append(
            f"{JOURNAL.relative_to(ROOT)}: upload journal must be synchronously durable, not apply()"
        )

if PROCESSOR.is_file():
    text = PROCESSOR.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "Dispatchers.IO",
        "feedback_source_",
        "MAX_SOURCE_BYTES",
        "currentCoroutineContext().ensureActive()",
        "inJustDecodeBounds",
        "FileInputStream",
        ".use {",
        "CancellationException",
        "OutOfMemoryError",
        "MAX_OUTPUT_BYTES",
    ):
        if token not in text:
            errors.append(
                f"{PROCESSOR.relative_to(ROOT)}: bounded/cancellable media processing missing: {token}"
            )

if APP_MEDIA_STORAGE.is_file():
    text = APP_MEDIA_STORAGE.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "app_media_pending_v1",
        "JSON_OWNER_UID",
        ".commit()",
        "commitPendingMedia",
        "rollbackPendingMedia",
        "reconcilePendingMedia",
        "discardPendingMediaForOwner",
        "sourceFile.copyTo",
    ):
        if token not in text:
            errors.append(
                f"{APP_MEDIA_STORAGE.relative_to(ROOT)}: owner-aware media ownership contract missing: {token}"
            )
    if "return sourceUriString" not in text:
        errors.append(
            f"{APP_MEDIA_STORAGE.relative_to(ROOT)}: external non-owned URI preservation missing"
        )

if COORDINATOR.is_file():
    text = COORDINATOR.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "SavedStateHandle",
        "FeedbackMediaProcessor",
        "ownerUid",
        "prepareCropIntent",
        "preparationMutex.withLock",
        "commitSelection",
        "rollbackSelection",
        "setMaxBitmapSize",
    ):
        if token not in text:
            errors.append(
                f"{COORDINATOR.relative_to(ROOT)}: shared lifecycle/bounded media contract missing: {token}"
            )

if CREATE_TANK_VIEW_MODEL.is_file():
    text = CREATE_TANK_VIEW_MODEL.read_text(encoding="utf-8", errors="ignore")
    for token in ("SavedStateHandle", "KEY_DRAFT_JSON", "restoreDraft", "completeTank"):
        if token not in text:
            errors.append(
                f"{CREATE_TANK_VIEW_MODEL.relative_to(ROOT)}: process-death draft contract missing: {token}"
            )

TEST_EXPECTATIONS = {
    ROOT / "app/src/test/java/com/aqua/aqualight/data/feedback/FirebaseFeedbackSubmissionOperationsTest.kt": (
        "successfulSubmissionReservesOwnerBeforeUploadAndCommitsAtomically",
        "firestoreFailureAfterUploadAbortsFenceAndDeletesStorageObject",
        "ambiguousCommitErrorReturnsSuccessWhenServerFenceIsCommitted",
        "cancellationDuringCommitKeepsJournalAndDoesNotGuessRemoteOutcome",
        "cleanupFailsSafeForConflictOrUnverifiedServerState",
    ),
    ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/feedback/FeedbackViewModelTest.kt": (
        "restoresFormAndSelectedMediaThenSubmitsThroughUseCase",
        "mediaProcessingLockPreventsSubmitUntilSelectionCompletes",
        "recreationNeverReplaysAnInterruptedSubmission",
        "submissionFailureKeepsFormAndScreenshotForRetry",
    ),
    CREATE_TANK_VIEW_MODEL_TEST: (
        "draftSurvivesViewModelRecreationAndCanBeClearedAfterCommit",
    ),
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/platform/media/FeedbackMediaProcessorInstrumentedTest.kt": (
        "oversizedProviderImageUsesSampledDecodeAndCompressesWithinCommercialLimits",
        "largeUnknownLengthImageIsBoundedAndSourceStreamIsClosed",
        "sourceBeyondByteLimitIsRejectedBeforeDecodeAndStreamIsClosed",
        "cancellationIsNotConvertedToIoFailureAndStagedFileIsDeleted",
    ),
    MEDIA_STORAGE_INSTRUMENTED_TEST: (
        "recoveryNeverDeletesAnotherOwnersPendingMedia",
        "committedCandidateIsNeverRemovedByLaterReconciliation",
    ),
    JOURNAL_INSTRUMENTED_TEST: (
        "journalEntrySurvivesStoreRecreationAndCanBeRemovedDurably",
        "multipleOwnerAwareEntriesAreUpdatedWithoutMutatingPreferenceSnapshots",
    ),
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/ui/common/media/MediaFlowCoordinatorInstrumentedTest.kt": (
        "boundedPreparedSourceAndPendingCropAreCleanedAfterCancel",
        "previousPersistedMediaIsDeletedOnlyAfterReplacementCommit",
        "ownerReconciliationPreservesReferencedMediaAndExpiresOnlyOrphan",
    ),
}

for path, tokens in TEST_EXPECTATIONS.items():
    if not path.is_file():
        continue
    text = path.read_text(encoding="utf-8", errors="ignore")
    for token in tokens:
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: required regression test missing: {token}")

if errors:
    print("Stage 9 feedback/media architecture guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Stage 9 feedback/media guard passed.")
