package com.aqua.aqualight.application.auth

enum class AccountProvider {
    GOOGLE,
    PASSWORD,
    UNKNOWN
}

interface AccountSecurityOperations {
    fun provider(): AccountProvider

    fun currentEmail(): String?

    suspend fun reauthenticateWithPassword(
        password: String
    )

    suspend fun reauthenticateWithGoogleToken(
        idToken: String
    )

    suspend fun deleteCurrentAccount(): AccountDeletionResult
}

data class AccountDeletionResult(
    val accountDeleteError: Throwable? = null,
    val cleanupErrors: List<Throwable> = emptyList()
) {
    val hasPostDeleteCleanupErrors: Boolean
        get() = cleanupErrors.isNotEmpty()
}
