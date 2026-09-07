package com.aqua.aqualight.application.auth

/** Stable failure taxonomy exposed by the authentication application boundary. */
enum class AuthOperationFailure {
    NO_AUTHENTICATED_USER,
    MISSING_EMAIL,
    CURRENT_EMAIL_MISMATCH,
    EMAIL_ALREADY_IN_USE,
    PROVIDER_USER_MISSING,
    INVALID_CREDENTIALS,
    USER_COLLISION,
    WEAK_PASSWORD,
    NETWORK,
    RATE_LIMITED,
    RECENT_LOGIN_REQUIRED,
    UNKNOWN
}

/** Prevents Firebase and repository exception types from crossing into presentation code. */
class AuthOperationException(
    val failure: AuthOperationFailure,
    cause: Throwable? = null
) : Exception("Authentication operation failed: $failure", cause)
