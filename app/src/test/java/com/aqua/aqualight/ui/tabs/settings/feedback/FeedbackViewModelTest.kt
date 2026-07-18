package com.aqua.aqualight.ui.tabs.settings.feedback

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.aqua.aqualight.application.feedback.FeedbackOrphanCleanupResult
import com.aqua.aqualight.application.feedback.FeedbackRepository
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import com.aqua.aqualight.application.feedback.FeedbackSubmissionUseCase
import com.aqua.aqualight.platform.media.FeedbackMediaProcessingResult
import com.aqua.aqualight.platform.media.FeedbackMediaProcessor
import com.aqua.aqualight.platform.media.ProcessedFeedbackMedia
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

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
    fun restoresFormFromSavedStateAndSubmitsThroughUseCase() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val viewModel = FeedbackViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "feedback.category" to "Bug",
                    "feedback.email" to "",
                    "feedback.message" to "A reproducible problem"
                )
            ),
            submissionUseCase = FeedbackSubmissionUseCase(repository),
            mediaProcessor = FakeFeedbackMediaProcessor(),
            appVersionProvider = { "9.0" },
            localeTagProvider = { "tr-TR" }
        )

        assertEquals("Bug", viewModel.uiState.value.category)
        assertEquals("A reproducible problem", viewModel.uiState.value.message)

        val event = async { viewModel.events.first() }
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(FeedbackUiEvent.SubmissionSucceeded, event.await())
        assertEquals("Bug", repository.request?.category)
        assertEquals("9.0", repository.request?.appVersion)
        assertEquals("tr-TR", repository.request?.localeTag)
        assertEquals("", viewModel.uiState.value.category)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    private class FakeFeedbackRepository : FeedbackRepository {
        var request: FeedbackSubmissionRequest? = null

        override suspend fun submit(
            request: FeedbackSubmissionRequest,
            screenshotFile: File?
        ): FeedbackSubmissionResult {
            this.request = request
            return FeedbackSubmissionResult.Success("document-1")
        }

        override suspend fun cleanupOrphans(): FeedbackOrphanCleanupResult {
            return FeedbackOrphanCleanupResult(0, 0, 0)
        }
    }

    private class FakeFeedbackMediaProcessor : FeedbackMediaProcessor {
        override suspend fun process(uri: Uri): FeedbackMediaProcessingResult {
            error("Not expected")
        }

        override fun restore(
            path: String?,
            displayName: String?,
            width: Int?,
            height: Int?,
            byteCount: Long?
        ): ProcessedFeedbackMedia? = null

        override suspend fun delete(path: String?) = Unit

        override suspend fun cleanupExpired() = Unit
    }
}
