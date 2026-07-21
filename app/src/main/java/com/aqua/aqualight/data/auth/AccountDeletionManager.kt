package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.user.CloudUserDataCleaner
import com.aqua.aqualight.data.user.UserDataCleaner
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Single account deletion path for the app.
 *
 * Deleting a Firebase account and cleaning user data must stay together;
 * fragments should not individually clear random stores after account deletion.
 */
class AccountDeletionManager private constructor(
    private val appContext: Context,
    private val firebaseAuth: FirebaseAuth,
    private val userDataCleaner: UserDataCleaner,
    private val cloudUserDataCleaner: CloudUserDataCleaner
) {

    data class DeleteResult(
        val accountDeleteError: Throwable? = null,
        val cloudCleanupResult: CloudUserDataCleaner.CleanupResult =
            CloudUserDataCleaner.CleanupResult.Success,
        val localCleanupResult: UserDataCleaner.CleanupResult =
            UserDataCleaner.CleanupResult.Success,
        val googleRevokeError: Throwable? = null,
        val firebaseSignOutError: Throwable? = null
    ) {
        val isAccountDeleted: Boolean
            get() = accountDeleteError == null

        val hasCloudCleanupError: Boolean
            get() = cloudCleanupResult.hasError

        val hasLocalCleanupErrors: Boolean
            get() = localCleanupResult.hasErrors

        val hasPostDeleteCleanupErrors: Boolean
            get() = hasLocalCleanupErrors ||
                googleRevokeError != null ||
                firebaseSignOutError != null
    }

    companion object {
        private const val FIREBASE_ACCOUNT_DELETE_TIMEOUT_MILLIS = 15_000L
        private const val GOOGLE_REVOKE_TIMEOUT_MILLIS = 5_000L

        fun create(
            context: Context
        ): AccountDeletionManager {
            val appContext =
                context.applicationContext

            return AccountDeletionManager(
                appContext = appContext,
                firebaseAuth = Firebase.auth,
                userDataCleaner = UserDataCleaner.create(
                    context = appContext
                ),
                cloudUserDataCleaner = CloudUserDataCleaner.create()
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
        val revokeGoogleAccess = shouldRevokeGoogleAccess(
            user.providerData.map { provider -> provider.providerId }
        )

        val cloudCleanupResult =
            cloudUserDataCleaner.clearCloudUserData(
                ownerUid = ownerUid
            )

        if (cloudCleanupResult.hasError) {
            return DeleteResult(
                accountDeleteError = cloudCleanupResult.error
                    ?: IllegalStateException(
                        "Cloud user data cleanup failed."
                    ),
                cloudCleanupResult = cloudCleanupResult
            )
        }

        val accountDeleteError = runBoundedOperation(
            timeoutMillis = FIREBASE_ACCOUNT_DELETE_TIMEOUT_MILLIS
        ) {
            user.delete().awaitCompletion()
        }

        if (accountDeleteError != null) {
            return DeleteResult(
                accountDeleteError = accountDeleteError,
                cloudCleanupResult = cloudCleanupResult
            )
        }

        return withContext(NonCancellable) {
            val firstLocalCleanupResult = userDataCleaner.clearLocalUserData(
                ownerUid = ownerUid,
                clearUserPreferences = true,
                stopSessionBoundServices = true
            )
            val localCleanupResult = if (firstLocalCleanupResult.hasErrors) {
                userDataCleaner.clearLocalUserData(
                    ownerUid = ownerUid,
                    clearUserPreferences = true,
                    stopSessionBoundServices = true
                )
            } else {
                firstLocalCleanupResult
            }

            val googleRevokeError = if (revokeGoogleAccess) {
                runBoundedOperation(
                    timeoutMillis = GOOGLE_REVOKE_TIMEOUT_MILLIS
                ) {
                    GoogleSignInClientFactory.create(
                        appContext
                    ).revokeAccess()
                        .awaitCompletion()
                }
            } else {
                null
            }

            val firebaseSignOutError = runCatching {
                firebaseAuth.signOut()
            }.exceptionOrNull()

            DeleteResult(
                cloudCleanupResult = cloudCleanupResult,
                localCleanupResult = localCleanupResult,
                googleRevokeError = googleRevokeError,
                firebaseSignOutError = firebaseSignOutError
            )
        }
    }

    private suspend fun Task<Void>.awaitCompletion() {
        suspendCancellableCoroutine<Unit> { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) {
                    return@addOnCompleteListener
                }

                val exception =
                    task.exception

                if (task.isSuccessful && exception == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        exception ?: IllegalStateException(
                            "Firebase task failed."
                        )
                    )
                }
            }
        }
    }
}

internal fun shouldRevokeGoogleAccess(providerIds: Collection<String>): Boolean {
    return GoogleAuthProvider.PROVIDER_ID in providerIds
}

private suspend fun runBoundedOperation(
    timeoutMillis: Long,
    operation: suspend () -> Unit
): Throwable? {
    return try {
        withTimeout(timeoutMillis) {
            operation()
        }
        null
    } catch (timeout: TimeoutCancellationException) {
        timeout
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        error
    }
}
