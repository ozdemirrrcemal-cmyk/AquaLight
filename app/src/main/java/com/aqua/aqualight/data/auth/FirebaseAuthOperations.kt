package com.aqua.aqualight.data.auth

import com.aqua.aqualight.application.auth.AuthOperations
import com.aqua.aqualight.application.auth.AuthOperationException
import com.aqua.aqualight.application.auth.AuthOperationFailure
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CancellationException

internal class FirebaseAuthOperations(
    private val repository: AuthRepository
) : AuthOperations {

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ) {
        execute {
            repository.signInWithEmail(
                email = email,
                password = password
            )
        }
    }

    override suspend fun registerWithEmail(
        email: String,
        password: String
    ) {
        execute {
            repository.registerWithEmail(
                email = email,
                password = password
            )
        }
    }

    override suspend fun signInWithGoogleToken(
        idToken: String
    ) {
        execute {
            repository.signInWithGoogleToken(
                idToken = idToken
            )
        }
    }

    override suspend fun sendPasswordResetEmail(
        email: String
    ) {
        execute {
            repository.sendPasswordResetEmail(
                email = email
            )
        }
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ) {
        execute {
            repository.changePassword(
                currentPassword = currentPassword,
                newPassword = newPassword
            )
        }
    }

    override suspend fun requestEmailChangeVerification(
        currentEmail: String,
        password: String,
        newEmail: String
    ) {
        execute {
            repository.requestEmailChangeVerification(
                currentEmail = currentEmail,
                password = password,
                newEmail = newEmail
            )
        }
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

    private suspend fun execute(block: suspend () -> Unit) {
        val failure = runCatching { block() }.exceptionOrNull() ?: return
        val translatedFailure = when (failure) {
            is CancellationException,
            is AuthOperationException -> failure
            else -> AuthOperationException(
                failure = failure.toAuthOperationFailure(),
                cause = failure
            )
        }
        throw translatedFailure
    }
}

internal fun Throwable.toAuthOperationFailure(): AuthOperationFailure = when (this) {
    AuthRepositoryException.NoAuthenticatedUser ->
        AuthOperationFailure.NO_AUTHENTICATED_USER
    AuthRepositoryException.MissingEmail -> AuthOperationFailure.MISSING_EMAIL
    AuthRepositoryException.CurrentEmailMismatch ->
        AuthOperationFailure.CURRENT_EMAIL_MISMATCH
    AuthRepositoryException.EmailAlreadyInUse ->
        AuthOperationFailure.EMAIL_ALREADY_IN_USE
    AuthRepositoryException.NoFirebaseUserFromResult ->
        AuthOperationFailure.PROVIDER_USER_MISSING
    is FirebaseAuthInvalidCredentialsException,
    is FirebaseAuthInvalidUserException -> AuthOperationFailure.INVALID_CREDENTIALS
    is FirebaseAuthUserCollisionException -> AuthOperationFailure.USER_COLLISION
    is FirebaseAuthWeakPasswordException -> AuthOperationFailure.WEAK_PASSWORD
    is FirebaseNetworkException -> AuthOperationFailure.NETWORK
    is FirebaseTooManyRequestsException -> AuthOperationFailure.RATE_LIMITED
    is FirebaseAuthRecentLoginRequiredException ->
        AuthOperationFailure.RECENT_LOGIN_REQUIRED
    else -> AuthOperationFailure.UNKNOWN
}
