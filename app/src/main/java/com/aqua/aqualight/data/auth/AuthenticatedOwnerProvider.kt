package com.aqua.aqualight.data.auth

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.auth
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Lightweight process-level authentication boundary.
 *
 * Reading this provider never opens device repositories, UDP discovery, WebSocket
 * connections or any other owner runtime. It is therefore safe for workers and
 * broadcast receivers that only need the currently authenticated owner UID.
 */
interface AuthenticatedOwnerProvider {

    val state: StateFlow<AuthenticatedOwnerState>

    fun currentOwner(): AuthenticatedOwner?

    fun currentOwnerUid(): String? = currentOwner()?.uid

    /**
     * Forces Firebase to validate the current ID token without opening app runtime.
     * Only credential/user invalidation signs the user out. Transient network
     * failures are reported to the caller and do not destroy an offline session.
     */
    suspend fun validateCurrentOwner(): OwnerTokenValidationResult
}

data class AuthenticatedOwner(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String,
    val isEmailVerified: Boolean
)

sealed interface AuthenticatedOwnerState {
    data class Authenticated(
        val owner: AuthenticatedOwner
    ) : AuthenticatedOwnerState

    data object Unauthenticated : AuthenticatedOwnerState
}

sealed interface OwnerTokenValidationResult {
    data class Valid(
        val ownerUid: String
    ) : OwnerTokenValidationResult

    data object Unauthenticated : OwnerTokenValidationResult

    data class Revoked(
        val ownerUid: String,
        val error: Throwable
    ) : OwnerTokenValidationResult

    data class TransientFailure(
        val ownerUid: String,
        val error: Throwable
    ) : OwnerTokenValidationResult
}

class FirebaseAuthenticatedOwnerProvider private constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthenticatedOwnerProvider, AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val _state = MutableStateFlow(readState(firebaseAuth))

    override val state: StateFlow<AuthenticatedOwnerState> = _state.asStateFlow()

    private val idTokenListener = FirebaseAuth.IdTokenListener { auth ->
        if (!closed.get()) {
            _state.value = readState(auth)
        }
    }

    init {
        firebaseAuth.addIdTokenListener(idTokenListener)
    }

    override fun currentOwner(): AuthenticatedOwner? {
        if (closed.get()) {
            return null
        }
        return firebaseAuth.currentUser?.toAuthenticatedOwner()
    }

    override suspend fun validateCurrentOwner(): OwnerTokenValidationResult {
        if (closed.get()) {
            return OwnerTokenValidationResult.Unauthenticated
        }

        val user = firebaseAuth.currentUser
            ?: return OwnerTokenValidationResult.Unauthenticated
        val ownerUid = user.uid

        return try {
            user.getIdToken(true).awaitTokenResult()

            val currentUid = firebaseAuth.currentUser?.uid
            if (currentUid == ownerUid) {
                OwnerTokenValidationResult.Valid(ownerUid)
            } else {
                OwnerTokenValidationResult.Unauthenticated
            }
        } catch (error: Throwable) {
            if (error.isTerminalAuthenticationFailure()) {
                runCatching {
                    firebaseAuth.signOut()
                }
                _state.value = AuthenticatedOwnerState.Unauthenticated
                OwnerTokenValidationResult.Revoked(
                    ownerUid = ownerUid,
                    error = error
                )
            } else {
                OwnerTokenValidationResult.TransientFailure(
                    ownerUid = ownerUid,
                    error = error
                )
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        firebaseAuth.removeIdTokenListener(idTokenListener)
        _state.value = AuthenticatedOwnerState.Unauthenticated
    }

    private fun Throwable.isTerminalAuthenticationFailure(): Boolean {
        return this is FirebaseAuthInvalidUserException ||
            this is FirebaseAuthInvalidCredentialsException
    }

    companion object {
        @Volatile
        private var INSTANCE: FirebaseAuthenticatedOwnerProvider? = null

        fun create(
            context: Context
        ): FirebaseAuthenticatedOwnerProvider {
            context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseAuthenticatedOwnerProvider(
                    firebaseAuth = Firebase.auth
                ).also { provider ->
                    INSTANCE = provider
                }
            }
        }

        internal fun createForTests(
            firebaseAuth: FirebaseAuth
        ): FirebaseAuthenticatedOwnerProvider {
            return FirebaseAuthenticatedOwnerProvider(firebaseAuth)
        }

        private fun readState(
            firebaseAuth: FirebaseAuth
        ): AuthenticatedOwnerState {
            val owner = firebaseAuth.currentUser?.toAuthenticatedOwner()
            return if (owner == null) {
                AuthenticatedOwnerState.Unauthenticated
            } else {
                AuthenticatedOwnerState.Authenticated(owner)
            }
        }
    }
}

private fun com.google.firebase.auth.FirebaseUser.toAuthenticatedOwner(): AuthenticatedOwner {
    return AuthenticatedOwner(
        uid = uid,
        email = email.orEmpty(),
        displayName = displayName.orEmpty(),
        photoUrl = photoUrl?.toString().orEmpty(),
        isEmailVerified = isEmailVerified
    )
}

private suspend fun Task<GetTokenResult>.awaitTokenResult(): GetTokenResult {
    return suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) {
                return@addOnCompleteListener
            }

            val error = task.exception
            val result = task.result

            when {
                task.isSuccessful && result != null -> continuation.resume(result)
                error != null -> continuation.resumeWithException(error)
                else -> continuation.resumeWithException(
                    IllegalStateException("Firebase token validation completed without a result.")
                )
            }
        }
    }
}
