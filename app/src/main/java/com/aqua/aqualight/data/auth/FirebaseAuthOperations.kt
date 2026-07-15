package com.aqua.aqualight.data.auth

import com.aqua.aqualight.application.auth.AuthOperations

internal class FirebaseAuthOperations(
    private val repository: AuthRepository
) : AuthOperations {

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ) {
        repository.signInWithEmail(
            email = email,
            password = password
        )
    }

    override suspend fun registerWithEmail(
        email: String,
        password: String
    ) {
        repository.registerWithEmail(
            email = email,
            password = password
        )
    }

    override suspend fun signInWithGoogleToken(
        idToken: String
    ) {
        repository.signInWithGoogleToken(
            idToken = idToken
        )
    }

    override suspend fun sendPasswordResetEmail(
        email: String
    ) {
        repository.sendPasswordResetEmail(
            email = email
        )
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ) {
        repository.changePassword(
            currentPassword = currentPassword,
            newPassword = newPassword
        )
    }

    override suspend fun requestEmailChangeVerification(
        currentEmail: String,
        password: String,
        newEmail: String
    ) {
        repository.requestEmailChangeVerification(
            currentEmail = currentEmail,
            password = password,
            newEmail = newEmail
        )
    }

    override fun hasPasswordProvider(): Boolean {
        return repository.hasPasswordProvider()
    }

    override fun isGoogleUser(): Boolean {
        return repository.isGoogleUser()
    }

    override fun currentEmail(): String {
        return repository.currentEmail()
    }
}
