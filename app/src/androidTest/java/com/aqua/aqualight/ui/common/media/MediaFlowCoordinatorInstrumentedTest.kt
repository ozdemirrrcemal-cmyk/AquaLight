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
    fun acceptedCropIsPromotedAndRollbackDeletesPendingSelection() {
        val savedState = SavedStateHandle()
        val coordinator = coordinator(savedState)
        coordinator.initializeSelection(null)
        val output = AppMediaStorage.createCropOutputUri(
            context = context,
            scope = AppMediaScope.TANK,
            ownerToken = "test-owner"
        )
        assertNotNull(output)
        File(requireNotNull(output).path!!).writeBytes(byteArrayOf(1, 2, 3, 4))

        val promoted = coordinator.acceptCrop(output)

        assertNotNull(promoted)
        assertTrue(AppMediaStorage.isAppOwned(context, promoted.toString()))
        assertTrue(coordinator.selection.value.hasPendingChange)
        coordinator.rollbackSelection()
        assertFalse(AppMediaStorage.isAppOwned(context, promoted.toString()))
    }

    private fun coordinator(
        savedStateHandle: SavedStateHandle
    ): MediaFlowCoordinatorViewModel {
        return MediaFlowCoordinatorViewModel(
            savedStateHandle = savedStateHandle,
            context = context,
            scope = AppMediaScope.TANK,
            ownerToken = "test-owner",
            cropSpec = MediaCropSpec.TANK
        )
    }
}
