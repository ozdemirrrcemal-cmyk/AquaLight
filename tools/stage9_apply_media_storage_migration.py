#!/usr/bin/env python3
"""One-shot source migration from TankPhotoStorage to AppMediaStorage."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/aqua/aqualight/data/aquarium/store/AquariumTankDataStoreManager.kt"
LEGACY = ROOT / "app/src/main/java/com/aqua/aqualight/data/aquarium/photo/TankPhotoStorage.kt"

text = TARGET.read_text(encoding="utf-8")

replacements = (
    (
        "import com.aqua.aqualight.data.aquarium.photo.TankPhotoStorage\n",
        "import com.aqua.aqualight.platform.media.AppMediaScope\n"
        "import com.aqua.aqualight.platform.media.AppMediaStorage\n",
    ),
    (
        """            val duplicatedPhotoUri = TankPhotoStorage.copyInternalPhotoForTank(
                context = context,
                sourceUriString = sourceTank.photoUri,
                tankId = newTankId
            )
""",
        """            val duplicatedPhotoUri = AppMediaStorage.copyInternalMedia(
                context = context,
                sourceUriString = sourceTank.photoUri,
                targetScope = AppMediaScope.TANK,
                ownerToken = newTankId.toString()
            )
""",
    ),
    (
        """        TankPhotoStorage.deleteInternalPhotos(
            context = context,
            uriStrings = photoUrisToDelete
        )
        deletedTankIds.forEach { deletedTankId ->
            TankPhotoStorage.deleteTankOwnedTemporaryFiles(
                context = context,
                tankId = deletedTankId
            )
        }
""",
        """        AppMediaStorage.deleteInternalMedia(
            context = context,
            uriStrings = photoUrisToDelete
        )
        deletedTankIds.forEach { deletedTankId ->
            AppMediaStorage.deleteOwnerTemporaryFiles(
                context = context,
                scope = AppMediaScope.TANK,
                ownerToken = deletedTankId.toString()
            )
        }
""",
    ),
    (
        """        TankPhotoStorage.deleteInternalPhotos(
            context = context,
            uriStrings = deletedPhotoUris
        )
        deletedTankIds.forEach { deletedTankId ->
            TankPhotoStorage.deleteTankOwnedTemporaryFiles(
                context = context,
                tankId = deletedTankId
            )
        }
""",
        """        AppMediaStorage.deleteInternalMedia(
            context = context,
            uriStrings = deletedPhotoUris
        )
        deletedTankIds.forEach { deletedTankId ->
            AppMediaStorage.deleteOwnerTemporaryFiles(
                context = context,
                scope = AppMediaScope.TANK,
                ownerToken = deletedTankId.toString()
            )
        }
""",
    ),
    (
        """        TankPhotoStorage.deleteInternalPhoto(
            context = context,
            uriString = previousPhotoUri
        )
""",
        """        AppMediaStorage.deleteInternalMedia(
            context = context,
            uriString = previousPhotoUri
        )
""",
    ),
)

for old, new in replacements:
    if old not in text:
        raise SystemExit(f"Expected migration source was not found:\n{old}")
    text = text.replace(old, new, 1)

if "TankPhotoStorage" in text:
    raise SystemExit("TankPhotoStorage reference remained after migration")

TARGET.write_text(text, encoding="utf-8")

if LEGACY.exists():
    LEGACY.unlink()

print("Stage 9 aquarium media storage migration applied.")
