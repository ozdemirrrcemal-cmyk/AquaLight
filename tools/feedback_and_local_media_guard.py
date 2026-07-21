#!/usr/bin/env python3
"""Validate the authenticated feedback and owner-scoped local-media boundaries."""

import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"

FEEDBACK_CONTRACT = APP / "application/feedback/FeedbackSubmission.kt"
FEEDBACK_REPOSITORY = APP / "data/feedback/FirebaseFeedbackRepository.kt"
FEEDBACK_VIEW_MODEL = APP / "ui/tabs/settings/feedback/FeedbackViewModel.kt"
FEEDBACK_FRAGMENT = APP / "ui/tabs/settings/feedback/FeedbackFragment.kt"
FEEDBACK_LAYOUT = ROOT / "app/src/main/res/layout/fragment_feedback.xml"
APP_CONTAINER = APP / "composition/AppContainer.kt"
AQUA_APP = APP / "app/AquaApp.kt"
OWNER_SESSION = APP / "data/auth/OwnerSessionCoordinator.kt"
USER_DATA_CLEANER = APP / "data/user/UserDataCleaner.kt"
FIREBASE_WORKFLOW = ROOT / ".github/workflows/firebase_rules.yml"

IMAGE_PROCESSOR = APP / "platform/media/BoundedImageProcessor.kt"
MEDIA_STORAGE = APP / "platform/media/AppMediaStorage.kt"
MEDIA_COORDINATOR = APP / "ui/common/media/MediaFlowCoordinatorViewModel.kt"
MEDIA_RECOVERY = APP / "data/media/AppMediaRecoveryManager.kt"

REQUIRED = (
    FEEDBACK_CONTRACT,
    FEEDBACK_REPOSITORY,
    FEEDBACK_VIEW_MODEL,
    FEEDBACK_FRAGMENT,
    FEEDBACK_LAYOUT,
    APP_CONTAINER,
    AQUA_APP,
    OWNER_SESSION,
    USER_DATA_CLEANER,
    FIREBASE_WORKFLOW,
    IMAGE_PROCESSOR,
    MEDIA_STORAGE,
    MEDIA_COORDINATOR,
    MEDIA_RECOVERY,
    APP / "platform/media/BoundedImagePolicy.kt",
    ROOT / "firestore.rules",
    ROOT / "firebase.json",
    ROOT / "firebase/rules.test.mjs",
    ROOT / "docs/feedback-and-local-media-contract.md",
    ROOT / "docs/firebase-feedback-production-activation.md",
    ROOT
    / "app/src/test/java/com/aqua/aqualight/application/feedback/"
    / "FeedbackSubmissionUseCaseTest.kt",
    ROOT
    / "app/src/test/java/com/aqua/aqualight/data/feedback/"
    / "FirebaseFeedbackRepositoryTest.kt",
    ROOT
    / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/feedback/"
    / "FeedbackViewModelTest.kt",
    ROOT
    / "app/src/test/java/com/aqua/aqualight/ui/common/validation/"
    / "EmailAddressPolicyTest.kt",
    ROOT
    / "app/src/test/java/com/aqua/aqualight/platform/media/"
    / "LocalMediaArchitectureTest.kt",
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/platform/media/"
    / "BoundedImageProcessorInstrumentedTest.kt",
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/platform/media/"
    / "AppMediaStorageInstrumentedTest.kt",
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/ui/common/media/"
    / "MediaFlowCoordinatorInstrumentedTest.kt",
)

errors: list[str] = []


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore") if path.is_file() else ""


def require(path: Path, token: str, reason: str) -> None:
    if token not in read(path):
        errors.append(f"{path.relative_to(ROOT)}: {reason}: {token}")


def forbid(path: Path, token: str, reason: str) -> None:
    if token.lower() in read(path).lower():
        errors.append(f"{path.relative_to(ROOT)}: {reason}: {token}")


for path in REQUIRED:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required file is missing")

for token in (
    "interface FeedbackRepository",
    "class FeedbackSubmissionUseCase",
    "data class FeedbackSubmissionRequest",
    "AUTHENTICATION",
    "PERSISTENCE",
    "GENERIC",
):
    require(FEEDBACK_CONTRACT, token, "feedback application contract is incomplete")

