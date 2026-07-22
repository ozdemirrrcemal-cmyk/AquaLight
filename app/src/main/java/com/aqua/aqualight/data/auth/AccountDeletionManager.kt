package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.user.CloudUserDataCleaner
import com.aqua.aqualight.data.user.UserDataCleaner
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val cloudUserDataCleaner: CloudUserDataCleaner,
    private val checkpointStore: AccountDeletionCheckpointStore
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
        private val DELETION_MUTEX = Mutex()

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
                cloudUserDataCleaner = CloudUserDataCleaner.create(),
                checkpointStore = EncryptedAccountDeletionCheckpointStore(appContext)
            )
        }
    }

    suspend fun deleteCurrentAccount(): DeleteResult {
        return DELETION_MUTEX.withLock {
            deleteOrResumeLocked(requirePending = false)
                ?: DeleteResult(
                    accountDeleteError = IllegalStateException(
                        "No authenticated user."
                    )
                )
        }
    }

    /** Best-effort process-start recovery; null means that no deletion was pending. */
    suspend fun resumePendingDeletion(): DeleteResult? {
        return DELETION_MUTEX.withLock {
            deleteOrResumeLocked(requirePending = true)
        }
    }

    private suspend fun deleteOrResumeLocked(
        requirePending: Boolean
    ): DeleteResult? {
        var checkpoint = checkpointStore.read()
        if (checkpoint == null && requirePending) return null

        val currentUser = firebaseAuth.currentUser
        val ownerUid = checkpoint?.ownerUid
            ?: currentUser?.uid
            ?: return DeleteResult(
                accountDeleteError = IllegalStateException(
                    "No authenticated user."
                )
            )

        if (currentUser != null && currentUser.uid != ownerUid) {
            return DeleteResult(
                accountDeleteError = IllegalStateException(
                    "Pending account deletion belongs to a different authenticated owner."
                )
            )
        }

        if (checkpoint == null) {
            checkpoint = runCatching {
                checkpointStore.begin(ownerUid)
            }.getOrElse { error ->
                return DeleteResult(accountDeleteError = error)
            }
        }

        if (checkpoint.stage == AccountDeletionCheckpoint.Stage.STARTED) {
            val cloudCleanupResult = cloudUserDataCleaner.clearCloudUserData(
                ownerUid = ownerUid
            )

            if (cloudCleanupResult.hasError) {
                return DeleteResult(
                    accountDeleteError = cloudCleanupResult.error
                        ?: IllegalStateException("Cloud user data cleanup failed."),
                    cloudCleanupResult = cloudCleanupResult
                )
            }

            checkpoint = runCatching {
                checkpointStore.advance(
                    ownerUid,
                    AccountDeletionCheckpoint.Stage.CLOUD_CLEARED
                )
            }.getOrElse { error ->
                return DeleteResult(
                    accountDeleteError = error,
                    cloudCleanupResult = cloudCleanupResult
                )
            }
        }

        if (checkpoint.stage == AccountDeletionCheckpoint.Stage.CLOUD_CLEARED) {
            checkpoint = runCatching {
                checkpointStore.advance(
                    ownerUid,
                    AccountDeletionCheckpoint.Stage.AUTH_DELETE_REQUESTED
                )
            }.getOrElse { error ->
                return DeleteResult(accountDeleteError = error)
            }
        }

        if (checkpoint.stage == AccountDeletionCheckpoint.Stage.AUTH_DELETE_REQUESTED) {
            val user = firebaseAuth.currentUser

            if (user == null) {
                // The process may have died after Firebase confirmed deletion but before the local
                // checkpoint advanced. No current Firebase user is the safe completion signal here.
                checkpoint = runCatching {
                    checkpointStore.advance(
                        ownerUid,
                        AccountDeletionCheckpoint.Stage.ACCOUNT_DELETED
                    )
                }.getOrElse { error ->
                    return DeleteResult(accountDeleteError = error)
                }
            } else {
                val accountDeleteError = runCatching {
                    user.delete().awaitCompletion()
                }.exceptionOrNull()

                if (accountDeleteError != null) {
                    return DeleteResult(accountDeleteError = accountDeleteError)
                }

                checkpoint = runCatching {
                    checkpointStore.advance(
                        ownerUid,
                        AccountDeletionCheckpoint.Stage.ACCOUNT_DELETED
                    )
                }.getOrElse { error ->
                    return DeleteResult(accountDeleteError = error)
                }
            }
        }

        check(checkpoint.stage == AccountDeletionCheckpoint.Stage.ACCOUNT_DELETED) {
            "Account deletion reached an unsupported recovery stage."
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

            val googleRevokeError = runCatching {
                GoogleSignInClientFactory.create(
                    appContext
                ).revokeAccess()
                    .awaitCompletion()
            }.exceptionOrNull()

            val firebaseSignOutError = runCatching {
                firebaseAuth.signOut()
            }.exceptionOrNull()

            if (!localCleanupResult.hasErrors && firebaseSignOutError == null) {
                runCatching {
                    checkpointStore.clear(ownerUid)
                }.onFailure { checkpointError ->
                    return@withContext DeleteResult(
                        cloudCleanupResult = CloudUserDataCleaner.CleanupResult.Success,
                        localCleanupResult = localCleanupResult,
                        googleRevokeError = googleRevokeError,
                        firebaseSignOutError = checkpointError
                    )
                }
            }

            DeleteResult(
                cloudCleanupResult = CloudUserDataCleaner.CleanupResult.Success,
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
