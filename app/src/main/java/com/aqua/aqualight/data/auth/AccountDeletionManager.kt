package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.user.UserDataCleaner
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Single account deletion path for the app.
 *
 * Deleting a Firebase account and cleaning local user data must stay together;
 * fragments should not individually clear random stores after account deletion.
 */
class AccountDeletionManager private constructor(
    private val appContext: Context,
    private val firebaseAuth: FirebaseAuth,
    private val userDataCleaner: UserDataCleaner
) {

    data class DeleteResult(
        val accountDeleteError: Throwable? = null,
        val localCleanupResult: UserDataCleaner.CleanupResult = UserDataCleaner.CleanupResult.Success,
        val googleRevokeError: Throwable? = null,
        val firebaseSignOutError: Throwable? = null
    ) {
        val isAccountDeleted: Boolean
            get() = accountDeleteError == null

        val hasLocalCleanupErrors: Boolean
            get() = localCleanupResult.hasErrors
    }

    companion object {
        fun create(
            context: Context
        ): AccountDeletionManager {
            val appContext = context.applicationContext

            return AccountDeletionManager(
                appContext = appContext,
                firebaseAuth = Firebase.auth,
                userDataCleaner = UserDataCleaner.create(
                    context = appContext
                )
            )
        }
    }

    suspend fun deleteCurrentAccount(): DeleteResult {
        val user = firebaseAuth.currentUser
            ?: return DeleteResult(
                accountDeleteError = IllegalStateException(
                    "No authenticated user."
                )
            )

        val ownerUid = user.uid

        val accountDeleteError = runCatching {
            user.delete().awaitCompletion()
        }.exceptionOrNull()

        if (accountDeleteError != null) {
            return DeleteResult(
                accountDeleteError = accountDeleteError
            )
        }

        val localCleanupResult = userDataCleaner.clearLocalUserData(
            ownerUid = ownerUid,
            clearUserPreferences = true,
            stopSessionBoundServices = true
        )

        val googleRevokeError = runCatching {
            GoogleSignInClientFactory.create(
                appContext
            ).revokeAccess().awaitCompletion()
        }.exceptionOrNull()

        val firebaseSignOutError = runCatching {
            firebaseAuth.signOut()
        }.exceptionOrNull()

        return DeleteResult(
            localCleanupResult = localCleanupResult,
            googleRevokeError = googleRevokeError,
            firebaseSignOutError = firebaseSignOutError
        )
    }

    private suspend fun Task<Void>.awaitCompletion() {
        suspendCancellableCoroutine<Unit> { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) {
                    return@addOnCompleteListener
                }

                val exception = task.exception

                if (task.isSuccessful || exception == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(exception)
                }
            }
        }
    }
}