for token in ("android.", "com.google.firebase", "java.io.File"):
    forbid(FEEDBACK_CONTRACT, token, "application contract must remain platform-independent")

for token in (
    "class FirebaseFeedbackRepository",
    "FirebaseAuth.getInstance().currentUser?.uid",
    "FeedbackSubmissionFailureKind.AUTHENTICATION",
    "return@withContext",
    "documentStore.save",
    "FieldValue.serverTimestamp()",
    "CancellationException",
):
    require(FEEDBACK_REPOSITORY, token, "authenticated Firebase repository contract is incomplete")
for token in ("anonymous", "android.net.Uri", "java.io.File"):
    forbid(FEEDBACK_REPOSITORY, token, "feedback repository has an unsupported dependency or fallback")

allowed_firebase_imports = (
    "import com.google.firebase.auth.",
    "import com.google.firebase.firestore.",
)
for line in read(FEEDBACK_REPOSITORY).splitlines():
    if line.startswith("import com.google.firebase.") and not line.startswith(allowed_firebase_imports):
        errors.append(
            f"{FEEDBACK_REPOSITORY.relative_to(ROOT)}: unsupported Firebase import: {line}"
        )

for token in (
    "EmailAddressPolicy.isValid",
    "MIN_MESSAGE_LENGTH = 10",
    "MAX_MESSAGE_LENGTH = 500",
    "FeedbackSubmissionRequest(",
    "isSubmitting = true",
):
    require(FEEDBACK_VIEW_MODEL, token, "feedback ViewModel contract is incomplete")
for token in ("android.util.Patterns", "android.net.Uri", "android.graphics", "platform.media"):
    forbid(FEEDBACK_VIEW_MODEL, token, "feedback ViewModel must remain platform/media independent")

for token in ("container.feedbackSubmissionUseCase", "FeedbackViewModel.factory"):
    require(FEEDBACK_FRAGMENT, token, "feedback UI boundary is incomplete")
for token in ("com.google.firebase", "android.graphics", "java.io", "platform.media"):
    forbid(FEEDBACK_FRAGMENT, token, "feedback Fragment must only render state and intent")

for token in (
    "val feedbackSubmissionUseCase: FeedbackSubmissionUseCase",
    "FirebaseFeedbackRepository.create()",
    "val boundedImageProcessor: BoundedImageProcessor",
    "AndroidBoundedImageProcessor(appContext)",
):
    require(APP_CONTAINER, token, "composition binding is incomplete")

for token in ('android:maxLength="320"', 'android:maxLength="500"'):
    require(FEEDBACK_LAYOUT, token, "feedback input limits must match the server contract")

build_gradle = ROOT / "app/build.gradle"
allowed_firebase_artifacts = {
    "firebase-analytics",
    "firebase-auth",
    "firebase-firestore",
    "firebase-database",
    "firebase-messaging",
    "firebase-config",
    "firebase-perf",
    "firebase-crashlytics",
}
for artifact in re.findall(r'com\.google\.firebase:(firebase-[a-z-]+)', read(build_gradle)):
    if artifact != "firebase-bom" and artifact not in allowed_firebase_artifacts:
        errors.append(f"app/build.gradle: unsupported Firebase artifact: {artifact}")

firebase_config = ROOT / "firebase.json"
if firebase_config.is_file():
    try:
        config = json.loads(read(firebase_config))
        if "storage" in config:
            errors.append("firebase.json: unsupported Firebase Storage configuration is present")
        if config.get("firestore", {}).get("rules") != "firestore.rules":
            errors.append("firebase.json: Firestore rules binding is missing")
    except json.JSONDecodeError as error:
        errors.append(f"firebase.json: invalid JSON: {error}")
if (ROOT / "storage.rules").exists():
    errors.append("storage.rules: unsupported rules file is present")

require(FIREBASE_WORKFLOW, "--only firestore", "rules workflow must run only Firestore")
forbid(FIREBASE_WORKFLOW, "storage", "rules workflow must not configure an unused service")

