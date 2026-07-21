#!/usr/bin/env python3
"""Fail CI when text-feedback or shared photo-media contracts regress."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
AQUA_APP = APP / "app/AquaApp.kt"
APP_CONTAINER = APP / "composition/AppContainer.kt"
RELEASE_SMOKE_CONTAINER = (
    ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt"
)
FEEDBACK_APPLICATION = APP / "application/feedback/FeedbackSubmissionOperations.kt"
FEEDBACK_REPOSITORY = APP / "data/feedback/FirebaseFeedbackSubmissionOperations.kt"
FEEDBACK_VIEW_MODEL = APP / "ui/tabs/settings/feedback/FeedbackViewModel.kt"
FEEDBACK_FRAGMENT = APP / "ui/tabs/settings/feedback/FeedbackFragment.kt"
FEEDBACK_LAYOUT = ROOT / "app/src/main/res/layout/fragment_feedback.xml"
AQUARIUM_STORE = APP / "data/aquarium/store/AquariumTankDataStoreManager.kt"
PROCESSOR = APP / "platform/media/FeedbackMediaProcessor.kt"
APP_MEDIA_STORAGE = APP / "platform/media/AppMediaStorage.kt"
COORDINATOR = APP / "ui/common/media/MediaFlowCoordinatorViewModel.kt"
MEDIA_RECOVERY = APP / "data/media/AppMediaRecoveryManager.kt"
OWNER_SESSION = APP / "data/auth/OwnerSessionCoordinator.kt"
CREATE_TANK_VIEW_MODEL = APP / "ui/tabs/aquarium/create/CreateTankViewModel.kt"
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
    FEEDBACK_APPLICATION,
    FEEDBACK_REPOSITORY,
    FEEDBACK_VIEW_MODEL,
    PROCESSOR,
    APP_MEDIA_STORAGE,
    COORDINATOR,
    MEDIA_RECOVERY,
    CREATE_TANK_VIEW_MODEL,
    ROOT / "docs/stage9-feedback-media-contract.md",
    ROOT / "docs/stage9-commercial-gap-closure.md",
    ROOT / "docs/stage9-firebase-production-activation.md",
    ROOT / "app/src/test/java/com/aqua/aqualight/data/feedback/FirebaseFeedbackSubmissionOperationsTest.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/platform/media/FeedbackImagePolicyTest.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/feedback/FeedbackViewModelTest.kt",
    CREATE_TANK_VIEW_MODEL_TEST,
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/platform/media/FeedbackMediaProcessorInstrumentedTest.kt",
    MEDIA_STORAGE_INSTRUMENTED_TEST,
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/ui/common/media/MediaFlowCoordinatorInstrumentedTest.kt",
)

OBSOLETE = (
    APP / "ui/tabs/aquarium/photo/TankPhotoFlowCoordinator.kt",
    APP / "data/aquarium/photo/TankPhotoStorage.kt",
    APP / "data/feedback/FeedbackOrphanStore.kt",
    APP / "data/feedback/FeedbackSubmissionJournalStore.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/data/feedback/FirebaseFeedbackJournalFailureTest.kt",
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/data/feedback/FeedbackSubmissionJournalStoreInstrumentedTest.kt",
    ROOT / "app/src/main/res/drawable/ic_image.xml",
    ROOT / "app/src/main/res/drawable/ic_close.xml",
    ROOT / "storage.rules",
)

errors: list[str] = []

for path in REQUIRED:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required Stage 9 file is missing")

for path in OBSOLETE:
    if path.exists():
        errors.append(f"{path.relative_to(ROOT)}: obsolete implementation must stay removed")

if FEEDBACK_FRAGMENT.is_file():
    text = FEEDBACK_FRAGMENT.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "BitmapFactory",
        "FileOutputStream",
        "FirebaseFirestore",
        "FirebaseStorage",
        "FeedbackSubmissionCallback",
        "AndroidFeedbackMediaProcessor",
        "ActivityResultContracts.GetContent",
        "feedbackMediaProcessor",
        "selectScreenshot",
        "ScreenshotSelected",
        "MediaProcessingFailed",
    ):
        if token in text:
            errors.append(
                f"{FEEDBACK_FRAGMENT.relative_to(ROOT)}: text-only feedback UI contains forbidden token: {token}"
            )
    for token in ("FeedbackViewModel", "container.feedbackSubmissionOperations"):
        if token not in text:
            errors.append(
                f"{FEEDBACK_FRAGMENT.relative_to(ROOT)}: feedback UI boundary missing: {token}"
            )

if FEEDBACK_VIEW_MODEL.is_file():
    text = FEEDBACK_VIEW_MODEL.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "FeedbackMediaProcessor",
        "ProcessedFeedbackMedia",
        "KEY_SCREENSHOT",
        "selectScreenshot",
        "cleanupOrphans",
    ):
        if token in text:
            errors.append(
                f"{FEEDBACK_VIEW_MODEL.relative_to(ROOT)}: screenshot state must stay removed: {token}"
            )
    for token in ("SavedStateHandle", "isSubmitting", "FeedbackSubmissionUseCase"):
        if token not in text:
            errors.append(
                f"{FEEDBACK_VIEW_MODEL.relative_to(ROOT)}: text-feedback state contract missing: {token}"
            )

if FEEDBACK_APPLICATION.is_file():
    text = FEEDBACK_APPLICATION.read_text(encoding="utf-8", errors="ignore")
    for token in ("java.io.File", "screenshot", "cleanupOrphans", "FeedbackOrphanCleanupResult"):
        if token in text:
            errors.append(
                f"{FEEDBACK_APPLICATION.relative_to(ROOT)}: screenshot boundary must stay removed: {token}"
            )

if FEEDBACK_REPOSITORY.is_file():
    text = FEEDBACK_REPOSITORY.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "withContext(dispatcher)",
        "documentStore.save",
        "FIELD_USER_ID",
        "suspendCoroutine",
        "CancellationException",
    ):
        if token not in text:
            errors.append(
                f"{FEEDBACK_REPOSITORY.relative_to(ROOT)}: text-feedback persistence contract missing: {token}"
            )
    for token in (
        "FirebaseStorage",
        "FeedbackScreenshotStore",
        "screenshot",
        "journalStore",
        "mediaTransaction",
        "reservePending",
        "commitPending",
        "cleanupOrphans",
    ):
        if token in text:
            errors.append(
                f"{FEEDBACK_REPOSITORY.relative_to(ROOT)}: screenshot persistence must stay removed: {token}"
            )
    if "suspendCancellableCoroutine" in text:
        errors.append(
            f"{FEEDBACK_REPOSITORY.relative_to(ROOT)}: non-cancellable Firebase Task must not use cancellable await"
        )

if FEEDBACK_LAYOUT.is_file():
    text = FEEDBACK_LAYOUT.read_text(encoding="utf-8", errors="ignore")
    for token in ("Screenshot", "feedback_attachment", "feedback_screenshot", "@drawable/ic_image"):
        if token in text:
            errors.append(
                f"{FEEDBACK_LAYOUT.relative_to(ROOT)}: screenshot UI must stay removed: {token}"
            )

for path in (
    ROOT / "app/src/main/res/values/strings.xml",
    ROOT / "app/src/main/res/values-tr/strings.xml",
):
    if not path.is_file():
        continue
    text = path.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "feedback_attachment_title",
        "feedback_screenshot_",
        "feedback_error_upload",
        "feedback_error_file_too_large",
    ):
        if token in text:
            errors.append(f"{path.relative_to(ROOT)}: obsolete screenshot string remains: {token}")

for path, tokens in {
    ROOT / "app/build.gradle": ("firebase-storage",),
    ROOT / "firestore.rules": ("screenshot", "mediaTransaction", "media_pending", "media_aborted"),
    ROOT / "firebase.json": ('"storage"',),
}.items():
    if not path.is_file():
        continue
    text = path.read_text(encoding="utf-8", errors="ignore")
    for token in tokens:
        if token in text:
            errors.append(f"{path.relative_to(ROOT)}: removed screenshot dependency remains: {token}")

if APP_CONTAINER.is_file():
    text = APP_CONTAINER.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "val feedbackMediaProcessor: FeedbackMediaProcessor",
        "AndroidFeedbackMediaProcessor(appContext)",
    ):
        if token not in text:
            errors.append(
                f"{APP_CONTAINER.relative_to(ROOT)}: shared photo media composition binding missing: {token}"
            )

if RELEASE_SMOKE_CONTAINER.is_file():
    text = RELEASE_SMOKE_CONTAINER.read_text(encoding="utf-8", errors="ignore")
    if "override val feedbackMediaProcessor: FeedbackMediaProcessor" not in text:
        errors.append(
            f"{RELEASE_SMOKE_CONTAINER.relative_to(ROOT)}: release-smoke media composition parity missing"
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
    text = AQUARIUM_STORE.read_text(encoding="utf-8", errors="ignore")
    if "AppMediaStorage" not in text or "AppMediaScope.TANK" not in text:
        errors.append(
            f"{AQUARIUM_STORE.relative_to(ROOT)}: aquarium persistence must use AppMediaStorage"
        )

if AQUA_APP.is_file():
    text = AQUA_APP.read_text(encoding="utf-8", errors="ignore")
    if "AppMediaRecoveryManager(this@AquaApp).reconcileActiveOwner()" not in text:
        errors.append(f"{AQUA_APP.relative_to(ROOT)}: shared media process-start recovery missing")
    if "feedbackSubmissionOperations.cleanupOrphans()" in text:
        errors.append(f"{AQUA_APP.relative_to(ROOT)}: removed feedback upload recovery remains")

if OWNER_SESSION.is_file():
    text = OWNER_SESSION.read_text(encoding="utf-8", errors="ignore")
    if "AppMediaRecoveryManager(appContext).reconcileOwner(normalizedOwnerUid)" not in text:
        errors.append(
            f"{OWNER_SESSION.relative_to(ROOT)}: owner-session media recovery barrier missing"
        )

if PROCESSOR.is_file():
    text = PROCESSOR.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "Dispatchers.IO",
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
        "successfulSubmissionPersistsTextFeedbackWithOwnerMetadata",
        "missingOrBlankOwnerUsesAnonymousIdentity",
        "persistenceFailureReturnsTypedFailure",
        "cancellationIsPropagated",
    ),
    ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/feedback/FeedbackViewModelTest.kt": (
        "restoresFormThenSubmitsTextFeedbackThroughUseCase",
        "synchronousSubmissionLockPreventsDuplicateRequests",
        "recreationRestoresFormWithoutReplayingSubmission",
        "submissionFailureKeepsFormForRetry",
        "invalidFormIsRejectedBeforeRepositoryCall",
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

print(
    "Stage 9 feedback/media guard passed: feedback is text-only and shared profile/tank media "
    "remains bounded, owner-aware and process-safe."
)
