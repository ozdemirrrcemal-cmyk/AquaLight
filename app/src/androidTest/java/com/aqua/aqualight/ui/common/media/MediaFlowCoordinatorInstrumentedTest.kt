package com.aqua.aqualight.ui.common.media

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaFlowCoordinatorInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun pendingCameraUriSurvivesCoordinatorRecreation() {
        val savedState = SavedStateHandle()
        val first = coordinator(savedState)
        val cameraUri = first.createCameraUri()
        assertNotNull(cameraUri)
        assertTrue(AppMediaStorage.isAppOwned(context, cameraUri.toString()))

        val recreated = coordinator(savedState)

        assertEquals(cameraUri, recreated.currentCameraUri())
        recreated.cancelCamera()
        assertFalse(AppMediaStorage.isAppOwned(context, cameraUri.toString()))
    }

    @Test
    fun pendingCameraAndCropFilesAreCleanedAfterCoordinatorRecreationAndCancel() {
        AppMediaStorage.deleteOwnerTemporaryFiles(context, AppMediaScope.TANK, OWNER_TOKEN)
        val savedState = SavedStateHandle()
        val first = coordinator(savedState)
        val cameraUri = requireNotNull(first.createCameraUri())
        assertNotNull(first.buildCropIntent(cameraUri, "Crop"))
        assertTrue(tempFiles().any { it.name.contains("_camera_") })
        assertTrue(tempFiles().any { it.name.contains("_crop_") })

        val recreated = coordinator(savedState)
        recreated.cancelCrop()
        recreated.cancelCamera()

        assertFalse(AppMediaStorage.isAppOwned(context, cameraUri.toString()))
        assertFalse(tempFiles().any { it.name.contains("_camera_") || it.name.contains("_crop_") })
    }

    @Test
    fun acceptedCropIsPromotedAndRollbackDeletesPendingSelection() {
        val coordinator = coordinator(SavedStateHandle())
        coordinator.initializeSelection(null)
        val promoted = coordinator.acceptCrop(createCropOutput())

        assertNotNull(promoted)
        assertTrue(AppMediaStorage.isAppOwned(context, promoted.toString()))
        assertTrue(coordinator.selection.value.hasPendingChange)
        coordinator.rollbackSelection()
        assertFalse(AppMediaStorage.isAppOwned(context, promoted.toString()))
    }

    @Test
    fun previousPersistedMediaIsDeletedOnlyAfterReplacementCommit() {
        val coordinator = coordinator(SavedStateHandle())
        coordinator.initializeSelection(null)
        val firstPromoted = requireNotNull(coordinator.acceptCrop(createCropOutput()))
        coordinator.commitSelection(deletePersistedMedia = true)
        val secondPromoted = requireNotNull(coordinator.acceptCrop(createCropOutput()))

        assertTrue(AppMediaStorage.isAppOwned(context, firstPromoted.toString()))
        assertTrue(AppMediaStorage.isAppOwned(context, secondPromoted.toString()))

        coordinator.commitSelection(deletePersistedMedia = true)

        assertFalse(AppMediaStorage.isAppOwned(context, firstPromoted.toString()))
        assertTrue(AppMediaStorage.isAppOwned(context, secondPromoted.toString()))
        coordinator.deleteInternalMedia(secondPromoted.toString())
    }

    @Test
    fun rollbackKeepsPersistedMediaAndDeletesOnlyNewSelection() {
        val coordinator = coordinator(SavedStateHandle())
        coordinator.initializeSelection(null)
        val persisted = requireNotNull(coordinator.acceptCrop(createCropOutput()))
        coordinator.commitSelection(deletePersistedMedia = true)
        val replacement = requireNotNull(coordinator.acceptCrop(createCropOutput()))

        val restored = coordinator.rollbackSelection()

        assertEquals(persisted.toString(), restored)
        assertTrue(AppMediaStorage.isAppOwned(context, persisted.toString()))
        assertFalse(AppMediaStorage.isAppOwned(context, replacement.toString()))
        coordinator.deleteInternalMedia(persisted.toString())
    }

    private fun createCropOutput() = requireNotNull(
        AppMediaStorage.createCropOutputUri(
            context = context,
            scope = AppMediaScope.TANK,
            ownerToken = OWNER_TOKEN
        )
    ).also { output ->
        File(requireNotNull(output.path)).writeBytes(byteArrayOf(1, 2, 3, 4))
    }

    private fun tempFiles(): List<File> {
        return File(context.filesDir, AppMediaScope.TANK.directoryName)
            .listFiles()
            .orEmpty()
            .filter { file -> file.name.contains("_${OWNER_TOKEN}_") }
    }

    private fun coordinator(savedStateHandle: SavedStateHandle) =
        MediaFlowCoordinatorViewModel(
            savedStateHandle = savedStateHandle,
            context = context,
            scope = AppMediaScope.TANK,
            ownerToken = OWNER_TOKEN,
            cropSpec = MediaCropSpec.TANK
        )

    private companion object {
        const val OWNER_TOKEN = "stage9-test-owner"
    }
}
