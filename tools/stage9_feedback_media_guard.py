#!/usr/bin/env python3
"""Fail CI when feedback or shared local-media architecture regresses."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"

FEEDBACK_FRAGMENT = APP / "ui/tabs/settings/feedback/FeedbackFragment.kt"
FEEDBACK_VIEW_MODEL = APP / "ui/tabs/settings/feedback/FeedbackViewModel.kt"
FEEDBACK_BOUNDARY = APP / "application/feedback/FeedbackSubmissionOperations.kt"
FEEDBACK_REPOSITORY = APP / "data/feedback/FirebaseFeedbackSubmissionOperations.kt"
APP_CONTAINER = APP / "composition/AppContainer.kt"
AQUA_APP = APP / "app/AquaApp.kt"
PROCESSOR = APP / "platform/media/FeedbackMediaProcessor.kt"
APP_MEDIA_STORAGE = APP / "platform/media/AppMediaStorage.kt"
COORDINATOR = APP / "ui/common/media/MediaFlowCoordinatorViewModel.kt"
MEDIA_RECOVERY = APP / "data/media/AppMediaRecoveryManager.kt"
OWNER_SESSION = APP / "data/auth/OwnerSessionCoordinator.kt"
AQUARIUM_STORE = APP / "data/aquarium/store/AquariumTankDataStoreManager.kt"
CREATE_TANK_VIEW_MODEL = APP / "ui/tabs/aquarium/create/CreateTankViewModel.kt"

REQUIRED = (
    FEEDBACK_FRAGMENT,
    FEEDBACK_VIEW_MODEL,
    FEEDBACK_BOUNDARY,
    FEEDBACK_REPOSITORY,
    APP_CONTAINER,
    PROCESSOR,
    APP_MEDIA_STORAGE,
    COORDINATOR,
    MEDIA_RECOVERY,
    CREATE_TANK_VIEW_MODEL,
    ROOT / "app/src/test/java/com/aqua/aqualight/data/feedback/FirebaseFeedbackSubmissionOperationsTest.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/feedback/FeedbackViewModelTest.kt",
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/platform/media/FeedbackMediaProcessorInstrumentedTest.kt",
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/platform/media/AppMediaStorageInstrumentedTest.kt",
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/ui/common/media/MediaFlowCoordinatorInstrumentedTest.kt",
)

OBSOLETE = (
    APP / "data/feedback/FeedbackSubmissionJournalStore.kt",
    APP / "data/feedback/FeedbackOrphanStore.kt",
    APP / "ui/tabs/aquarium/photo/TankPhotoFlowCoordinator.kt",
    APP / "data/aquarium/photo/TankPhotoStorage.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/data/feedback/FirebaseFeedbackJournalFailureTest.kt",
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/data/feedback/FeedbackSubmissionJournalStoreInstrumentedTest.kt",
)

errors: list[str] = []

for path in REQUIRED:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required file is missing")

for path in OBSOLETE:
    if path.exists():
        errors.append(f"{path.relative_to(ROOT)}: obsolete feedback-media implementation must be removed")


def require_tokens(path: Path, tokens: tuple[str, ...], label: str) -> None:
    if not path.is_file():
        return
    text = path.read_text(encoding="utf-8", errors="ignore")
    for token in tokens:
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: {label} missing: {token}")


def forbid_tokens(path: Path, tokens: tuple[str, ...], label: str) -> None:
    if not path.is_file():
        return
    text = path.read_text(encoding="utf-8", errors="ignore")
    for token in tokens:
        if token in text:
            errors.append(f"{path.relative_to(ROOT)}: {label} forbidden: {token}")


require_tokens(
    FEEDBACK_FRAGMENT,
    ("FeedbackViewModel", "container.feedbackSubmissionOperations", "viewModel.submit()"),
    "text-feedback UI boundary",
)
forbid_tokens(
    FEEDBACK_FRAGMENT,
    (
        "ActivityResultContracts",
        "selectScreenshot",
        "ScreenshotSelected",
        "FeedbackMediaFailureKind",
        "feedbackMediaProcessor",
        "FirebaseStorage",
    ),
    "removed screenshot flow",
)

require_tokens(
    FEEDBACK_VIEW_MODEL,
    ("SavedStateHandle", "FeedbackSubmissionRequest", "isSubmitting", "SubmissionSucceeded"),
    "process-safe text-feedback state",
)
forbid_tokens(
    FEEDBACK_VIEW_MODEL,
    (
        "android.net.Uri",
        "ProcessedFeedbackMedia",
        "FeedbackMediaProcessor",
        "screenshotFile",
        "cleanupOrphans",
    ),
    "removed screenshot state",
)

require_tokens(
    FEEDBACK_REPOSITORY,
    (
        "FirebaseFirestore",
        "FieldValue.serverTimestamp()",
        "withContext(dispatcher)",
        "suspendCancellableCoroutine",
        "withTimeout(writeTimeoutMillis)",
        "FeedbackSubmissionFailureKind.PERSISTENCE",
    ),
    "text-only Firestore repository",
)
forbid_tokens(
    FEEDBACK_REPOSITORY,
    (
        "FirebaseStorage",
        "feedback_screenshots",
        "screenshotStore",
        "mediaTransaction",
        "FeedbackSubmissionJournalStore",
        "PendingFeedbackUpload",
    ),
    "cloud media dependency",
)

forbid_tokens(
    AQUA_APP,
    ("feedbackSubmissionOperations.cleanupOrphans()",),
    "obsolete process-start feedback recovery",
)

require_tokens(
    APP_CONTAINER,
    (
        "val feedbackMediaProcessor: FeedbackMediaProcessor",
        "AndroidFeedbackMediaProcessor(appContext)",
        "FirebaseFeedbackSubmissionOperations.create()",
    ),
    "composition binding",
)

PHOTO_CONSUMERS = (
    APP / "ui/tabs/settings/profile/EditProfileFragment.kt",
    APP / "ui/tabs/aquarium/create/steps/TankPhotoFragment.kt",
    APP / "ui/tabs/aquarium/detail/settings/TankSettingsBasicFragment.kt",
)
for path in PHOTO_CONSUMERS:
    require_tokens(
        path,
        (
            "MediaFlowCoordinatorViewModel",
            "prepareCropIntent",
            "authenticatedOwnerIdentity.requireOwnerUid()",
            "CancellationException",
            "throw cancellation",
        ),
        "shared bounded/cancellation-safe local media contract",
    )
    forbid_tokens(
        path,
        ("FileProvider", "File.createTempFile", "UCrop.Options()"),
        "duplicated media ownership",
    )

require_tokens(
    PROCESSOR,
    (
        "Dispatchers.IO",
        "MAX_SOURCE_BYTES",
        "currentCoroutineContext().ensureActive()",
        "inJustDecodeBounds",
        "FileInputStream",
        ".use {",
        "CancellationException",
        "OutOfMemoryError",
        "MAX_OUTPUT_BYTES",
    ),
    "bounded/cancellable local media processing",
)

require_tokens(
    APP_MEDIA_STORAGE,
    (
        "JSON_OWNER_UID",
        ".commit()",
        "commitPendingMedia",
        "rollbackPendingMedia",
        "reconcilePendingMedia",
        "discardPendingMediaForOwner",
        "sourceFile.copyTo",
    ),
    "owner-aware local media ownership",
)

require_tokens(
    COORDINATOR,
    (
        "SavedStateHandle",
        "FeedbackMediaProcessor",
        "ownerUid",
        "prepareCropIntent",
        "preparationMutex.withLock",
        "commitSelection",
        "rollbackSelection",
        "setMaxBitmapSize",
    ),
    "shared lifecycle-safe local media coordinator",
)

require_tokens(
    AQUARIUM_STORE,
    ("AppMediaStorage", "AppMediaScope.TANK"),
    "aquarium local media persistence",
)

require_tokens(
    OWNER_SESSION,
    ("AppMediaRecoveryManager(appContext).reconcileOwner(normalizedOwnerUid)",),
    "owner-session local media recovery barrier",
)

require_tokens(
    CREATE_TANK_VIEW_MODEL,
    ("SavedStateHandle", "KEY_DRAFT_JSON", "restoreDraft", "completeTank"),
    "process-death tank draft contract",
)

TEST_EXPECTATIONS = {
    ROOT / "app/src/test/java/com/aqua/aqualight/data/feedback/FirebaseFeedbackSubmissionOperationsTest.kt": (
        "authenticated text feedback is stored without media fields",
        "missing session uses anonymous marker",
        "firestore failure is reported as persistence failure",
        "stalled firestore write reaches a terminal timeout failure",
        "cancellation is never converted to feedback failure",
    ),
    ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/feedback/FeedbackViewModelTest.kt": (
        "restored form submits text feedback",
        "synchronous busy lock prevents duplicate submissions",
        "view model recreation never replays interrupted submission",
        "submission failure keeps the form for retry",
    ),
}
for path, tokens in TEST_EXPECTATIONS.items():
    require_tokens(path, tokens, "required regression test")

if errors:
    print("Feedback and local-media architecture guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Feedback and local-media guard passed.")
