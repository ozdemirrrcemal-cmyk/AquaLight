package com.aqua.aqualight.application.feedback

import java.io.File

/** Commercial feedback boundary. Firebase and Android callbacks never escape data code. */
interface FeedbackRepository {
    suspend fun submit(
        request: FeedbackSubmissionRequest,
        screenshotFile: File?
    ): FeedbackSubmissionResult

    suspend fun cleanupOrphans(): FeedbackOrphanCleanupResult
}

class FeedbackSubmissionUseCase(
    private val repository: FeedbackRepository
) {
    suspend fun submit(
        request: FeedbackSubmissionRequest,
        screenshotFile: File?
    ): FeedbackSubmissionResult {
        return repository.submit(request, screenshotFile)
    }

    suspend fun cleanupOrphans(): FeedbackOrphanCleanupResult {
        return repository.cleanupOrphans()
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
    val cause: Throwable?,
    val storagePath: String? = null,
    val rollbackCause: Throwable? = null
)

enum class FeedbackSubmissionFailureKind {
    UPLOAD,
    PERSISTENCE,
    ROLLBACK,
    GENERIC
}

data class FeedbackOrphanCleanupResult(
    val attemptedCount: Int,
    val deletedCount: Int,
    val remainingCount: Int
)
