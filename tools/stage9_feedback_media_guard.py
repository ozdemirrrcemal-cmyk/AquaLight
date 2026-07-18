#!/usr/bin/env python3
"""Fail CI when Stage 9 feedback/media architecture regresses."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
FEEDBACK_FRAGMENT = APP / "ui/tabs/settings/feedback/FeedbackFragment.kt"
AQUARIUM_STORE = APP / "data/aquarium/store/AquariumTankDataStoreManager.kt"

REQUIRED = (
    APP / "application/feedback/FeedbackSubmissionOperations.kt",
    APP / "data/feedback/FirebaseFeedbackSubmissionOperations.kt",
    APP / "data/feedback/FeedbackOrphanStore.kt",
    APP / "platform/media/FeedbackImagePolicy.kt",
    APP / "platform/media/FeedbackMediaProcessor.kt",
    APP / "platform/media/AppMediaStorage.kt",
    APP / "ui/common/media/MediaFlowCoordinatorViewModel.kt",
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
)

errors: list[str] = []

for path in REQUIRED:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required Stage 9 file is missing")

for path in OBSOLETE:
    if path.exists():
        errors.append(f"{path.relative_to(ROOT)}: obsolete media implementation must stay removed")

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

repository = APP / "data/feedback/FirebaseFeedbackSubmissionOperations.kt"
if repository.is_file():
    text = repository.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "suspend fun submit",
        "screenshotStore.delete",
        "cleanupOrphans",
        "orphanStore.add",
    ):
        if token not in text:
            errors.append(
                f"{repository.relative_to(ROOT)}: required rollback/orphan contract missing: {token}"
            )

processor = APP / "platform/media/FeedbackMediaProcessor.kt"
if processor.is_file():
    text = processor.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "Dispatchers.IO",
        "inJustDecodeBounds",
        ".use {",
        "OutOfMemoryError",
        "MAX_OUTPUT_BYTES",
    ):
        if token not in text:
            errors.append(
                f"{processor.relative_to(ROOT)}: required bounded processing missing: {token}"
            )

coordinator = APP / "ui/common/media/MediaFlowCoordinatorViewModel.kt"
if coordinator.is_file():
    text = coordinator.read_text(encoding="utf-8", errors="ignore")
    for token in ("SavedStateHandle", "commitSelection", "rollbackSelection"):
        if token not in text:
            errors.append(
                f"{coordinator.relative_to(ROOT)}: required lifecycle contract missing: {token}"
            )

if errors:
    print("Stage 9 feedback/media architecture guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Stage 9 feedback/media guard passed.")
