package com.aqua.aqualight.application.feedback

/** Commercial text-feedback boundary. Firebase callbacks never escape data code. */
interface FeedbackRepository {
    suspend fun submit(request: FeedbackSubmissionRequest): FeedbackSubmissionResult
}

class FeedbackSubmissionUseCase(
    private val repository: FeedbackRepository
) {
    suspend fun submit(request: FeedbackSubmissionRequest): FeedbackSubmissionResult {
        val normalized = FeedbackSubmissionPolicy.normalize(request)
        val validationError = FeedbackSubmissionPolicy.validate(normalized)
        if (validationError != null) {
            return FeedbackSubmissionResult.Failure(
                FeedbackSubmissionFailure(
                    kind = FeedbackSubmissionFailureKind.VALIDATION,
                    cause = IllegalArgumentException(validationError.name)
                )
            )
        }
        return repository.submit(normalized)
    }
}

data class FeedbackSubmissionRequest(
    val submissionId: String,
    val category: String,
    val email: String,
    val message: String,
    val appVersion: String,
    val localeTag: String
)

object FeedbackSubmissionPolicy {
    const val SUBMISSION_ID_LENGTH = 36
    const val CATEGORY_MAX_LENGTH = 80
    const val EMAIL_MAX_LENGTH = 254
    const val EMAIL_LOCAL_PART_MAX_LENGTH = 64
    const val MESSAGE_MIN_LENGTH = 10
    const val MESSAGE_MAX_LENGTH = 500
    const val APP_VERSION_MAX_LENGTH = 64
    const val LOCALE_TAG_MAX_LENGTH = 35

    private val submissionIdPattern = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE
    )
    private val emailPattern = Regex(
        "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+" +
            "(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@" +
            "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?" +
            "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
    )

    fun normalize(request: FeedbackSubmissionRequest): FeedbackSubmissionRequest {
        return request.copy(
            submissionId = request.submissionId.trim().lowercase(),
            category = request.category.trim(),
            email = request.email.trim(),
            message = request.message.trim(),
            appVersion = request.appVersion.trim(),
            localeTag = request.localeTag.trim()
        )
    }

    fun validate(request: FeedbackSubmissionRequest): FeedbackSubmissionValidationError? {
        if (!isSubmissionIdValid(request.submissionId)) {
            return FeedbackSubmissionValidationError.SUBMISSION_ID
        }
        if (request.category.isEmpty() || request.category.length > CATEGORY_MAX_LENGTH) {
            return FeedbackSubmissionValidationError.CATEGORY
        }
        if (!isEmailValid(request.email)) {
            return FeedbackSubmissionValidationError.EMAIL
        }
        if (request.message.length !in MESSAGE_MIN_LENGTH..MESSAGE_MAX_LENGTH) {
            return FeedbackSubmissionValidationError.MESSAGE
        }
        if (request.appVersion.isEmpty() || request.appVersion.length > APP_VERSION_MAX_LENGTH) {
            return FeedbackSubmissionValidationError.APP_VERSION
        }
        if (request.localeTag.isEmpty() || request.localeTag.length > LOCALE_TAG_MAX_LENGTH) {
            return FeedbackSubmissionValidationError.LOCALE
        }
        return null
    }

    fun isSubmissionIdValid(value: String): Boolean {
        val submissionId = value.trim()
        return submissionId.length == SUBMISSION_ID_LENGTH && submissionIdPattern.matches(submissionId)
    }

    fun isEmailValid(value: String): Boolean {
        val email = value.trim()
        if (email.isEmpty()) return true
        if (email.length > EMAIL_MAX_LENGTH) return false
        val separatorIndex = email.lastIndexOf('@')
        if (separatorIndex <= 0 || separatorIndex == email.lastIndex) return false
        if (email.substring(0, separatorIndex).length > EMAIL_LOCAL_PART_MAX_LENGTH) return false
        return emailPattern.matches(email)
    }
}

enum class FeedbackSubmissionValidationError {
    SUBMISSION_ID,
    CATEGORY,
    EMAIL,
    MESSAGE,
    APP_VERSION,
    LOCALE
}

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
    VALIDATION,
    NETWORK,
    PERSISTENCE,
    GENERIC
}
