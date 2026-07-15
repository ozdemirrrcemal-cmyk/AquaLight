package com.aqua.aqualight.application.auth

/**
 * UI-facing authentication application boundary.
 *
 * ViewModels depend on this contract rather than Firebase or a concrete data
 * repository. Tests can provide a deterministic fake implementation.
 */
interface AuthOperations {
    suspend fun signInWithEmail(
        email: String,
        password: String
    )

    suspend fun registerWithEmail(
        email: String,
        password: String
    )

    suspend fun signInWithGoogleToken(
        idToken: String
    )

    suspend fun sendPasswordResetEmail(
        email: String
    )

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    )

    suspend fun requestEmailChangeVerification(
        currentEmail: String,
        password: String,
        newEmail: String
    )

    fun hasPasswordProvider(): Boolean

    fun isGoogleUser(): Boolean

    fun currentEmail(): String
}
