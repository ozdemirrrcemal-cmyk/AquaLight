package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.user.UserDataOwnershipMigrator
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

/**
 * Single source of truth for authentication/session state.
 *
 * Local preferences are treated as a cache only. The real auth decision is
 * always based on FirebaseAuth.currentUser.
 */
class AuthSessionManager private constructor(
    private val firebaseAuth: FirebaseAuth,
    private val userPrefs: UserPreferencesManager,
    private val ownershipMigrator: UserDataOwnershipMigrator,
    private val ownerSessionCoordinator: OwnerSessionCoordinator
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
                ownershipMigrator = UserDataOwnershipMigrator.create(
                    appContext
                ),
                ownerSessionCoordinator = OwnerSessionCoordinator.create(
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
            userPrefs.logout()
            return SessionState.Unauthenticated
        }

        syncLocalSession(user)
        restoreCachedProfile(user)
        ownershipMigrator.migrateLegacyRecordsToOwner(
            ownerUid = user.uid
        )
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
        ownershipMigrator.migrateLegacyRecordsToOwner(
            ownerUid = user.uid
        )
        openOwnerSession(user.uid)
        return user.toSession()
    }

    suspend fun markLoggedOut() {
        userPrefs.logout()
    }

    private suspend fun openOwnerSession(
        ownerUid: String
    ) {
        when (
            val result = ownerSessionCoordinator.open(ownerUid)
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
