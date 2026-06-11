package com.aqua.aqualight.data.auth

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth

/**
 * Auth data boundary for the app.
 *
 * Fragments should not call FirebaseAuth task callbacks directly. They call a
 * ViewModel, the ViewModel calls this repository, and this repository performs
 * Firebase work with suspend functions.
 */
class AuthRepository private constructor(
    private val firebaseAuth: FirebaseAuth,
    private val sessionManager: AuthSessionManager
) {

    companion object {
        fun create(
            context: Context
        ): AuthRepository {
            val appContext = context.applicationContext

            return AuthRepository(
                firebaseAuth = Firebase.auth,
                sessionManager = AuthSessionManager.create(
                    appContext
                )
            )
        }
    }

    suspend fun signInWithEmail(
        email: String,
        password: String
    ): AuthSessionManager.Session {
        val result = firebaseAuth
            .signInWithEmailAndPassword(
                email,
                password
            )
            .awaitTask()

        val user = result.user
            ?: throw AuthRepositoryException.NoFirebaseUserFromResult

        return sessionManager.completeLogin(
            user = user
        )
    }

    suspend fun registerWithEmail(
        email: String,
        password: String
    ): AuthSessionManager.Session {
        val result = firebaseAuth
            .createUserWithEmailAndPassword(
                email,
                password
            )
            .awaitTask()

        val user = result.user
            ?: throw AuthRepositoryException.NoFirebaseUserFromResult

        return sessionManager.completeLogin(
            user = user
        )
    }

    suspend fun signInWithGoogleToken(
        idToken: String
    ): AuthSessionManager.Session {
        val credential = GoogleAuthProvider.getCredential(
            idToken,
            null
        )

        val result = firebaseAuth
            .signInWithCredential(
                credential
            )
            .awaitTask()

        val user = result.user
            ?: firebaseAuth.currentUser
            ?: throw AuthRepositoryException.NoFirebaseUserFromResult

        return sessionManager.completeLogin(
            user = user
        )
    }

    suspend fun sendPasswordResetEmail(
        email: String
    ) {
        firebaseAuth
            .sendPasswordResetEmail(
                email
            )
            .awaitTask()
    }

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ) {
        val user = firebaseAuth.currentUser
            ?: throw AuthRepositoryException.NoAuthenticatedUser

        val email = user.email
            ?: throw AuthRepositoryException.MissingEmail

        val credential = EmailAuthProvider.getCredential(
            email,
            currentPassword
        )

        user.reauthenticate(
            credential
        ).awaitTask()

        user.updatePassword(
            newPassword
        ).awaitTask()
    }

    suspend fun requestEmailChangeVerification(
        currentEmail: String,
        password: String,
        newEmail: String
    ) {
        val user = firebaseAuth.currentUser
            ?: throw AuthRepositoryException.NoAuthenticatedUser

        val activeEmail = user.email
            ?: throw AuthRepositoryException.MissingEmail

        if (!currentEmail.equals(activeEmail, ignoreCase = true)) {
            throw AuthRepositoryException.CurrentEmailMismatch
        }

        val emailAlreadyRegistered = runCatching {
            firebaseAuth
                .fetchSignInMethodsForEmail(
                    newEmail
                )
                .awaitTask()
                .signInMethods
                .orEmpty()
                .isNotEmpty()
        }.getOrNull()

        if (emailAlreadyRegistered == true) {
            throw AuthRepositoryException.EmailAlreadyInUse
        }

        val credential = EmailAuthProvider.getCredential(
            activeEmail,
            password
        )

        user.reauthenticate(
            credential
        ).awaitTask()

        user.verifyBeforeUpdateEmail(
            newEmail
        ).awaitTask()
    }

    fun hasPasswordProvider(): Boolean {
        val user = firebaseAuth.currentUser ?: return false

        return user.providerData.any {
            it.providerId == EmailAuthProvider.PROVIDER_ID
        }
    }

    fun isGoogleUser(): Boolean {
        val user = firebaseAuth.currentUser ?: return false

        return user.providerData.any {
            it.providerId == GoogleAuthProvider.PROVIDER_ID
        }
    }

    fun currentEmail(): String {
        return firebaseAuth.currentUser?.email.orEmpty()
    }
}
