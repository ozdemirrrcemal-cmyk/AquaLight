#!/usr/bin/env python3
"""Fail CI when commercial text-feedback or shared image-media contracts regress."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"

FEEDBACK_APPLICATION = APP / "application/feedback/FeedbackSubmissionOperations.kt"
FEEDBACK_REPOSITORY = APP / "data/feedback/FirebaseFeedbackSubmissionOperations.kt"
FEEDBACK_VIEW_MODEL = APP / "ui/tabs/settings/feedback/FeedbackViewModel.kt"
FEEDBACK_FRAGMENT = APP / "ui/tabs/settings/feedback/FeedbackFragment.kt"
FEEDBACK_LAYOUT = ROOT / "app/src/main/res/layout/fragment_feedback.xml"
FIRESTORE_RULES = ROOT / "firestore.rules"
FIREBASE_RULES_TEST = ROOT / "firebase/rules.test.mjs"
APP_CONTAINER = APP / "composition/AppContainer.kt"
RELEASE_SMOKE_CONTAINER = (
    ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt"
)
PROCESSOR = APP / "platform/media/ImageMediaProcessor.kt"
IMAGE_POLICY = APP / "platform/media/ImageMediaPolicy.kt"
APP_MEDIA_STORAGE = APP / "platform/media/AppMediaStorage.kt"
COORDINATOR = APP / "ui/common/media/MediaFlowCoordinatorViewModel.kt"
MEDIA_RECOVERY = APP / "data/media/AppMediaRecoveryManager.kt"
OWNER_SESSION = APP / "data/auth/OwnerSessionCoordinator.kt"
AQUA_APP = APP / "app/AquaApp.kt"
CREATE_TANK_VIEW_MODEL = APP / "ui/tabs/aquarium/create/CreateTankViewModel.kt"
AQUARIUM_STORE = APP / "data/aquarium/store/AquariumTankDataStoreManager.kt"
FILE_PROVIDER_PATHS = ROOT / "app/src/main/res/xml/file_paths.xml"

IMAGE_POLICY_TEST = (
    ROOT / "app/src/test/java/com/aqua/aqualight/platform/media/ImageMediaPolicyTest.kt"
)
IMAGE_PROCESSOR_TEST = (
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/platform/media/"
    / "ImageMediaProcessorInstrumentedTest.kt"
)
MEDIA_STORAGE_TEST = (
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/platform/media/"
    / "AppMediaStorageInstrumentedTest.kt"
)
MEDIA_COORDINATOR_TEST = (
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/ui/common/media/"
    / "MediaFlowCoordinatorInstrumentedTest.kt"
)
FEEDBACK_REPOSITORY_TEST = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/data/feedback/"
    / "FirebaseFeedbackSubmissionOperationsTest.kt"
)
FEEDBACK_USE_CASE_TEST = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/application/feedback/"
    / "FeedbackSubmissionUseCaseTest.kt"
)
FEEDBACK_VIEW_MODEL_TEST = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/feedback/"
    / "FeedbackViewModelTest.kt"
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
    FEEDBACK_FRAGMENT,
    FEEDBACK_LAYOUT,
    FIRESTORE_RULES,
    FIREBASE_RULES_TEST,
    APP_CONTAINER,
    RELEASE_SMOKE_CONTAINER,
    PROCESSOR,
    IMAGE_POLICY,
    APP_MEDIA_STORAGE,
    COORDINATOR,
    MEDIA_RECOVERY,
    OWNER_SESSION,
    AQUA_APP,
    CREATE_TANK_VIEW_MODEL,
    AQUARIUM_STORE,
    FILE_PROVIDER_PATHS,
    IMAGE_POLICY_TEST,
    IMAGE_PROCESSOR_TEST,
    MEDIA_STORAGE_TEST,
    MEDIA_COORDINATOR_TEST,
    FEEDBACK_REPOSITORY_TEST,
    FEEDBACK_USE_CASE_TEST,
    FEEDBACK_VIEW_MODEL_TEST,
    CREATE_TANK_VIEW_MODEL_TEST,
    ROOT / "docs/stage9-feedback-media-contract.md",
    ROOT / "docs/stage9-commercial-gap-closure.md",
    ROOT / "docs/stage9-firebase-production-activation.md",
)

OBSOLETE_PATHS = (
    APP / "ui/tabs/aquarium/photo/TankPhotoFlowCoordinator.kt",
    APP / "data/aquarium/photo/TankPhotoStorage.kt",
    APP / "data/feedback/FeedbackOrphanStore.kt",
    APP / "data/feedback/FeedbackSubmissionJournalStore.kt",
    APP / "platform/media/FeedbackMediaProcessor.kt",
    APP / "platform/media/FeedbackImagePolicy.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/platform/media/FeedbackImagePolicyTest.kt",
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/platform/media/"
    / "FeedbackMediaProcessorInstrumentedTest.kt",
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/platform/media/"
    / "ImageMediaProcessorCompatibilityInstrumentedTest.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/data/feedback/FirebaseFeedbackJournalFailureTest.kt",
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/data/feedback/"
    / "FeedbackSubmissionJournalStoreInstrumentedTest.kt",
    ROOT / "app/src/main/res/drawable/ic_image.xml",
    ROOT / "app/src/main/res/drawable/ic_close.xml",
    ROOT / "storage.rules",
)

errors: list[str] = []

for path in REQUIRED:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required commercial file is missing")

for path in OBSOLETE_PATHS:
    if path.exists():
        errors.append(f"{path.relative_to(ROOT)}: obsolete compatibility implementation remains")


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore") if path.is_file() else ""


feedback_application = read(FEEDBACK_APPLICATION)
for token in (
    "FeedbackSubmissionPolicy",
    "EMAIL_MAX_LENGTH = 254",
    "EMAIL_LOCAL_PART_MAX_LENGTH = 64",
    "MESSAGE_MIN_LENGTH = 10",
    "MESSAGE_MAX_LENGTH = 500",
    "FeedbackSubmissionFailureKind.AUTHENTICATION",
    "FeedbackSubmissionFailureKind.VALIDATION",
):
    if token not in feedback_application:
        errors.append(f"{FEEDBACK_APPLICATION.relative_to(ROOT)}: feedback policy missing: {token}")
for token in ("java.io.File", "screenshot", "cleanupOrphans", "FeedbackOrphanCleanupResult"):
    if token in feedback_application:
        errors.append(f"{FEEDBACK_APPLICATION.relative_to(ROOT)}: obsolete screenshot boundary remains: {token}")

feedback_repository = read(FEEDBACK_REPOSITORY)
for token in (
    "FeedbackSubmissionFailureKind.AUTHENTICATION",
    "documentStore.save(documentId, data)",
    "withContext(dispatcher)",
    "request.email.trim().ifBlank { null }",
    "suspendCoroutine",
    "CancellationException",
):
    if token not in feedback_repository:
        errors.append(f"{FEEDBACK_REPOSITORY.relative_to(ROOT)}: persistence contract missing: {token}")
for token in (
    "anonymous",
    "FirebaseStorage",
    "FeedbackScreenshotStore",
    "screenshot",
    "journalStore",
    "mediaTransaction",
    "cleanupOrphans",
    "suspendCancellableCoroutine",
):
    if token in feedback_repository:
        errors.append(f"{FEEDBACK_REPOSITORY.relative_to(ROOT)}: unsafe/obsolete token remains: {token}")

feedback_view_model = read(FEEDBACK_VIEW_MODEL)
for token in (
    "FeedbackSubmissionPolicy.isEmailValid",
    "FeedbackSubmissionPolicy.MESSAGE_MIN_LENGTH",
    "FeedbackSubmissionPolicy.MESSAGE_MAX_LENGTH",
    "val request = FeedbackSubmissionRequest(",
    "isSubmitting",
    "SavedStateHandle",
):
    if token not in feedback_view_model:
        errors.append(f"{FEEDBACK_VIEW_MODEL.relative_to(ROOT)}: feedback state contract missing: {token}")
for token in ("PatternsCompat", "FeedbackMediaProcessor", "KEY_SCREENSHOT", "selectScreenshot"):
    if token in feedback_view_model:
        errors.append(f"{FEEDBACK_VIEW_MODEL.relative_to(ROOT)}: obsolete feedback token remains: {token}")

feedback_fragment = read(FEEDBACK_FRAGMENT)
for token in (
    "FeedbackSubmissionFailureKind.AUTHENTICATION",
    "FeedbackSubmissionFailureKind.VALIDATION",
    "feedback_error_message_length",
):
    if token not in feedback_fragment:
        errors.append(f"{FEEDBACK_FRAGMENT.relative_to(ROOT)}: commercial error handling missing: {token}")
for token in (
    "BitmapFactory",
    "FileOutputStream",
    "FirebaseFirestore",
    "FirebaseStorage",
    "feedbackMediaProcessor",
    "selectScreenshot",
    "ScreenshotSelected",
):
    if token in feedback_fragment:
        errors.append(f"{FEEDBACK_FRAGMENT.relative_to(ROOT)}: text-only UI contains forbidden token: {token}")

feedback_layout = read(FEEDBACK_LAYOUT)
for token in ('android:maxLength="254"', 'app:counterMaxLength="500"', 'android:maxLength="500"'):
    if token not in feedback_layout:
        errors.append(f"{FEEDBACK_LAYOUT.relative_to(ROOT)}: feedback input limit missing: {token}")
for token in ("Screenshot", "feedback_attachment", "feedback_screenshot", "@drawable/ic_image"):
    if token in feedback_layout:
        errors.append(f"{FEEDBACK_LAYOUT.relative_to(ROOT)}: screenshot UI remains: {token}")

rules = read(FIRESTORE_RULES)
for token in (
    "isAuthenticatedOwner(data.userId)",
    "data.message.size() <= 500",
    "data.email.size() <= 254",
    "allow get, delete:",
    "allow list:",
    "allow update: if false",
):
    if token not in rules:
        errors.append(f"{FIRESTORE_RULES.relative_to(ROOT)}: secure feedback rule missing: {token}")
for token in ("data.userId == 'anonymous'", "request.auth == null", "screenshot", "mediaTransaction"):
    if token in rules:
        errors.append(f"{FIRESTORE_RULES.relative_to(ROOT)}: unsafe/obsolete rule remains: {token}")

rules_test = read(FIREBASE_RULES_TEST)
for token in (
    "anonymous-feedback",
    "invalid-email",
    "oversized-message",
    "crossOwnerQuery",
    "deleteDoc(ownerRef)",
):
    if token not in rules_test:
        errors.append(f"{FIREBASE_RULES_TEST.relative_to(ROOT)}: rules regression missing: {token}")

processor = read(PROCESSOR)
for token in (
    "interface ImageMediaProcessor",
    "data class ProcessedImageMedia",
    "sealed interface ImageMediaProcessingResult",
    "enum class ImageMediaFailureKind",
    "class AndroidImageMediaProcessor",
    "ImageMediaPolicy.MAX_SOURCE_BYTES",
    "ImageMediaPolicy.MAX_OUTPUT_BYTES",
    "currentCoroutineContext().ensureActive()",
    "inJustDecodeBounds",
    "OutOfMemoryError",
    'DIRECTORY_NAME = "image_processing"',
    'OUTPUT_PREFIX = "image_output_"',
):
    if token not in processor:
        errors.append(f"{PROCESSOR.relative_to(ROOT)}: generic bounded image contract missing: {token}")

image_policy = read(IMAGE_POLICY)
for token in (
    "object ImageMediaPolicy",
    "MAX_SOURCE_BYTES",
    "MAX_SOURCE_PIXELS",
    "MAX_OUTPUT_BYTES",
    "MAX_OUTPUT_EDGE_PX",
    "MAX_OUTPUT_PIXELS",
):
    if token not in image_policy:
        errors.append(f"{IMAGE_POLICY.relative_to(ROOT)}: image policy missing: {token}")

app_container = read(APP_CONTAINER)
smoke_container = read(RELEASE_SMOKE_CONTAINER)
for path, text in ((APP_CONTAINER, app_container), (RELEASE_SMOKE_CONTAINER, smoke_container)):
    if "imageMediaProcessor: ImageMediaProcessor" not in text:
        errors.append(f"{path.relative_to(ROOT)}: generic media composition binding missing")

coordinator = read(COORDINATOR)
for token in (
    "ImageMediaProcessor",
    "ImageMediaProcessingResult",
    "ImageMediaFailureKind",
    "prepareCropIntent",
    "preparationMutex.withLock",
    "commitSelection",
    "rollbackSelection",
    "setMaxBitmapSize",
):
    if token not in coordinator:
        errors.append(f"{COORDINATOR.relative_to(ROOT)}: lifecycle-safe image contract missing: {token}")

file_provider_paths = read(FILE_PROVIDER_PATHS)
if 'name="image_processing"' not in file_provider_paths:
    errors.append(f"{FILE_PROVIDER_PATHS.relative_to(ROOT)}: image processing provider path missing")

photo_consumers = (
    APP / "ui/tabs/settings/profile/EditProfileFragment.kt",
    APP / "ui/tabs/aquarium/create/steps/TankPhotoFragment.kt",
    APP / "ui/tabs/aquarium/detail/settings/TankSettingsBasicFragment.kt",
)
for path in photo_consumers:
    text = read(path)
    for token in (
        "MediaFlowCoordinatorViewModel",
        "container.imageMediaProcessor",
        "prepareCropIntent",
        "authenticatedOwnerIdentity.requireOwnerUid()",
        "CancellationException",
        "throw cancellation",
    ):
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: shared image flow contract missing: {token}")
    for token in ("FileProvider", "File.createTempFile", "UCrop.Options()", "feedbackMediaProcessor"):
        if token in text:
            errors.append(f"{path.relative_to(ROOT)}: duplicated/legacy media ownership remains: {token}")

for path in (APP_MEDIA_STORAGE, MEDIA_RECOVERY, OWNER_SESSION, AQUA_APP, AQUARIUM_STORE):
    if not path.is_file():
        continue

storage = read(APP_MEDIA_STORAGE)
for token in (
    "app_media_pending_v1",
    "app_media_deletion_v1",
    "commitPendingMedia",
    "rollbackPendingMedia",
    "reconcilePendingMedia",
    "reconcilePendingDeletions",
    "discardPendingMediaForOwner",
):
    if token not in storage:
        errors.append(f"{APP_MEDIA_STORAGE.relative_to(ROOT)}: durable owner media contract missing: {token}")

if "AppMediaRecoveryManager(this@AquaApp).reconcileActiveOwner()" not in read(AQUA_APP):
    errors.append(f"{AQUA_APP.relative_to(ROOT)}: process-start media recovery missing")
if "AppMediaRecoveryManager(appContext).reconcileOwner(normalizedOwnerUid)" not in read(OWNER_SESSION):
    errors.append(f"{OWNER_SESSION.relative_to(ROOT)}: owner-session media recovery barrier missing")
if "AppMediaStorage" not in read(AQUARIUM_STORE) or "AppMediaScope.TANK" not in read(AQUARIUM_STORE):
    errors.append(f"{AQUARIUM_STORE.relative_to(ROOT)}: aquarium persistence must use AppMediaStorage")

LEGACY_TOKENS = (
    "FeedbackMediaProcessor",
    "AndroidFeedbackMediaProcessor",
    "ProcessedFeedbackMedia",
    "FeedbackMediaProcessingResult",
    "FeedbackMediaFailureKind",
    "FeedbackMediaSourceAccess",
    "FeedbackImagePolicy",
    "feedbackMediaProcessor",
    "feedback_media",
    "feedback_output_",
    "typealias ImageMedia",
)
for source_root in (ROOT / "app/src", ROOT / "tools"):
    for path in source_root.rglob("*"):
        if not path.is_file() or path.suffix not in {".kt", ".xml", ".py"}:
            continue
        text = read(path)
        for token in LEGACY_TOKENS:
            if token in text:
                errors.append(f"{path.relative_to(ROOT)}: legacy media token remains: {token}")

TEST_EXPECTATIONS = {
    FEEDBACK_REPOSITORY_TEST: (
        "missingOrBlankOwnerReturnsAuthenticationFailureWithoutWriting",
        "fieldsAreNormalizedAtPersistenceBoundary",
        "persistenceFailureReturnsTypedFailure",
        "cancellationIsPropagated",
    ),
    FEEDBACK_USE_CASE_TEST: (
        "invalidRequestReturnsValidationFailureWithoutRepositoryCall",
        "commercialEmailPolicyAcceptsPlusAddressAndRejectsUnsafeForms",
    ),
    FEEDBACK_VIEW_MODEL_TEST: (
        "emailLocalPartBeyond64CharactersIsRejected",
        "messageAtCommercialLimitIsAccepted",
        "messageAboveCommercialLimitIsRejected",
        "synchronousSubmissionLockPreventsDuplicateRequests",
        "editsAfterSubmitDoNotChangeValidatedRequestSnapshot",
    ),
    IMAGE_PROCESSOR_TEST: (
        "oversizedProviderImageUsesSampledDecodeAndCompressesWithinCommercialLimits",
        "largeUnknownLengthImageIsBoundedAndSourceStreamIsClosed",
        "sourceBeyondByteLimitIsRejectedBeforeDecodeAndStreamIsClosed",
        "cancellationIsNotConvertedToIoFailureAndStagedFileIsDeleted",
    ),
    MEDIA_STORAGE_TEST: (
        "recoveryNeverDeletesAnotherOwnersPendingMedia",
        "committedCandidateIsNeverRemovedByLaterReconciliation",
    ),
    MEDIA_COORDINATOR_TEST: (
        "boundedPreparedSourceAndPendingCropAreCleanedAfterCancel",
        "previousPersistedMediaIsDeletedOnlyAfterReplacementCommit",
        "ownerReconciliationPreservesReferencedMediaAndExpiresOnlyOrphan",
    ),
    CREATE_TANK_VIEW_MODEL_TEST: (
        "draftSurvivesViewModelRecreationAndCanBeClearedAfterCommit",
    ),
}

for path, tokens in TEST_EXPECTATIONS.items():
    text = read(path)
    for token in tokens:
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: required regression test missing: {token}")

for path, tokens in {
    ROOT / "app/build.gradle": ("firebase-storage",),
    ROOT / "firebase.json": ('"storage"',),
}.items():
    text = read(path)
    for token in tokens:
        if token in text:
            errors.append(f"{path.relative_to(ROOT)}: removed screenshot dependency remains: {token}")

if errors:
    print("Stage 9 commercial feedback/media architecture guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print(
    "Stage 9 commercial guard passed: feedback is authenticated and text-only; profile/tank "
    "image processing is domain-neutral, bounded, owner-aware and free of compatibility shims."
)
