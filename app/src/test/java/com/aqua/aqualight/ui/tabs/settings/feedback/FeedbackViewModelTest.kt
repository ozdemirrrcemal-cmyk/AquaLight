package com.aqua.aqualight.ui.tabs.settings.feedback

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.aqua.aqualight.application.feedback.FeedbackOrphanCleanupResult
import com.aqua.aqualight.application.feedback.FeedbackRepository
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailure
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import com.aqua.aqualight.application.feedback.FeedbackSubmissionUseCase
import com.aqua.aqualight.platform.media.FeedbackMediaProcessingResult
import com.aqua.aqualight.platform.media.FeedbackMediaProcessor
import com.aqua.aqualight.platform.media.ProcessedFeedbackMedia
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun restoresFormAndSelectedMediaThenSubmitsThroughUseCase() = runTest(dispatcher) {
        val mediaFile = File.createTempFile("feedback-vm-", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        try {
            val repository = FakeFeedbackRepository()
            val mediaProcessor = FakeFeedbackMediaProcessor()
            val viewModel = viewModel(savedState(mediaFile), repository, mediaProcessor)

            assertEquals("Bug", viewModel.uiState.value.category)
            assertEquals("A reproducible problem", viewModel.uiState.value.message)
            assertEquals(mediaFile.canonicalPath, viewModel.uiState.value.screenshot?.path)
            assertFalse(viewModel.uiState.value.isBusy)

            val event = async { viewModel.events.first() }
            viewModel.submit()
            advanceUntilIdle()

            assertEquals(FeedbackUiEvent.SubmissionSucceeded, event.await())
            assertEquals("Bug", repository.request?.category)
            assertEquals(mediaFile.canonicalPath, repository.screenshotFile?.canonicalPath)
            assertEquals("9.0", repository.request?.appVersion)
            assertEquals("tr-TR", repository.request?.localeTag)
            assertEquals("", viewModel.uiState.value.category)
            assertNull(viewModel.uiState.value.screenshot)
            assertFalse(viewModel.uiState.value.isBusy)
            assertTrue(mediaProcessor.deletedPaths.contains(mediaFile.canonicalPath))
        } finally {
            mediaFile.delete()
        }
    }

    @Test
    fun synchronousBusyLockPreventsDoubleSubmitAndScreenshotDeletionDuringUpload() =
        runTest(dispatcher) {
            val mediaFile = File.createTempFile("feedback-race-", ".jpg").apply {
                writeBytes(byteArrayOf(1, 2, 3))
            }
            try {
                val gate = CompletableDeferred<Unit>()
                val repository = FakeFeedbackRepository().apply { submitGate = gate }
                val mediaProcessor = FakeFeedbackMediaProcessor()
                val viewModel = viewModel(savedState(mediaFile), repository, mediaProcessor)

                viewModel.submit()
                viewModel.submit()
                viewModel.clearScreenshot()

                assertTrue(viewModel.uiState.value.isSubmitting)
                assertNotNull(viewModel.uiState.value.screenshot)
                assertTrue(mediaProcessor.deletedPaths.isEmpty())

                runCurrent()
                assertEquals(1, repository.submitCount)
                gate.complete(Unit)
                advanceUntilIdle()

                assertEquals(1, repository.submitCount)
                assertFalse(viewModel.uiState.value.isBusy)
            } finally {
                mediaFile.delete()
            }
        }

    @Test
    fun mediaProcessingLockPreventsSubmitUntilSelectionCompletes() = runTest(dispatcher) {
        val processedFile = File.createTempFile("feedback-processed-", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        try {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeFeedbackRepository()
            val mediaProcessor = FakeFeedbackMediaProcessor().apply {
                processGate = gate
                processResult = FeedbackMediaProcessingResult.Success(
                    ProcessedFeedbackMedia(
                        path = processedFile.canonicalPath,
                        displayName = "processed.jpg",
                        width = 640,
                        height = 480,
                        byteCount = processedFile.length()
                    )
                )
            }
            val state = SavedStateHandle(
                mapOf(
                    "feedback.category" to "Bug",
                    "feedback.message" to "A reproducible problem"
                )
            )
            val viewModel = viewModel(state, repository, mediaProcessor)

            viewModel.selectScreenshotForTest { mediaProcessor.processForTest() }
            viewModel.submit()

            assertTrue(viewModel.uiState.value.isProcessingMedia)
            runCurrent()
            assertEquals(0, repository.submitCount)

            gate.complete(Unit)
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.screenshot)
            assertFalse(viewModel.uiState.value.isBusy)
            assertEquals(0, repository.submitCount)
        } finally {
            processedFile.delete()
        }
    }

    @Test
    fun recreationNeverReplaysAnInterruptedSubmission() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val mediaProcessor = FakeFeedbackMediaProcessor()
        val savedState = SavedStateHandle(
            mapOf(
                "feedback.category" to "Bug",
                "feedback.message" to "A reproducible problem"
            )
        )

        val first = viewModel(savedState, repository, mediaProcessor)
        assertFalse(first.uiState.value.isSubmitting)
        val recreated = viewModel(savedState, repository, mediaProcessor)
        advanceUntilIdle()

        assertEquals("Bug", recreated.uiState.value.category)
        assertEquals("A reproducible problem", recreated.uiState.value.message)
        assertFalse(recreated.uiState.value.isSubmitting)
        assertEquals(0, repository.submitCount)
    }

    @Test
    fun submissionFailureKeepsFormAndScreenshotForRetry() = runTest(dispatcher) {
        val mediaFile = File.createTempFile("feedback-retry-", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        try {
            val repository = FakeFeedbackRepository().apply {
                result = FeedbackSubmissionResult.Failure(
                    FeedbackSubmissionFailure(
                        kind = FeedbackSubmissionFailureKind.PERSISTENCE,
                        cause = IllegalStateException("write failed")
                    )
                )
            }
            val mediaProcessor = FakeFeedbackMediaProcessor()
            val viewModel = viewModel(savedState(mediaFile), repository, mediaProcessor)
            val event = async { viewModel.events.first() }

            viewModel.submit()
            advanceUntilIdle()

            assertEquals(
                FeedbackUiEvent.SubmissionFailed(FeedbackSubmissionFailureKind.PERSISTENCE),
                event.await()
            )
            assertEquals("Bug", viewModel.uiState.value.category)
            assertNotNull(viewModel.uiState.value.screenshot)
            assertTrue(mediaProcessor.deletedPaths.isEmpty())
            assertFalse(viewModel.uiState.value.isBusy)
        } finally {
            mediaFile.delete()
        }
    }

    @Test
    fun invalidRestoredMediaIsRemovedFromSavedState() = runTest(dispatcher) {
        val savedState = SavedStateHandle(
            mapOf(
                "feedback.screenshot.path" to "/invalid/outside/file.jpg",
                "feedback.screenshot.name" to "file.jpg",
                "feedback.screenshot.width" to 100,
                "feedback.screenshot.height" to 100,
                "feedback.screenshot.bytes" to 10L
            )
        )
        val processor = FakeFeedbackMediaProcessor().apply { allowRestore = false }

        val viewModel = viewModel(savedState, FakeFeedbackRepository(), processor)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.screenshot)
        assertNull(savedState.get<String>("feedback.screenshot.path"))
    }

    private fun viewModel(
        savedStateHandle: SavedStateHandle,
        repository: FakeFeedbackRepository,
        mediaProcessor: FakeFeedbackMediaProcessor
    ) = FeedbackViewModel(
        savedStateHandle = savedStateHandle,
        submissionUseCase = FeedbackSubmissionUseCase(repository),
        mediaProcessor = mediaProcessor,
        appVersionProvider = { "9.0" },
        localeTagProvider = { "tr-TR" }
    )

    private fun savedState(mediaFile: File) = SavedStateHandle(
        mapOf(
            "feedback.category" to "Bug",
            "feedback.email" to "",
            "feedback.message" to "A reproducible problem",
            "feedback.screenshot.path" to mediaFile.canonicalPath,
            "feedback.screenshot.name" to "screenshot.jpg",
            "feedback.screenshot.width" to 640,
            "feedback.screenshot.height" to 480,
            "feedback.screenshot.bytes" to mediaFile.length()
        )
    )

    private class FakeFeedbackRepository : FeedbackRepository {
        var request: FeedbackSubmissionRequest? = null
        var screenshotFile: File? = null
        var submitCount: Int = 0
        var submitGate: CompletableDeferred<Unit>? = null
        var result: FeedbackSubmissionResult = FeedbackSubmissionResult.Success("document-1")

        override suspend fun submit(
            request: FeedbackSubmissionRequest,
            screenshotFile: File?
        ): FeedbackSubmissionResult {
            submitCount += 1
            this.request = request
            this.screenshotFile = screenshotFile
            submitGate?.await()
            return result
        }

        override suspend fun cleanupOrphans() = FeedbackOrphanCleanupResult(0, 0, 0)
    }

    private class FakeFeedbackMediaProcessor : FeedbackMediaProcessor {
        var allowRestore: Boolean = true
        var processGate: CompletableDeferred<Unit>? = null
        var processResult: FeedbackMediaProcessingResult? = null
        val deletedPaths = mutableListOf<String>()

        suspend fun processForTest(): FeedbackMediaProcessingResult {
            processGate?.await()
            return processResult ?: error("Unexpected process call")
        }

        override suspend fun process(uri: Uri): FeedbackMediaProcessingResult = processForTest()

        override fun restore(
            path: String?,
            displayName: String?,
            width: Int?,
            height: Int?,
            byteCount: Long?
        ): ProcessedFeedbackMedia? {
            if (!allowRestore || path.isNullOrBlank()) return null
            val file = File(path)
            if (!file.isFile || file.length() <= 0L) return null
            return ProcessedFeedbackMedia(
                path = file.canonicalPath,
                displayName = displayName.orEmpty(),
                width = width ?: return null,
                height = height ?: return null,
                byteCount = byteCount ?: file.length()
            )
        }

        override suspend fun delete(path: String?) {
            path?.let(deletedPaths::add)
        }

        override suspend fun cleanupExpired() = Unit
    }
}
