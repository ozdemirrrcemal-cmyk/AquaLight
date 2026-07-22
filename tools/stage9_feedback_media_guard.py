#!/usr/bin/env python3
"""Commercial architecture guard for Spark-plan text feedback and shared image media."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"

FEEDBACK_APPLICATION = APP / "application/feedback/FeedbackSubmissionOperations.kt"
FEEDBACK_REPOSITORY = APP / "data/feedback/FirebaseFeedbackSubmissionOperations.kt"
FEEDBACK_VIEW_MODEL = APP / "ui/tabs/settings/feedback/FeedbackViewModel.kt"
FEEDBACK_FRAGMENT = APP / "ui/tabs/settings/feedback/FeedbackFragment.kt"
FEEDBACK_LAYOUT = ROOT / "app/src/main/res/layout/fragment_feedback.xml"
CLOUD_CLEANER = APP / "data/user/CloudUserDataCleaner.kt"
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
    CLOUD_CLEANER,
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
    ROOT / "functions",
)

errors: list[str] = []


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore") if path.is_file() else ""


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


for path in REQUIRED:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required commercial file is missing")

for path in OBSOLETE_PATHS:
    if path.exists():
        errors.append(f"{path.relative_to(ROOT)}: obsolete or paid-plan implementation remains")

require_tokens(
    FEEDBACK_APPLICATION,
    (
        "val submissionId: String",
        "SUBMISSION_ID_LENGTH = 36",
        "EMAIL_MAX_LENGTH = 254",
        "EMAIL_LOCAL_PART_MAX_LENGTH = 64",
        "MESSAGE_MIN_LENGTH = 10",
        "MESSAGE_MAX_LENGTH = 500",
        "NETWORK,",
        "FeedbackSubmissionFailureKind.VALIDATION",
    ),
    "feedback policy",
)
forbid_tokens(
    FEEDBACK_APPLICATION,
    ("RATE_LIMITED", "java.io.File", "screenshot", "cleanupOrphans"),
    "unsupported or obsolete feedback boundary",
)

require_tokens(
    FEEDBACK_REPOSITORY,
    (
        "FeedbackSubmissionFailureKind.AUTHENTICATION",
        "firestore.runTransaction",
        "withTimeout(timeoutMillis)",
        "SUBMISSION_TIMEOUT_MILLIS = 15_000L",
        "CompletableDeferred",
        "collection(SUBMISSIONS_COLLECTION)",
        "FeedbackDocumentStoreFailureKind.NETWORK",
        "CancellationException",
    ),
    "Spark transaction persistence contract",
)
forbid_tokens(
    FEEDBACK_REPOSITORY,
    (
        "FirebaseFunctions",
        "HttpsCallableOptions",
        "FirebaseAppCheck",
        "PlayIntegrityAppCheckProviderFactory",
        "FirebaseStorage",
        "anonymous",
        "screenshot",
        "journalStore",
        "cleanupOrphans",
        ".document(documentId).set",
        "suspendCoroutine",
    ),
    "paid, offline-capable or obsolete feedback token",
)

require_tokens(
    FEEDBACK_VIEW_MODEL,
    (
        "FeedbackSubmissionPolicy.isEmailValid",
        "FeedbackSubmissionPolicy.MESSAGE_MIN_LENGTH",
        "FeedbackSubmissionPolicy.MESSAGE_MAX_LENGTH",
        "KEY_SUBMISSION_ID",
        "submissionIdentity()",
        "invalidateSubmissionIdentity()",
        "submissionId = submissionIdentity()",
        "isSubmitting = false",
    ),
    "feedback state and idempotency contract",
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
        "FeedbackSubmissionFailureKind.NETWORK",
        "feedback_error_network",
        "feedback_error_message_length",
        "setFragmentGlobalLoading(state.isSubmitting)",
    ),
    "commercial feedback error handling",
)
forbid_tokens(
    FEEDBACK_FRAGMENT,
    (
        "FeedbackSubmissionFailureKind.RATE_LIMITED",
        "FirebaseStorage",
        "feedbackMediaProcessor",
        "selectScreenshot",
        "ScreenshotSelected",
    ),
    "paid or screenshot UI dependency",
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
    CLOUD_CLEANER,
    (
        'ROOT_COLLECTION = "feedback_items"',
        'SUBMISSIONS_COLLECTION = "submissions"',
        "get(Source.SERVER)",
        "runTransaction",
        "transaction.delete",
        "CancellationException",
    ),
    "Spark account cleanup contract",
)
forbid_tokens(
    CLOUD_CLEANER,
    ("FirebaseFunctions", "HttpsCallableOptions", "deleteUserCloudData"),
    "paid account cleanup dependency",
)

require_tokens(
    FIRESTORE_RULES,
    (
        "match /feedback_items/{ownerUid}",
        "match /submissions/{submissionId}",
        "data.submissionId == submissionId",
        "data.userId == ownerUid",
        "data.message.size() <= 500",
        "data.email.size() <= 254",
        "data.email.split('@')[0].size() <= 64",
        "allow get, list, delete:",
        "allow update: if false",
    ),
    "secure Spark feedback rule",
)
forbid_tokens(
    FIRESTORE_RULES,
    (
        "feedback_rate_limits",
        "data.userId == 'anonymous'",
        "request.auth == null",
        "screenshot",
        "mediaTransaction",
    ),
    "unsafe, paid or obsolete rule",
)

require_tokens(
    FIREBASE_RULES_TEST,
    (
        "Spark-compatible feedback Firestore rules tests passed.",
        "anonymousDb",
        "invalid-email",
        "user..name@example.com",
        "repeat(65)",
        "repeat(501)",
        "crossOwnerSubmissions",
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
# token literals so their absence can be asserted without creating a self-match.
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
        "successfulSubmissionUsesOwnerScopedTransactionStore",
        "transactionNetworkFailureReturnsTypedNetworkFailure",
        "nonTerminatingTransactionTimesOutAsNetworkFailure",
        "lifecycleCancellationIsPropagated",
    ),
    FEEDBACK_USE_CASE_TEST: (
        "invalidSubmissionIdentityIsRejectedBeforeRepositoryCall",
        "commercialEmailPolicyAcceptsPlusAddressAndRejectsUnsafeForms",
    ),
    FEEDBACK_VIEW_MODEL_TEST: (
        "networkFailureStopsLoadingKeepsFormAndReusesSubmissionIdentity",
        "editingAfterFailureCreatesANewSubmissionIdentity",
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

forbid_tokens(
    ROOT / "app/build.gradle",
    (
        "firebase-storage",
        "firebase-functions",
        "firebase-appcheck-playintegrity",
        "firebase-appcheck-debug",
    ),
    "paid or removed Firebase dependency",
)
forbid_tokens(
    ROOT / "firebase.json",
    ('"storage"', '"functions"'),
    "paid or removed Firebase configuration",
)

if errors:
    print("Stage 9 commercial feedback/media architecture guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print(
    "Stage 9 commercial guard passed: feedback is authenticated, text-only, Spark-plan compatible, "
    "transactional and idempotent; profile/tank image processing remains generic and bounded."
)
