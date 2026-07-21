package com.aqua.aqualight.ui.tabs.settings.feedback

import androidx.lifecycle.SavedStateHandle
import com.aqua.aqualight.application.feedback.FeedbackRepository
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailure
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import com.aqua.aqualight.application.feedback.FeedbackSubmissionUseCase
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
    fun restoresFormThenSubmitsTextFeedbackThroughUseCase() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val viewModel = viewModel(savedState(), repository)

        assertEquals("Bug", viewModel.uiState.value.category)
        assertEquals("user@example.com", viewModel.uiState.value.email)
        assertEquals("A reproducible problem", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isSubmitting)

        val event = async { viewModel.events.first() }
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(FeedbackUiEvent.SubmissionSucceeded, event.await())
        assertEquals(1, repository.submitCount)
        assertEquals("Bug", repository.request?.category)
        assertEquals("user@example.com", repository.request?.email)
        assertEquals("A reproducible problem", repository.request?.message)
        assertEquals("9.0", repository.request?.appVersion)
        assertEquals("tr-TR", repository.request?.localeTag)
        assertEquals(FeedbackUiState(), viewModel.uiState.value)
    }

    @Test
    fun synchronousSubmissionLockPreventsDuplicateRequests() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeFeedbackRepository().apply { submitGate = gate }
        val viewModel = viewModel(savedState(), repository)

        viewModel.submit()
        viewModel.submit()

        assertTrue(viewModel.uiState.value.isSubmitting)
        runCurrent()
        assertEquals(1, repository.submitCount)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.submitCount)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun recreationRestoresFormWithoutReplayingSubmission() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val savedState = savedState()

        val first = viewModel(savedState, repository)
        assertFalse(first.uiState.value.isSubmitting)
        val recreated = viewModel(savedState, repository)
        advanceUntilIdle()

        assertEquals("Bug", recreated.uiState.value.category)
        assertEquals("A reproducible problem", recreated.uiState.value.message)
        assertFalse(recreated.uiState.value.isSubmitting)
        assertEquals(0, repository.submitCount)
    }

    @Test
    fun submissionFailureKeepsFormForRetry() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository().apply {
            result = FeedbackSubmissionResult.Failure(
                FeedbackSubmissionFailure(
                    kind = FeedbackSubmissionFailureKind.PERSISTENCE,
                    cause = IllegalStateException("write failed")
                )
            )
        }
        val viewModel = viewModel(savedState(), repository)
        val event = async { viewModel.events.first() }

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(
            FeedbackUiEvent.SubmissionFailed(FeedbackSubmissionFailureKind.PERSISTENCE),
            event.await()
        )
        assertEquals("Bug", viewModel.uiState.value.category)
        assertEquals("user@example.com", viewModel.uiState.value.email)
        assertEquals("A reproducible problem", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun invalidFormIsRejectedBeforeRepositoryCall() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val viewModel = viewModel(SavedStateHandle(), repository)

        viewModel.updateEmail("invalid-email")
        viewModel.updateMessage("short")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.submitCount)
        assertTrue(viewModel.uiState.value.categoryError)
        assertTrue(viewModel.uiState.value.emailError)
        assertTrue(viewModel.uiState.value.messageError)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    private fun viewModel(
        savedStateHandle: SavedStateHandle,
        repository: FakeFeedbackRepository
    ) = FeedbackViewModel(
        savedStateHandle = savedStateHandle,
        submissionUseCase = FeedbackSubmissionUseCase(repository),
        appVersionProvider = { "9.0" },
        localeTagProvider = { "tr-TR" }
    )

    private fun savedState() = SavedStateHandle(
        mapOf(
            "feedback.category" to "Bug",
            "feedback.email" to "user@example.com",
            "feedback.message" to "A reproducible problem"
        )
    )

    private class FakeFeedbackRepository : FeedbackRepository {
        var request: FeedbackSubmissionRequest? = null
        var submitCount: Int = 0
        var submitGate: CompletableDeferred<Unit>? = null
        var result: FeedbackSubmissionResult = FeedbackSubmissionResult.Success("document-1")

        override suspend fun submit(
            request: FeedbackSubmissionRequest
        ): FeedbackSubmissionResult {
            submitCount += 1
            this.request = request
            submitGate?.await()
            return result
        }
    }
}
