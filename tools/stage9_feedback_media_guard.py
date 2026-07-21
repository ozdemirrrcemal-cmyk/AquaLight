#!/usr/bin/env python3
"""Commercial architecture guard for authenticated text feedback and shared image media."""

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
FILE_PROVIDER_PATHS = ROOT / "app/src/main/res/xml/file_paths.xml"

IMAGE_POLICY_TEST = ROOT / "app/src/test/java/com/aqua/aqualight/platform/media/ImageMediaPolicyTest.kt"
IMAGE_PROCESSOR_TEST = (
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/platform/media/"
    / "ImageMediaProcessorInstrumentedTest.kt"
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
    FILE_PROVIDER_PATHS,
    IMAGE_POLICY_TEST,
    IMAGE_PROCESSOR_TEST,
    MEDIA_COORDINATOR_TEST,
    FEEDBACK_REPOSITORY_TEST,
    FEEDBACK_USE_CASE_TEST,
    FEEDBACK_VIEW_MODEL_TEST,
    ROOT / "docs/stage9-feedback-media-contract.md",
    ROOT / "docs/stage9-commercial-gap-closure.md",
    ROOT / "docs/stage9-firebase-production-activation.md",
)

OBSOLETE_PATHS = (
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
    ROOT / "app/src/main/res/drawable/ic_image.xml",
    ROOT / "app/src/main/res/drawable/ic_close.xml",
    ROOT / "storage.rules",
)

errors: list[str] = []


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore") if path.is_file() else ""


for path in REQUIRED:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required commercial file is missing")

for path in OBSOLETE_PATHS:
    if path.exists():
        errors.append(f"{path.relative_to(ROOT)}: obsolete compatibility implementation remains")


def require_tokens(path: Path, tokens: tuple[str, ...], contract: str) -> None:
    text = read(path)
    for token in tokens:
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: {contract} missing: {token}")


def forbid_tokens(path: Path, tokens: tuple[str, ...], contract: str) -> None:
    text = read(path)
    for token in tokens:
        if token in text:
            errors.append(f"{path.relative_to(ROOT)}: {contract} remains: {token}")


require_tokens(
    FEEDBACK_APPLICATION,
    (
        "FeedbackSubmissionPolicy",
        "EMAIL_MAX_LENGTH = 254",
        "EMAIL_LOCAL_PART_MAX_LENGTH = 64",
        "MESSAGE_MIN_LENGTH = 10",
        "MESSAGE_MAX_LENGTH = 500",
        "FeedbackSubmissionFailureKind.AUTHENTICATION",
        "FeedbackSubmissionFailureKind.VALIDATION",
    ),
    "feedback policy",
)
forbid_tokens(
    FEEDBACK_APPLICATION,
    ("java.io.File", "screenshot", "cleanupOrphans", "FeedbackOrphanCleanupResult"),
    "obsolete screenshot boundary",
)

require_tokens(
    FEEDBACK_REPOSITORY,
    (
        "FeedbackSubmissionFailureKind.AUTHENTICATION",
        "documentStore.save(documentId, data)",
        "request.email.trim().ifBlank { null }",
        "withContext(dispatcher)",
        "suspendCoroutine",
        "CancellationException",
    ),
    "authenticated persistence contract",
)
forbid_tokens(
    FEEDBACK_REPOSITORY,
    (
        "anonymous",
        "FirebaseStorage",
        "screenshot",
        "journalStore",
        "mediaTransaction",
        "cleanupOrphans",
        "suspendCancellableCoroutine",
    ),
    "unsafe or obsolete feedback token",
)

require_tokens(
    FEEDBACK_VIEW_MODEL,
    (
        "FeedbackSubmissionPolicy.isEmailValid",
        "FeedbackSubmissionPolicy.MESSAGE_MIN_LENGTH",
        "FeedbackSubmissionPolicy.MESSAGE_MAX_LENGTH",
        "val request = FeedbackSubmissionRequest(",
        "SavedStateHandle",
        "isSubmitting",
    ),
    "feedback state contract",
)
forbid_tokens(
    FEEDBACK_VIEW_MODEL,
    ("PatternsCompat", "FeedbackMediaProcessor", "KEY_SCREENSHOT", "selectScreenshot"),
    "obsolete feedback state",
)

require_tokens(
    FEEDBACK_FRAGMENT,
    (
        "FeedbackSubmissionFailureKind.AUTHENTICATION",
        "FeedbackSubmissionFailureKind.VALIDATION",
        "feedback_error_message_length",
    ),
    "commercial feedback error handling",
)
forbid_tokens(
    FEEDBACK_FRAGMENT,
    ("FirebaseStorage", "feedbackMediaProcessor", "selectScreenshot", "ScreenshotSelected"),
    "screenshot UI dependency",
)

