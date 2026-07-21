package com.aqua.aqualight.application.feedback

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test

class FeedbackSubmissionUseCaseTest {

    @Test
    fun submitForwardsRequestToRepository() = runTest {
        val repository = FakeFeedbackRepository()
        val useCase = FeedbackSubmissionUseCase(repository)
        val request = FeedbackSubmissionRequest(
            category = "Bug",
            email = "user@example.com",
            message = "A reproducible feedback message",
            appVersion = "1.0",
            localeTag = "tr-TR"
        )
        val expected = FeedbackSubmissionResult.Success("document-1")
        repository.submitResult = expected

        val actual = useCase.submit(request)

        assertSame(request, repository.request)
        assertSame(expected, actual)
    }

    private class FakeFeedbackRepository : FeedbackRepository {
        var request: FeedbackSubmissionRequest? = null
        var submitResult: FeedbackSubmissionResult =
            FeedbackSubmissionResult.Success("default")

        override suspend fun submit(
            request: FeedbackSubmissionRequest
        ): FeedbackSubmissionResult {
            this.request = request
            return submitResult
        }
    }
}
