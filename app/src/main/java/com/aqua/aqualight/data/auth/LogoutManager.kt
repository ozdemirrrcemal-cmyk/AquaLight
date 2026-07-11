package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Centralized logout flow for every sign-out path.
 *
 * Logout must tear down session-bound work before opening the auth graph again,
 * otherwise stale reminders, device scans, or pending navigation can survive the
 * user session boundary.
 */
class LogoutManager private constructor(
    private val appContext: Context,
    private val firebaseAuth: FirebaseAuth,
    private val userPrefs: UserPreferencesManager
) {

    data class LogoutResult(
        val serviceCleanupError: Throwable? = null,
        val googleSignOutError: Throwable? = null,
        val firebaseSignOutError: Throwable? = null,
        val preferenceCleanupError: Throwable? = null
    ) {
        val hasBlockingError: Boolean
            get() = preferenceCleanupError != null
    }

    companion object {
        fun create(
            context: Context
        ): LogoutManager {
            val appContext = context.applicationContext

            return LogoutManager(
                appContext = appContext,
                firebaseAuth = Firebase.auth,
                userPrefs = UserPreferencesManager.create(
                    appContext
                )
            )
        }
    }

    suspend fun logout(): LogoutResult {
        val ownerUid = firebaseAuth.currentUser?.uid.orEmpty()
        val serviceCleanupError = stopSessionBoundServices(
            ownerUid = ownerUid,
            cancelNotifications = true
        )

        val googleSignOutError = runCatching {
            GoogleSignInClientFactory.create(
                appContext
            ).signOut().awaitCompletion()
        }.exceptionOrNull()

        val firebaseSignOutError = runCatching {
            firebaseAuth.signOut()
        }.exceptionOrNull()

        val preferenceCleanupError = runCatching {
            userPrefs.logout()
        }.exceptionOrNull()

        return LogoutResult(
            serviceCleanupError = serviceCleanupError,
            googleSignOutError = googleSignOutError,
            firebaseSignOutError = firebaseSignOutError,
            preferenceCleanupError = preferenceCleanupError
        )
    }

    suspend fun cleanupAfterLocalSensitiveAction(
        cancelNotifications: Boolean = true
    ): LogoutResult {
        val ownerUid = firebaseAuth.currentUser?.uid.orEmpty()
        val serviceCleanupError = stopSessionBoundServices(
            ownerUid = ownerUid,
            cancelNotifications = cancelNotifications
        )

        val googleSignOutError = runCatching {
            GoogleSignInClientFactory.create(
                appContext
            ).signOut().awaitCompletion()
        }.exceptionOrNull()

        val firebaseSignOutError = runCatching {
            firebaseAuth.signOut()
        }.exceptionOrNull()

        val preferenceCleanupError = runCatching {
            userPrefs.logout()
        }.exceptionOrNull()

        return LogoutResult(
            serviceCleanupError = serviceCleanupError,
            googleSignOutError = googleSignOutError,
            firebaseSignOutError = firebaseSignOutError,
            preferenceCleanupError = preferenceCleanupError
        )
    }

    private suspend fun stopSessionBoundServices(
        ownerUid: String,
        cancelNotifications: Boolean
    ): Throwable? {
        return runCatching {
            SessionBoundServiceManager.stop(
                context = appContext,
                cancelNotifications = cancelNotifications,
                expectedOwnerUid = ownerUid.ifBlank { null }
            )
        }.fold(
            onSuccess = SessionBoundServiceManager.StopResult::exceptionOrNull,
            onFailure = { error -> error }
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
