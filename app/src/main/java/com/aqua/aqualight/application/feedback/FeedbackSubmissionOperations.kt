package com.aqua.aqualight.application.feedback

import java.io.File

interface FeedbackSubmissionOperations {
    fun submit(
        request: FeedbackSubmissionRequest,
        screenshotFile: File?,
        callback: FeedbackSubmissionCallback
    )
}

data class FeedbackSubmissionRequest(
    val category: String,
    val email: String,
    val message: String,
    val appVersion: String,
    val localeTag: String
)

interface FeedbackSubmissionCallback {
    fun onSuccess()

    fun onFailure(failure: FeedbackSubmissionFailure)
}

data class FeedbackSubmissionFailure(
    val kind: FeedbackSubmissionFailureKind,
    val cause: Throwable?
)

enum class FeedbackSubmissionFailureKind {
    UPLOAD,
    GENERIC
}