require_tokens(
    FEEDBACK_LAYOUT,
    ('android:maxLength="254"', 'app:counterMaxLength="500"', 'android:maxLength="500"'),
    "feedback input limit",
)
forbid_tokens(
    FEEDBACK_LAYOUT,
    ("Screenshot", "feedback_attachment", "feedback_screenshot", "@drawable/ic_image"),
    "screenshot UI",
)

require_tokens(
    FIRESTORE_RULES,
    (
        "isAuthenticatedOwner(data.userId)",
        "data.message.size() <= 500",
        "data.email.size() <= 254",
        "allow get, delete:",
        "allow list:",
        "allow update: if false",
    ),
    "secure feedback rule",
)
forbid_tokens(
    FIRESTORE_RULES,
    ("data.userId == 'anonymous'", "request.auth == null", "screenshot", "mediaTransaction"),
    "unsafe or obsolete rule",
)

require_tokens(
    FIREBASE_RULES_TEST,
    (
        "anonymous-feedback",
        "invalid-email",
        "oversized-message",
        "crossOwnerQuery",
        "deleteDoc(ownerRef)",
    ),
    "rules regression",
)

require_tokens(
    PROCESSOR,
    (
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
    ),
    "bounded generic image contract",
)
require_tokens(
    IMAGE_POLICY,
    (
        "object ImageMediaPolicy",
        "MAX_SOURCE_BYTES",
        "MAX_SOURCE_PIXELS",
        "MAX_OUTPUT_BYTES",
        "MAX_OUTPUT_EDGE_PX",
        "MAX_OUTPUT_PIXELS",
    ),
    "image policy",
)

for path in (APP_CONTAINER, RELEASE_SMOKE_CONTAINER):
    require_tokens(path, ("imageMediaProcessor: ImageMediaProcessor",), "generic media composition")

require_tokens(
    COORDINATOR,
    (
        "ImageMediaProcessor",
        "ImageMediaProcessingResult",
        "ImageMediaFailureKind",
        "prepareCropIntent",
        "preparationMutex.withLock",
        "commitSelection",
        "rollbackSelection",
        "setMaxBitmapSize",
    ),
    "lifecycle-safe image flow",
)
require_tokens(FILE_PROVIDER_PATHS, ('name="image_processing"',), "image provider path")

photo_consumers = (
    APP / "ui/tabs/settings/profile/EditProfileFragment.kt",
    APP / "ui/tabs/aquarium/create/steps/TankPhotoFragment.kt",
    APP / "ui/tabs/aquarium/detail/settings/TankSettingsBasicFragment.kt",
)
for path in photo_consumers:
    require_tokens(
        path,
        (
            "MediaFlowCoordinatorViewModel",
            "container.imageMediaProcessor",
            "prepareCropIntent",
            "authenticatedOwnerIdentity.requireOwnerUid()",
            "CancellationException",
            "throw cancellation",
        ),
        "shared image consumer contract",
    )
    forbid_tokens(
        path,
        ("FileProvider", "File.createTempFile", "UCrop.Options()", "feedbackMediaProcessor"),
        "duplicated or legacy media ownership",
    )

require_tokens(
    APP_MEDIA_STORAGE,
    (
        "app_media_pending_v1",
        "app_media_deletion_v1",
        "commitPendingMedia",
        "rollbackPendingMedia",
        "reconcilePendingMedia",
        "reconcilePendingDeletions",
        "discardPendingMediaForOwner",
    ),
    "durable owner media contract",
)

# Scan only production source roots. The guard and regression tests intentionally contain forbidden
# token literals so that their absence can be asserted without creating a self-match.
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
for source_root in (ROOT / "app/src/main", ROOT / "app/src/releaseSmoke"):
    for path in source_root.rglob("*"):
        if not path.is_file() or path.suffix not in {".kt", ".xml"}:
            continue
        forbid_tokens(path, LEGACY_TOKENS, "legacy media compatibility token")

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
    MEDIA_COORDINATOR_TEST: (
        "boundedPreparedSourceAndPendingCropAreCleanedAfterCancel",
        "previousPersistedMediaIsDeletedOnlyAfterReplacementCommit",
        "ownerReconciliationPreservesReferencedMediaAndExpiresOnlyOrphan",
    ),
}
for path, tokens in TEST_EXPECTATIONS.items():
    require_tokens(path, tokens, "required regression test")

forbid_tokens(ROOT / "app/build.gradle", ("firebase-storage",), "removed Storage dependency")
forbid_tokens(ROOT / "firebase.json", ('"storage"',), "removed Storage configuration")

if errors:
    print("Stage 9 commercial feedback/media architecture guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print(
    "Stage 9 commercial guard passed: feedback is authenticated and text-only; profile/tank "
    "image processing is generic, bounded, owner-aware and free of compatibility shims."
)
