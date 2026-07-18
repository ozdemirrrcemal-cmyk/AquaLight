package com.aqua.aqualight.application.feedback

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FeedbackSubmissionUseCaseTest {

    @Test
    fun submitForwardsRequestAndFileToRepository() = runTest {
        val repository = FakeFeedbackRepository()
        val useCase = FeedbackSubmissionUseCase(repository)
        val request = request()
        val screenshotFile = File("feedback.jpg")
        val expected = FeedbackSubmissionResult.Success("document-1")
        repository.submitResult = expected

        val actual = useCase.submit(request, screenshotFile)

        assertSame(request, repository.request)
        assertSame(screenshotFile, repository.screenshotFile)
        assertSame(expected, actual)
    }

    @Test
    fun cleanupForwardsToRepository() = runTest {
        val repository = FakeFeedbackRepository()
        val expected = FeedbackOrphanCleanupResult(
            attemptedCount = 3,
            deletedCount = 2,
            remainingCount = 1
        )
        repository.cleanupResult = expected
        val useCase = FeedbackSubmissionUseCase(repository)

        val actual = useCase.cleanupOrphans()

        assertEquals(expected, actual)
    }

    private fun request(): FeedbackSubmissionRequest {
        return FeedbackSubmissionRequest(
            category = "Bug",
            email = "user@example.com",
            message = "A reproducible feedback message",
            appVersion = "1.0",
            localeTag = "tr-TR"
        )
    }

    private class FakeFeedbackRepository : FeedbackRepository {
        var request: FeedbackSubmissionRequest? = null
        var screenshotFile: File? = null
        var submitResult: FeedbackSubmissionResult =
            FeedbackSubmissionResult.Success("default")
        var cleanupResult: FeedbackOrphanCleanupResult =
            FeedbackOrphanCleanupResult(0, 0, 0)

        override suspend fun submit(
            request: FeedbackSubmissionRequest,
            screenshotFile: File?
        ): FeedbackSubmissionResult {
            this.request = request
            this.screenshotFile = screenshotFile
            return submitResult
        }

        override suspend fun cleanupOrphans(): FeedbackOrphanCleanupResult {
            return cleanupResult
        }
    }
}
