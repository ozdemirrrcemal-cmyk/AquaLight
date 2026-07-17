package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

/**
 * Foreground authentication/session synchronizer.
 *
 * Local preferences are a cache only. Firebase is the authentication source of
 * truth. Heavy device runtime is opened exclusively through [OwnerRuntimeSession]
 * and this manager must never be used by workers or broadcast receivers.
 *
 * AquaLight has no released ownerless local-data contract. Session startup never
 * adopts records that do not already belong to the authenticated owner.
 */
class AuthSessionManager private constructor(
    private val firebaseAuth: FirebaseAuth,
    private val userPrefs: UserPreferencesManager,
    private val ownerRuntimeSession: OwnerRuntimeSession
) {

    data class Session(
        val uid: String,
        val email: String,
        val displayName: String,
        val photoUrl: String,
        val isEmailVerified: Boolean
    )

    sealed interface SessionState {
        data class Authenticated(
            val session: Session
        ) : SessionState

        data object Unauthenticated : SessionState
    }

    companion object {
        fun create(
            context: Context
        ): AuthSessionManager {
            val appContext = context.applicationContext

            return AuthSessionManager(
                firebaseAuth = Firebase.auth,
                userPrefs = UserPreferencesManager.create(
                    appContext
                ),
                ownerRuntimeSession = OwnerRuntimeSession.create(
                    appContext
                )
            )
        }
    }

    fun currentUser(): FirebaseUser? {
        return firebaseAuth.currentUser
    }

    fun isAuthenticated(): Boolean {
        return firebaseAuth.currentUser != null
    }

    suspend fun currentSessionState(): SessionState {
        val user = firebaseAuth.currentUser

        if (user == null) {
            closeResidualOwnerSession()
            userPrefs.logout()
            return SessionState.Unauthenticated
        }

        syncLocalSession(user)
        restoreCachedProfile(user)
        openOwnerSession(user.uid)

        return SessionState.Authenticated(
            session = user.toSession()
        )
    }

    suspend fun completeLogin(
        user: FirebaseUser
    ): Session {
        syncLocalSession(user)
        restoreCachedProfile(user)
        openOwnerSession(user.uid)
        return user.toSession()
    }

    suspend fun markLoggedOut() {
        userPrefs.logout()
    }

    private suspend fun closeResidualOwnerSession() {
        val snapshot = ownerRuntimeSession.snapshot()
        val ownerUid = snapshot.pendingOwnerUid ?: snapshot.activeOwnerUid ?: return

        ownerRuntimeSession.close(
            expectedOwnerUid = ownerUid,
            cancelNotifications = true
        )
    }

    private suspend fun openOwnerSession(
        ownerUid: String
    ) {
        when (
            val result = ownerRuntimeSession.open(ownerUid)
        ) {
            is OwnerSessionCoordinator.OpenResult.Active,
            is OwnerSessionCoordinator.OpenResult.AlreadyActive -> Unit

            is OwnerSessionCoordinator.OpenResult.Superseded -> {
                throw IllegalStateException(
                    "Owner session transition was superseded for ${result.ownerUid}."
                )
            }

            is OwnerSessionCoordinator.OpenResult.Failure -> {
                throw result.error
            }
        }
    }

    private suspend fun syncLocalSession(
        user: FirebaseUser
    ) {
        userPrefs.saveUserSession(
            uid = user.uid,
            isLoggedIn = true
        )
    }

    private suspend fun restoreCachedProfile(
        user: FirebaseUser
    ) {
        userPrefs.restoreProfileForLogin(
            ownerUid = user.uid,
            email = user.email.orEmpty(),
            fullName = user.displayName.orEmpty(),
            photoUrl = user.photoUrl?.toString().orEmpty()
        )
    }

    private fun FirebaseUser.toSession(): Session {
        return Session(
            uid = uid,
            email = email.orEmpty(),
            displayName = displayName.orEmpty(),
            photoUrl = photoUrl?.toString().orEmpty(),
            isEmailVerified = isEmailVerified
        )
    }
}
