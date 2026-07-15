package com.aqua.aqualight.application.auth

/** UI-facing boundary for logout and post-sensitive-action session cleanup. */
interface SessionExitOperations {
    suspend fun logout(): SessionExitResult

    suspend fun cleanupAfterSensitiveAction(
        cancelNotifications: Boolean = true
    ): SessionExitResult
}

data class SessionExitResult(
    val blockingError: Throwable? = null
) {
    val hasBlockingError: Boolean
        get() = blockingError != null
}
