package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.application.auth.AccountDeletionResult
import com.aqua.aqualight.application.auth.AccountProvider
import com.aqua.aqualight.application.auth.AccountSecurityOperations
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class FirebaseAccountSecurityOperations private constructor(
    private val firebaseAuth: FirebaseAuth,
    private val accountDeletionManager: AccountDeletionManager
) : AccountSecurityOperations {

    override fun provider(): AccountProvider {
        val providerIds = firebaseAuth.currentUser
            ?.providerData
            ?.map { it.providerId }
            .orEmpty()

        return when {
            GoogleAuthProvider.PROVIDER_ID in providerIds -> AccountProvider.GOOGLE
            EmailAuthProvider.PROVIDER_ID in providerIds -> AccountProvider.PASSWORD
            else -> AccountProvider.UNKNOWN
        }
    }

    override fun currentEmail(): String? {
        return firebaseAuth.currentUser?.email
    }

    override suspend fun reauthenticateWithPassword(
        password: String
    ) {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("No authenticated user.")
        val email = user.email
            ?: throw IllegalStateException("Authenticated user has no email address.")
        val credential = EmailAuthProvider.getCredential(
            email,
            password
        )

        user.reauthenticate(credential).awaitCompletion()
    }

    override suspend fun reauthenticateWithGoogleToken(
        idToken: String
    ) {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("No authenticated user.")
        val credential = GoogleAuthProvider.getCredential(
            idToken,
            null
        )

        user.reauthenticate(credential).awaitCompletion()
    }

    override suspend fun deleteCurrentAccount(): AccountDeletionResult {
        val result = accountDeletionManager.deleteCurrentAccount()
        val cleanupErrors = buildList {
            result.localCleanupResult.issues.forEach { issue ->
                add(issue.error)
            }
            result.googleRevokeError?.let(::add)
            result.firebaseSignOutError?.let(::add)
        }

        return AccountDeletionResult(
            accountDeleteError = result.accountDeleteError,
            cleanupErrors = cleanupErrors
        )
    }

    private suspend fun Task<Void>.awaitCompletion() {
        suspendCancellableCoroutine<Unit> { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) {
                    return@addOnCompleteListener
                }

                val error = task.exception
                if (task.isSuccessful && error == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        error ?: IllegalStateException("Firebase task failed.")
                    )
                }
            }
        }
    }

    companion object {
        fun create(
            context: Context
        ): FirebaseAccountSecurityOperations {
            val appContext = context.applicationContext
            return FirebaseAccountSecurityOperations(
                firebaseAuth = Firebase.auth,
                accountDeletionManager = AccountDeletionManager.create(appContext)
            )
        }
    }
}
