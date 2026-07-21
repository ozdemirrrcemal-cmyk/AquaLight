package com.aqua.aqualight.application.feedback

/** Persistence boundary for authenticated feedback submissions. */
interface FeedbackRepository {
    suspend fun submit(request: FeedbackSubmissionRequest): FeedbackSubmissionResult
}

class FeedbackSubmissionUseCase(
    private val repository: FeedbackRepository
) {
    suspend fun submit(request: FeedbackSubmissionRequest): FeedbackSubmissionResult {
        return repository.submit(request)
    }
}

data class FeedbackSubmissionRequest(
    val category: String,
    val email: String,
    val message: String,
    val appVersion: String,
    val localeTag: String
)

sealed interface FeedbackSubmissionResult {
    data class Success(val documentId: String) : FeedbackSubmissionResult
    data class Failure(val failure: FeedbackSubmissionFailure) : FeedbackSubmissionResult
}

data class FeedbackSubmissionFailure(
    val kind: FeedbackSubmissionFailureKind,
    val cause: Throwable?
)

enum class FeedbackSubmissionFailureKind {
    AUTHENTICATION,
    PERSISTENCE,
    GENERIC
}
