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
        assertEquals(SUBMISSION_ID_1, repository.request?.submissionId)
        assertEquals("Bug", repository.request?.category)
        assertEquals("user@example.com", repository.request?.email)
        assertEquals("A reproducible problem", repository.request?.message)
        assertEquals("9.0", repository.request?.appVersion)
        assertEquals("tr-TR", repository.request?.localeTag)
        assertEquals(FeedbackUiState(), viewModel.uiState.value)
    }

    @Test
    fun paddedPlusAddressIsAcceptedAndNormalizedBeforeSubmission() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val viewModel = viewModel(savedState(), repository)
        viewModel.updateEmail("  user+tag@sub.example.co.uk  ")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, repository.submitCount)
        assertEquals("user+tag@sub.example.co.uk", repository.request?.email)
        assertFalse(viewModel.uiState.value.emailError)
    }

    @Test
    fun optionalWhitespaceOnlyEmailIsAcceptedAsEmpty() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val viewModel = viewModel(savedState(), repository)
        viewModel.updateEmail("   ")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, repository.submitCount)
        assertEquals("", repository.request?.email)
    }

    @Test
    fun emailBeyondCommercialLimitIsRejectedBeforeRepositoryCall() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val viewModel = viewModel(savedState(), repository)
        viewModel.updateEmail("a".repeat(243) + "@example.com")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.submitCount)
        assertTrue(viewModel.uiState.value.emailError)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun emailLocalPartBeyond64CharactersIsRejected() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val viewModel = viewModel(savedState(), repository)
        viewModel.updateEmail("a".repeat(65) + "@example.com")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.submitCount)
        assertTrue(viewModel.uiState.value.emailError)
    }

    @Test
    fun emailDomainWithoutPublicSuffixIsRejected() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val viewModel = viewModel(savedState(), repository)
        viewModel.updateEmail("user@localhost")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.submitCount)
        assertTrue(viewModel.uiState.value.emailError)
    }

    @Test
    fun messageAtCommercialLimitIsAccepted() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val viewModel = viewModel(savedState(), repository)
        viewModel.updateMessage("x".repeat(500))

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, repository.submitCount)
        assertEquals(500, repository.request?.message?.length)
    }

    @Test
    fun messageAboveCommercialLimitIsRejected() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val viewModel = viewModel(savedState(), repository)
        viewModel.updateMessage("x".repeat(501))

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.submitCount)
        assertTrue(viewModel.uiState.value.messageError)
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
    fun networkFailureStopsLoadingKeepsFormAndReusesSubmissionIdentity() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository().apply {
            results += failure(FeedbackSubmissionFailureKind.NETWORK)
            results += FeedbackSubmissionResult.Success("document-1")
        }
        val ids = ArrayDeque(listOf(SUBMISSION_ID_1, SUBMISSION_ID_2))
        val viewModel = viewModel(savedState(), repository) { ids.removeFirst() }

        val failureEvent = async { viewModel.events.first() }
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(
            FeedbackUiEvent.SubmissionFailed(FeedbackSubmissionFailureKind.NETWORK),
            failureEvent.await()
        )
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals("Bug", viewModel.uiState.value.category)
        assertEquals("user@example.com", viewModel.uiState.value.email)
        assertEquals("A reproducible problem", viewModel.uiState.value.message)
        assertEquals(SUBMISSION_ID_1, repository.requests.single().submissionId)
        assertEquals(1, ids.size)

        val successEvent = async { viewModel.events.first() }
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(FeedbackUiEvent.SubmissionSucceeded, successEvent.await())
        assertEquals(2, repository.requests.size)
        assertEquals(SUBMISSION_ID_1, repository.requests[1].submissionId)
        assertEquals(1, ids.size)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(FeedbackUiState(), viewModel.uiState.value)
    }

    @Test
    fun editingAfterFailureCreatesANewSubmissionIdentity() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository().apply {
            results += failure(FeedbackSubmissionFailureKind.NETWORK)
            results += FeedbackSubmissionResult.Success("document-2")
        }
        val ids = ArrayDeque(listOf(SUBMISSION_ID_1, SUBMISSION_ID_2))
        val viewModel = viewModel(savedState(), repository) { ids.removeFirst() }

        viewModel.submit()
        advanceUntilIdle()
        viewModel.updateMessage("A different reproducible problem")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(SUBMISSION_ID_1, repository.requests[0].submissionId)
        assertEquals(SUBMISSION_ID_2, repository.requests[1].submissionId)
    }

    @Test
    fun editsAfterSubmitDoNotChangeValidatedRequestSnapshot() = runTest(dispatcher) {
        val repository = FakeFeedbackRepository()
        val viewModel = viewModel(savedState(), repository)

        viewModel.submit()
        viewModel.updateEmail("invalid-email")
        viewModel.updateMessage("short")
        advanceUntilIdle()

        assertEquals(1, repository.submitCount)
        assertEquals("user@example.com", repository.request?.email)
        assertEquals("A reproducible problem", repository.request?.message)
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
            result = failure(FeedbackSubmissionFailureKind.PERSISTENCE)
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
        repository: FakeFeedbackRepository,
        submissionIdProvider: () -> String = { SUBMISSION_ID_1 }
    ) = FeedbackViewModel(
        savedStateHandle = savedStateHandle,
        submissionUseCase = FeedbackSubmissionUseCase(repository),
        appVersionProvider = { "9.0" },
        localeTagProvider = { "tr-TR" },
        submissionIdProvider = submissionIdProvider
    )

    private fun savedState() = SavedStateHandle(
        mapOf(
            "feedback.category" to "Bug",
            "feedback.email" to "user@example.com",
            "feedback.message" to "A reproducible problem"
        )
    )

    private fun failure(kind: FeedbackSubmissionFailureKind) =
        FeedbackSubmissionResult.Failure(
            FeedbackSubmissionFailure(
                kind = kind,
                cause = IllegalStateException(kind.name)
            )
        )

    private class FakeFeedbackRepository : FeedbackRepository {
        var request: FeedbackSubmissionRequest? = null
        val requests = mutableListOf<FeedbackSubmissionRequest>()
        val results = ArrayDeque<FeedbackSubmissionResult>()
        var submitCount: Int = 0
        var submitGate: CompletableDeferred<Unit>? = null
        var result: FeedbackSubmissionResult = FeedbackSubmissionResult.Success("document-1")

        override suspend fun submit(
            request: FeedbackSubmissionRequest
        ): FeedbackSubmissionResult {
            submitCount += 1
            this.request = request
            requests += request
            submitGate?.await()
            return if (results.isEmpty()) result else results.removeFirst()
        }
    }

    private companion object {
        const val SUBMISSION_ID_1 = "123e4567-e89b-42d3-a456-426614174000"
        const val SUBMISSION_ID_2 = "123e4567-e89b-42d3-a456-426614174001"
    }
}
