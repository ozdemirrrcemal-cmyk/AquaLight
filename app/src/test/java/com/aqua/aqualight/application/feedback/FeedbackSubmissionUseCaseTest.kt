package com.aqua.aqualight.application.feedback

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackSubmissionUseCaseTest {

    @Test
    fun submitNormalizesAndForwardsValidRequestToRepository() = runTest {
        val repository = FakeFeedbackRepository()
        val useCase = FeedbackSubmissionUseCase(repository)
        val expected = FeedbackSubmissionResult.Success("document-1")
        repository.submitResult = expected

        val actual = useCase.submit(
            request().copy(
                category = "  Bug  ",
                email = "  user+tag@example.com  ",
                message = "  A reproducible feedback message  "
            )
        )

        assertEquals(expected, actual)
        assertEquals(SUBMISSION_ID, repository.request?.submissionId)
        assertEquals("Bug", repository.request?.category)
        assertEquals("user+tag@example.com", repository.request?.email)
        assertEquals("A reproducible feedback message", repository.request?.message)
    }

    @Test
    fun invalidRequestReturnsValidationFailureWithoutRepositoryCall() = runTest {
        val repository = FakeFeedbackRepository()
        val useCase = FeedbackSubmissionUseCase(repository)

        val result = useCase.submit(request().copy(message = "short"))

        val failure = (result as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.VALIDATION, failure.kind)
        assertNull(repository.request)
    }

    @Test
    fun invalidSubmissionIdentityIsRejectedBeforeRepositoryCall() = runTest {
        val repository = FakeFeedbackRepository()
        val result = FeedbackSubmissionUseCase(repository).submit(
            request().copy(submissionId = "not-a-uuid")
        )

        val failure = (result as FeedbackSubmissionResult.Failure).failure
        assertEquals(FeedbackSubmissionFailureKind.VALIDATION, failure.kind)
        assertNull(repository.request)
    }

    @Test
    fun commercialEmailPolicyAcceptsPlusAddressAndRejectsUnsafeForms() {
        assertTrue(FeedbackSubmissionPolicy.isEmailValid("user+tag@sub.example.co.uk"))
        assertTrue(FeedbackSubmissionPolicy.isEmailValid(""))
        assertEquals(false, FeedbackSubmissionPolicy.isEmailValid("user@localhost"))
        assertEquals(false, FeedbackSubmissionPolicy.isEmailValid(".user@example.com"))
        assertEquals(false, FeedbackSubmissionPolicy.isEmailValid("user.@example.com"))
        assertEquals(false, FeedbackSubmissionPolicy.isEmailValid("user..name@example.com"))
        assertEquals(
            false,
            FeedbackSubmissionPolicy.isEmailValid("a".repeat(65) + "@example.com")
        )
        assertEquals(
            false,
            FeedbackSubmissionPolicy.isEmailValid("a".repeat(243) + "@example.com")
        )
    }

    private fun request(): FeedbackSubmissionRequest {
        return FeedbackSubmissionRequest(
            submissionId = SUBMISSION_ID,
            category = "Bug",
            email = "user@example.com",
            message = "A reproducible feedback message",
            appVersion = "1.0",
            localeTag = "tr-TR"
        )
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

    private companion object {
        const val SUBMISSION_ID = "123e4567-e89b-42d3-a456-426614174000"
    }
}
