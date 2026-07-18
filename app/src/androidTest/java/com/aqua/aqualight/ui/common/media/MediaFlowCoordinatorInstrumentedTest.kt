package com.aqua.aqualight.ui.common.media

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import com.aqua.aqualight.platform.media.FeedbackMediaProcessingResult
import com.aqua.aqualight.platform.media.FeedbackMediaProcessor
import com.aqua.aqualight.platform.media.ProcessedFeedbackMedia
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
    fun pendingCameraUriSurvivesCoordinatorRecreation() = runBlocking {
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
    fun boundedPreparedSourceAndPendingCropAreCleanedAfterCancel() = runBlocking {
        AppMediaStorage.deleteOwnerTemporaryFiles(context, AppMediaScope.TANK, OWNER_TOKEN)
        val savedState = SavedStateHandle()
        val processor = FakeProcessor(preparedSource())
        val first = coordinator(savedState, processor)
        val cameraUri = requireNotNull(first.createCameraUri())
        val preparation = first.prepareCropIntent(cameraUri, "Crop")

        assertTrue(preparation is MediaCropPreparationResult.Ready)
        assertTrue(tempFiles().any { it.name.contains("_camera_") })
        assertTrue(tempFiles().any { it.name.contains("_crop_") })

        first.cancelCrop()
        first.cancelCamera()

        assertFalse(AppMediaStorage.isAppOwned(context, cameraUri.toString()))
        assertFalse(tempFiles().any { it.name.contains("_camera_") || it.name.contains("_crop_") })
        assertTrue(processor.deletedPaths.isNotEmpty())
    }

    @Test
    fun acceptedCropIsPromotedAndRollbackDeletesPendingSelection() = runBlocking {
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
    fun coordinatorClearKeepsPromotedSelectionForRepositoryRecovery() = runBlocking {
        val coordinator = coordinator(SavedStateHandle())
        coordinator.initializeSelection(null)
        val promoted = requireNotNull(coordinator.acceptCrop(createCropOutput()))

        MediaFlowCoordinatorViewModel::class.java
            .getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(coordinator)

        assertTrue(AppMediaStorage.isAppOwned(context, promoted.toString()))
        AppMediaStorage.rollbackPendingMedia(context, promoted.toString())
        assertFalse(AppMediaStorage.isAppOwned(context, promoted.toString()))
    }

    @Test
    fun previousPersistedMediaIsDeletedOnlyAfterReplacementCommit() = runBlocking {
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
        AppMediaStorage.deleteInternalMedia(context, secondPromoted.toString())
    }

    @Test
    fun rollbackKeepsPersistedMediaAndDeletesOnlyNewSelection() = runBlocking {
        val coordinator = coordinator(SavedStateHandle())
        coordinator.initializeSelection(null)
        val persisted = requireNotNull(coordinator.acceptCrop(createCropOutput()))
        coordinator.commitSelection(deletePersistedMedia = true)
        val replacement = requireNotNull(coordinator.acceptCrop(createCropOutput()))

        val restored = coordinator.rollbackSelection()

        assertEquals(persisted.toString(), restored)
        assertTrue(AppMediaStorage.isAppOwned(context, persisted.toString()))
        assertFalse(AppMediaStorage.isAppOwned(context, replacement.toString()))
        AppMediaStorage.deleteInternalMedia(context, persisted.toString())
    }

    @Test
    fun ownerReconciliationPreservesReferencedMediaAndExpiresOnlyOrphan() = runBlocking {
        val coordinator = coordinator(SavedStateHandle())
        coordinator.initializeSelection(null)
        val referenced = requireNotNull(coordinator.acceptCrop(createCropOutput()))
        coordinator.commitSelection(deletePersistedMedia = false)
        val orphan = requireNotNull(coordinator.acceptCrop(createCropOutput()))

        AppMediaStorage.reconcilePendingMedia(
            context = context,
            ownerUid = OWNER_UID,
            referencedUris = listOf(referenced.toString()),
            nowMillis = System.currentTimeMillis() + TWO_DAYS_MILLIS
        )

        assertTrue(AppMediaStorage.isAppOwned(context, referenced.toString()))
        assertFalse(AppMediaStorage.isAppOwned(context, orphan.toString()))
        AppMediaStorage.deleteInternalMedia(context, referenced.toString())
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

    private fun preparedSource(): ProcessedFeedbackMedia {
        val directory = File(context.cacheDir, "feedback_media").apply { mkdirs() }
        val file = File.createTempFile("feedback_output_test_", ".jpg", directory).apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        return ProcessedFeedbackMedia(
            path = file.canonicalPath,
            displayName = file.name,
            width = 100,
            height = 100,
            byteCount = file.length()
        )
    }

    private fun tempFiles(): List<File> {
        return File(context.filesDir, AppMediaScope.TANK.directoryName)
            .listFiles()
            .orEmpty()
            .filter { file -> file.name.contains("_${OWNER_TOKEN}_") }
    }

    private fun coordinator(
        savedStateHandle: SavedStateHandle,
        processor: FeedbackMediaProcessor = FakeProcessor(preparedSource())
    ) = MediaFlowCoordinatorViewModel(
        savedStateHandle = savedStateHandle,
        context = context,
        scope = AppMediaScope.TANK,
        ownerToken = OWNER_TOKEN,
        ownerUid = OWNER_UID,
        cropSpec = MediaCropSpec.TANK,
        mediaProcessor = processor,
        dispatcher = Dispatchers.Unconfined
    )

    private class FakeProcessor(
        private val media: ProcessedFeedbackMedia
    ) : FeedbackMediaProcessor {
        val deletedPaths = mutableListOf<String>()

        override suspend fun process(uri: Uri): FeedbackMediaProcessingResult =
            FeedbackMediaProcessingResult.Success(media)

        override fun restore(
            path: String?,
            displayName: String?,
            width: Int?,
            height: Int?,
            byteCount: Long?
        ): ProcessedFeedbackMedia? = null

        override suspend fun delete(path: String?) {
            if (path == null) return
            deletedPaths += path
            File(path).delete()
        }

        override suspend fun cleanupExpired() = Unit
    }

    private companion object {
        const val OWNER_TOKEN = "stage9-test-owner"
        const val OWNER_UID = "stage9-owner-uid"
        const val TWO_DAYS_MILLIS = 2L * 24L * 60L * 60L * 1000L
    }
}