firestore_rules = ROOT / "firestore.rules"
for token in (
    "request.auth != null && request.auth.uid == userId",
    "data.keys().hasOnly",
    "data.message.size() >= 10",
    "data.message.size() <= 500",
    "data.createdAt == request.time",
    "allow update: if false",
):
    require(firestore_rules, token, "Firestore feedback policy is incomplete")
forbid(firestore_rules, "anonymous", "unauthenticated feedback fallback is forbidden")

rules_test = ROOT / "firebase/rules.test.mjs"
for token in (
    "authenticatedContext(OWNER)",
    "unauthenticatedContext()",
    "unexpectedField: true",
    "'x'.repeat(501)",
    "assertFails",
):
    require(rules_test, token, "Firestore policy regression coverage is incomplete")
firebase_test_imports = set(re.findall(r"from '([^']+)'", read(rules_test)))
if {module for module in firebase_test_imports if module.startswith("firebase/")} != {
    "firebase/firestore"
}:
    errors.append("firebase/rules.test.mjs: rules test must import only the Firestore client")

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
    require(IMAGE_PROCESSOR, token, "bounded image processing contract is incomplete")

for token in (
    "app_media_pending_journal",
    "app_media_deletion_journal",
    "JSON_OWNER_UID",
    "commitPendingMedia",
    "rollbackPendingMedia",
    "deleteAfterCommit",
    "reconcilePendingMedia",
    "reconcilePendingDeletions",
):
    require(MEDIA_STORAGE, token, "owner-scoped local-media transaction contract is incomplete")
if read(MEDIA_STORAGE).count("fun copyInternalMedia(") != 1:
    errors.append("app/src/main/java/com/aqua/aqualight/platform/media/AppMediaStorage.kt: "
                  "copyInternalMedia must have one explicit-owner entry point")
if re.search(r"_v\d+\b", read(MEDIA_STORAGE), re.IGNORECASE):
    errors.append(
        "app/src/main/java/com/aqua/aqualight/platform/media/AppMediaStorage.kt: "
        "versioned storage path is forbidden"
    )
forbid(MEDIA_STORAGE, "Compatibility entry", "compatibility storage path is forbidden")

for token in (
    "SavedStateHandle",
    "BoundedImageProcessor",
    "ownerUid",
    "prepareCropIntent",
    "preparationMutex.withLock",
    "commitSelection",
    "rollbackSelection",
):
    require(MEDIA_COORDINATOR, token, "media lifecycle contract is incomplete")

for token in (
    "reconcilePendingMedia",
    "reconcilePendingDeletions",
    "reconcileUnreferencedCommittedMedia",
):
    require(MEDIA_RECOVERY, token, "media recovery contract is incomplete")

require(
    AQUA_APP,
    "AppMediaRecoveryManager(this@AquaApp).reconcileActiveOwner()",
    "process startup must reconcile the active owner's current media transactions",
)
require(
    OWNER_SESSION,
    "AppMediaRecoveryManager(appContext).reconcileOwner(normalizedOwnerUid)",
    "owner-session startup must reconcile current media transactions",
)
require(
    USER_DATA_CLEANER,
    "AppMediaStorage.discardPendingMediaForOwner(appContext, ownerUid)",
    "account cleanup must discard only the departing owner's pending media",
)

photo_consumers = (
    APP / "ui/tabs/settings/profile/EditProfileFragment.kt",
    APP / "ui/tabs/aquarium/create/steps/TankPhotoFragment.kt",
    APP / "ui/tabs/aquarium/detail/settings/TankSettingsBasicFragment.kt",
)
for path in photo_consumers:
    for token in (
        "MediaFlowCoordinatorViewModel",
        "prepareCropIntent",
        "authenticatedOwnerIdentity.requireOwnerUid()",
    ):
        require(path, token, "photo flow must use the shared owner-scoped media boundary")
    for token in ("FileProvider", "File.createTempFile", "AndroidBoundedImageProcessor("):
        forbid(path, token, "feature UI must not construct local-media infrastructure")

if errors:
    print("Feedback/local-media architecture guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print("Feedback/local-media architecture guard passed.")
