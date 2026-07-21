#!/usr/bin/env python3
"""Fail CI when text-only feedback or shared Stage 9 media contracts regress."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
AQUA_APP = APP / "app/AquaApp.kt"
APP_CONTAINER = APP / "composition/AppContainer.kt"
RELEASE_SMOKE_CONTAINER = (
    ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt"
)
FEEDBACK_ROOT = APP / "ui/tabs/settings/feedback"
FEEDBACK_FRAGMENT = FEEDBACK_ROOT / "FeedbackFragment.kt"
FEEDBACK_VIEW_MODEL = FEEDBACK_ROOT / "FeedbackViewModel.kt"
FEEDBACK_CONTRACT = APP / "application/feedback/FeedbackSubmissionOperations.kt"
FEEDBACK_REPOSITORY = APP / "data/feedback/FirebaseFeedbackSubmissionOperations.kt"
FEEDBACK_DATA_ROOT = APP / "data/feedback"
FEEDBACK_LAYOUT = ROOT / "app/src/main/res/layout/fragment_feedback.xml"
FEEDBACK_STRINGS = (
    ROOT / "app/src/main/res/values/strings.xml",
    ROOT / "app/src/main/res/values-tr/strings.xml",
)
AQUARIUM_STORE = APP / "data/aquarium/store/AquariumTankDataStoreManager.kt"
PROCESSOR = APP / "platform/media/BoundedImageProcessor.kt"
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

FEEDBACK_USE_CASE_TEST = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/application/feedback/"
    / "FeedbackSubmissionUseCaseTest.kt"
)
FEEDBACK_REPOSITORY_TEST = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/data/feedback/"
    / "FirebaseFeedbackSubmissionOperationsTest.kt"
)
FEEDBACK_VIEW_MODEL_TEST = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/feedback/"
    / "FeedbackViewModelTest.kt"
)
PROCESSOR_INSTRUMENTED_TEST = (
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/platform/media/"
    / "BoundedImageProcessorInstrumentedTest.kt"
)
COORDINATOR_INSTRUMENTED_TEST = (
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/ui/common/media/"
    / "MediaFlowCoordinatorInstrumentedTest.kt"
)

REQUIRED = (
    FEEDBACK_CONTRACT,
    FEEDBACK_REPOSITORY,
    APP / "platform/media/BoundedImagePolicy.kt",
    PROCESSOR,
    APP_MEDIA_STORAGE,
    COORDINATOR,
    MEDIA_RECOVERY,
    FEEDBACK_VIEW_MODEL,
    CREATE_TANK_VIEW_MODEL,
    ROOT / "docs/stage9-feedback-media-contract.md",
    ROOT / "docs/stage9-commercial-gap-closure.md",
    ROOT / "docs/stage9-firebase-production-activation.md",
    FEEDBACK_USE_CASE_TEST,
    FEEDBACK_REPOSITORY_TEST,
    ROOT / "app/src/test/java/com/aqua/aqualight/platform/media/BoundedImagePolicyTest.kt",
    FEEDBACK_VIEW_MODEL_TEST,
    CREATE_TANK_VIEW_MODEL_TEST,
    PROCESSOR_INSTRUMENTED_TEST,
    MEDIA_STORAGE_INSTRUMENTED_TEST,
    COORDINATOR_INSTRUMENTED_TEST,
    ROOT / "firestore.rules",
    ROOT / "storage.rules",
    ROOT / "firebase/rules.test.mjs",
)

OBSOLETE = (
    APP / "ui/tabs/aquarium/photo/TankPhotoFlowCoordinator.kt",
    APP / "data/aquarium/photo/TankPhotoStorage.kt",
    APP / "data/feedback/FeedbackOrphanStore.kt",
    APP / "data/feedback/FeedbackSubmissionJournalStore.kt",
    APP / "platform/media/FeedbackImagePolicy.kt",
    APP / "platform/media/FeedbackMediaProcessor.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/data/feedback/FirebaseFeedbackJournalFailureTest.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/platform/media/FeedbackImagePolicyTest.kt",
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/data/feedback/"
    / "FeedbackSubmissionJournalStoreInstrumentedTest.kt",
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/platform/media/"
    / "FeedbackMediaProcessorInstrumentedTest.kt",
)

errors: list[str] = []

for path in REQUIRED:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required Stage 9 file is missing")

for path in OBSOLETE:
    if path.exists():
        errors.append(f"{path.relative_to(ROOT)}: obsolete implementation must stay removed")

if FEEDBACK_CONTRACT.is_file():
    text = FEEDBACK_CONTRACT.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "suspend fun submit(",
        "request: FeedbackSubmissionRequest",
        "FeedbackSubmissionFailureKind",
        "PERSISTENCE",
        "GENERIC",
    ):
        if token not in text:
            errors.append(
                f"{FEEDBACK_CONTRACT.relative_to(ROOT)}: text feedback contract missing: {token}"
            )
    for token in (
        "java.io.File",
        "screenshot",
        "cleanupOrphans",
        "FeedbackOrphanCleanupResult",
        "UPLOAD",
        "ROLLBACK",
        "storagePath",
        "rollbackCause",
    ):
        if token in text:
            errors.append(
                f"{FEEDBACK_CONTRACT.relative_to(ROOT)}: retired media contract remains: {token}"
            )

if FEEDBACK_FRAGMENT.is_file():
    text = FEEDBACK_FRAGMENT.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "BitmapFactory",
        "FileOutputStream",
        "FirebaseFirestore",
        "FirebaseStorage",
        "ActivityResultContracts.GetContent",
        "boundedImageProcessor",
        "selectScreenshot",
        "clearScreenshot",
        "ScreenshotSelected",
        "MediaProcessingFailed",
    ):
        if token in text:
            errors.append(
                f"{FEEDBACK_FRAGMENT.relative_to(ROOT)}: retired feedback attachment remains: {token}"
            )
    if "FeedbackViewModel" not in text:
        errors.append(
            f"{FEEDBACK_FRAGMENT.relative_to(ROOT)}: FeedbackViewModel boundary is missing"
        )

if FEEDBACK_ROOT.is_dir():
    for source in FEEDBACK_ROOT.rglob("*.kt"):
        text = source.read_text(encoding="utf-8", errors="ignore")
        if "screenshot" in text.lower():
            errors.append(
                f"{source.relative_to(ROOT)}: feedback UI must remain text-only"
            )

if FEEDBACK_DATA_ROOT.is_dir():
    for source in FEEDBACK_DATA_ROOT.rglob("*.kt"):
        text = source.read_text(encoding="utf-8", errors="ignore")
        for token in (
            "screenshot",
            "FeedbackSubmissionJournalStore",
            "PendingFeedbackUpload",
            "mediaTransaction",
            "FirebaseStorage",
            "Firebase.storage",
            "cleanupOrphans",
        ):
            if token.lower() in text.lower():
                errors.append(
                    f"{source.relative_to(ROOT)}: retired feedback media data path remains: {token}"
                )

for source in APP.rglob("*.kt"):
    text = source.read_text(encoding="utf-8", errors="ignore")
    for token in ("feedback_screenshots", "FeedbackScreenshot", "FirebaseStorage"):
        if token in text:
            errors.append(f"{source.relative_to(ROOT)}: retired Storage code remains: {token}")

if FEEDBACK_LAYOUT.is_file():
    text = FEEDBACK_LAYOUT.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "Screenshot",
        "feedback_attachment_title",
        "feedback_screenshot_",
        "@drawable/ic_image",
        "@drawable/ic_close",
    ):
        if token in text:
            errors.append(
                f"{FEEDBACK_LAYOUT.relative_to(ROOT)}: retired attachment view remains: {token}"
            )

for path in FEEDBACK_STRINGS:
    if not path.is_file():
        continue
    text = path.read_text(encoding="utf-8", errors="ignore")
    for resource_name in (
        "feedback_attachment_title",
        "feedback_screenshot_add",
        "feedback_screenshot_selected",
        "feedback_error_upload",
        "feedback_error_file_too_large",
    ):
        if f'name="{resource_name}"' in text:
            errors.append(f"{path.relative_to(ROOT)}: retired resource remains: {resource_name}")

build_gradle = ROOT / "app/build.gradle"
if build_gradle.is_file() and "firebase-storage" in build_gradle.read_text(
    encoding="utf-8", errors="ignore"
):
    errors.append(f"{build_gradle.relative_to(ROOT)}: Firebase Storage dependency must stay removed")

if APP_CONTAINER.is_file():
    text = APP_CONTAINER.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "val boundedImageProcessor: BoundedImageProcessor",
        "AndroidBoundedImageProcessor(appContext)",
    ):
        if token not in text:
            errors.append(
                f"{APP_CONTAINER.relative_to(ROOT)}: shared photo media binding missing: {token}"
            )

if RELEASE_SMOKE_CONTAINER.is_file():
    text = RELEASE_SMOKE_CONTAINER.read_text(encoding="utf-8", errors="ignore")
    if "override val boundedImageProcessor: BoundedImageProcessor" not in text:
        errors.append(
            f"{RELEASE_SMOKE_CONTAINER.relative_to(ROOT)}: shared media composition parity missing"
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
                f"{path.relative_to(ROOT)}: shared bounded media contract missing: {token}"
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
    if "TankPhotoStorage" in text:
        errors.append(
            f"{AQUARIUM_STORE.relative_to(ROOT)}: legacy tank photo storage reference is forbidden"
        )

if AQUA_APP.is_file():
    text = AQUA_APP.read_text(encoding="utf-8", errors="ignore")
    if "AppMediaRecoveryManager(this@AquaApp).reconcileActiveOwner()" not in text:
        errors.append(f"{AQUA_APP.relative_to(ROOT)}: shared media startup recovery missing")
    if "feedbackSubmissionOperations.cleanupOrphans()" in text:
        errors.append(f"{AQUA_APP.relative_to(ROOT)}: retired feedback cleanup remains")

if OWNER_SESSION.is_file():
    text = OWNER_SESSION.read_text(encoding="utf-8", errors="ignore")
    if "AppMediaRecoveryManager(appContext).reconcileOwner(normalizedOwnerUid)" not in text:
        errors.append(
            f"{OWNER_SESSION.relative_to(ROOT)}: owner-session media recovery barrier missing"
        )

if FEEDBACK_REPOSITORY.is_file():
    text = FEEDBACK_REPOSITORY.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "withContext(dispatcher)",
        "documentStore.save",
        "ANONYMOUS_OWNER_UID",
        "FIELD_USER_ID",
        "FIELD_CREATED_AT",
        "CancellationException",
    ):
        if token not in text:
            errors.append(
                f"{FEEDBACK_REPOSITORY.relative_to(ROOT)}: text persistence contract missing: {token}"
            )

if PROCESSOR.is_file():
    text = PROCESSOR.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "Dispatchers.IO",
        "image_source_",
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
    for token in (
        "fun restore(",
        "displayName",
        "screenshot",
        "feedback_media",
        "feedback_source_",
        "feedback_output_",
    ):
        if token.lower() in text.lower():
            errors.append(
                f"{PROCESSOR.relative_to(ROOT)}: retired feedback-media residue remains: {token}"
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
                f"{APP_MEDIA_STORAGE.relative_to(ROOT)}: owner-aware media contract missing: {token}"
            )
    if "return sourceUriString" not in text:
        errors.append(
            f"{APP_MEDIA_STORAGE.relative_to(ROOT)}: external URI preservation missing"
        )

if COORDINATOR.is_file():
    text = COORDINATOR.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "SavedStateHandle",
        "BoundedImageProcessor",
        "ownerUid",
        "prepareCropIntent",
        "preparationMutex.withLock",
        "commitSelection",
        "rollbackSelection",
        "setMaxBitmapSize",
    ):
        if token not in text:
            errors.append(
                f"{COORDINATOR.relative_to(ROOT)}: shared lifecycle media contract missing: {token}"
            )

if CREATE_TANK_VIEW_MODEL.is_file():
    text = CREATE_TANK_VIEW_MODEL.read_text(encoding="utf-8", errors="ignore")
    for token in ("SavedStateHandle", "KEY_DRAFT_JSON", "restoreDraft", "completeTank"):
        if token not in text:
            errors.append(
                f"{CREATE_TANK_VIEW_MODEL.relative_to(ROOT)}: process-death draft contract missing: {token}"
            )

firestore_rules = ROOT / "firestore.rules"
if firestore_rules.is_file():
    text = firestore_rules.read_text(encoding="utf-8", errors="ignore")
    for token in ("textFeedbackIsValid", "allow create", "allow read, delete", "allow update: if false"):
        if token not in text:
            errors.append(f"{firestore_rules.relative_to(ROOT)}: text-only rule missing: {token}")
    for token in ("screenshot", "mediaTransaction", "feedback_screenshots"):
        if token.lower() in text.lower():
            errors.append(f"{firestore_rules.relative_to(ROOT)}: retired media rule remains: {token}")

storage_rules = ROOT / "storage.rules"
if storage_rules.is_file():
    text = storage_rules.read_text(encoding="utf-8", errors="ignore")
    if "match /{allPaths=**}" not in text or "allow read, write: if false" not in text:
        errors.append(f"{storage_rules.relative_to(ROOT)}: global deny-all policy is missing")
    if "feedback_screenshots" in text:
        errors.append(f"{storage_rules.relative_to(ROOT)}: retired feature path remains")

TEST_EXPECTATIONS = {
    FEEDBACK_USE_CASE_TEST: (
        "submitForwardsTextRequestToRepository",
    ),
    FEEDBACK_REPOSITORY_TEST: (
        "authenticatedTextSubmissionPersistsOnlyFeedbackFields",
        "missingAuthenticatedOwnerPersistsAnonymousTextFeedback",
        "persistenceFailureReturnsTypedFailure",
        "cancellationFromPersistenceIsPropagated",
    ),
    FEEDBACK_VIEW_MODEL_TEST: (
        "restoresFormAndSubmitsTextThroughUseCase",
        "invalidFormSetsValidationErrorsWithoutSubmitting",
        "synchronousBusyLockPreventsDoubleSubmit",
        "recreationNeverReplaysAnInterruptedSubmission",
        "submissionFailureKeepsFormForRetry",
    ),
    CREATE_TANK_VIEW_MODEL_TEST: (
        "draftSurvivesViewModelRecreationAndCanBeClearedAfterCommit",
    ),
    PROCESSOR_INSTRUMENTED_TEST: (
        "oversizedProviderImageUsesSampledDecodeAndCompressesWithinCommercialLimits",
        "largeUnknownLengthImageIsBoundedAndSourceStreamIsClosed",
        "sourceBeyondByteLimitIsRejectedBeforeDecodeAndStreamIsClosed",
        "cancellationIsNotConvertedToIoFailureAndStagedFileIsDeleted",
    ),
    MEDIA_STORAGE_INSTRUMENTED_TEST: (
        "recoveryNeverDeletesAnotherOwnersPendingMedia",
        "committedCandidateIsNeverRemovedByLaterReconciliation",
    ),
    COORDINATOR_INSTRUMENTED_TEST: (
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
    print("Stage 9 text-feedback/shared-media architecture guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Stage 9 text-feedback/shared-media guard passed.")
