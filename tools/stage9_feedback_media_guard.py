#!/usr/bin/env python3
"""Fail CI when Stage 9 feedback/media architecture or recovery contracts regress."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
AQUA_APP = APP / "app/AquaApp.kt"
FEEDBACK_FRAGMENT = APP / "ui/tabs/settings/feedback/FeedbackFragment.kt"
AQUARIUM_STORE = APP / "data/aquarium/store/AquariumTankDataStoreManager.kt"
REPOSITORY = APP / "data/feedback/FirebaseFeedbackSubmissionOperations.kt"
JOURNAL = APP / "data/feedback/FeedbackSubmissionJournalStore.kt"
PROCESSOR = APP / "platform/media/FeedbackMediaProcessor.kt"
COORDINATOR = APP / "ui/common/media/MediaFlowCoordinatorViewModel.kt"

REQUIRED = (
    APP / "application/feedback/FeedbackSubmissionOperations.kt",
    REPOSITORY,
    JOURNAL,
    APP / "platform/media/FeedbackImagePolicy.kt",
    PROCESSOR,
    APP / "platform/media/AppMediaStorage.kt",
    COORDINATOR,
    APP / "ui/tabs/settings/feedback/FeedbackViewModel.kt",
    ROOT / "docs/stage9-feedback-media-contract.md",
    ROOT / "app/src/test/java/com/aqua/aqualight/data/feedback/FirebaseFeedbackSubmissionOperationsTest.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/platform/media/FeedbackImagePolicyTest.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/feedback/FeedbackViewModelTest.kt",
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/platform/media/FeedbackMediaProcessorInstrumentedTest.kt",
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
        "openInputStream(",
        "openAssetFileDescriptor(",
    ):
        if token in text:
            errors.append(
                f"{FEEDBACK_FRAGMENT.relative_to(ROOT)}: UI must not own heavy work: {token}"
            )
    if "FeedbackViewModel" not in text:
        errors.append(
            f"{FEEDBACK_FRAGMENT.relative_to(ROOT)}: feedback state must be ViewModel-owned"
        )

for path in (
    APP / "ui/tabs/settings/profile/EditProfileFragment.kt",
    APP / "ui/tabs/aquarium/create/steps/TankPhotoFragment.kt",
    APP / "ui/tabs/aquarium/detail/settings/TankSettingsBasicFragment.kt",
):
    if not path.is_file():
        continue
    text = path.read_text(encoding="utf-8", errors="ignore")
    if "MediaFlowCoordinatorViewModel" not in text:
        errors.append(f"{path.relative_to(ROOT)}: photo flow must use shared media coordinator")
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
    if "feedbackSubmissionOperations.cleanupOrphans()" not in text:
        errors.append(
            f"{AQUA_APP.relative_to(ROOT)}: orphan recovery must run at process startup"
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
        "TRANSACTION_PENDING",
        "TRANSACTION_COMMITTED",
        "TRANSACTION_ABORTED",
        "runTransaction",
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
        ".commit()",
        "feedback_submission_journal_v1",
    ):
        if token not in text:
            errors.append(
                f"{JOURNAL.relative_to(ROOT)}: durable journal contract missing: {token}"
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

if COORDINATOR.is_file():
    text = COORDINATOR.read_text(encoding="utf-8", errors="ignore")
    for token in ("SavedStateHandle", "commitSelection", "rollbackSelection"):
        if token not in text:
            errors.append(
                f"{COORDINATOR.relative_to(ROOT)}: required lifecycle contract missing: {token}"
            )

TEST_EXPECTATIONS = {
    ROOT / "app/src/test/java/com/aqua/aqualight/data/feedback/FirebaseFeedbackSubmissionOperationsTest.kt": (
        "successfulSubmissionReservesBeforeUploadAndCommitsAtomically",
        "firestoreFailureAfterUploadAbortsFenceAndDeletesStorageObject",
        "ambiguousCommitErrorReturnsSuccessWhenServerFenceIsCommitted",
        "cancellationDuringCommitKeepsJournalAndDoesNotGuessRemoteOutcome",
        "cleanupFailsSafeForConflictOrUnverifiedServerState",
    ),
    ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/feedback/FeedbackViewModelTest.kt": (
        "restoresFormAndSelectedMediaThenSubmitsThroughUseCase",
        "recreationNeverReplaysAnInterruptedSubmission",
        "submissionFailureKeepsFormAndScreenshotForRetry",
    ),
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/platform/media/FeedbackMediaProcessorInstrumentedTest.kt": (
        "largeUnknownLengthImageIsBoundedAndSourceStreamIsClosed",
        "sourceBeyondByteLimitIsRejectedBeforeDecodeAndStreamIsClosed",
        "cancellationIsNotConvertedToIoFailureAndStagedFileIsDeleted",
    ),
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/ui/common/media/MediaFlowCoordinatorInstrumentedTest.kt": (
        "pendingCameraAndCropFilesAreCleanedAfterCoordinatorRecreationAndCancel",
        "previousPersistedMediaIsDeletedOnlyAfterReplacementCommit",
        "rollbackKeepsPersistedMediaAndDeletesOnlyNewSelection",
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
