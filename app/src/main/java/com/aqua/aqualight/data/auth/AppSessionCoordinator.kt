package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single foreground authority for startup and authenticated-owner transitions.
 *
 * Splash never opens a session. MainActivity observes this coordinator and the coordinator
 * serializes every Firebase owner change through the foreground [OwnerRuntimeSession] path exposed
 * by [AuthSessionManager]. Device runtime foreground/background policy follows the same process
 * boundary, so UI never reaches concrete device repositories directly.
 */
class AppSessionCoordinator internal constructor(
    private val ownerProvider: AuthenticatedOwnerProvider,
    private val sessionResolver: ForegroundSessionResolver,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val foregroundRuntimeController: ForegroundRuntimeController =
        ForegroundRuntimeController(DevicesRepositoryProvider::setAppForeground)
) : AutoCloseable {

    sealed interface State {
        data object Starting : State

        data class Authenticated(
            val ownerUid: String
        ) : State

        data object Unauthenticated : State

        data class Failure(
            val ownerUid: String?,
            val error: Throwable
        ) : State
    }

    private val coordinatorJob = SupervisorJob()
    private val scope = CoroutineScope(coordinatorJob + dispatcher)
    private val transitionMutex = Mutex()
    private val started = AtomicBoolean(false)
    private val lifecycleLock = Any()

    private val _state = MutableStateFlow<State>(State.Starting)
    val state: StateFlow<State> = _state.asStateFlow()

    private var ownerObservationJob: Job? = null
    private var validationJob: Job? = null
    private var foregroundConsumerCount: Int = 0

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        ownerObservationJob = scope.launch {
            ownerProvider.state.collect {
                reconcileCurrentOwner()
            }
        }
    }

    /** Starts owner validation and device presence while the app has a foreground consumer. */
    fun enterForeground() {
        start()

        val becameForeground = synchronized(lifecycleLock) {
            foregroundConsumerCount += 1
            if (foregroundConsumerCount > 1) {
                false
            } else {
                if (validationJob?.isActive != true) {
                    validationJob = scope.launch {
                        while (isActive) {
                            validateRemoteSession()
                            delay(REMOTE_VALIDATION_INTERVAL_MILLIS)
                        }
                    }
                }
                true
            }
        }

        if (becameForeground) {
            foregroundRuntimeController.setForeground(true)
        }
    }

    fun leaveForeground() {
        val becameBackground = synchronized(lifecycleLock) {
            foregroundConsumerCount = (foregroundConsumerCount - 1).coerceAtLeast(0)
            if (foregroundConsumerCount == 0) {
                validationJob?.cancel()
                validationJob = null
                true
            } else {
                false
            }
        }

        if (becameBackground) {
            foregroundRuntimeController.setForeground(false)
        }
    }

    fun requestReconcile() {
        start()
        scope.launch {
            reconcileCurrentOwner()
        }
    }

    private suspend fun validateRemoteSession() {
        when (ownerProvider.validateCurrentOwner()) {
            is OwnerTokenValidationResult.Revoked,
            OwnerTokenValidationResult.Unauthenticated -> reconcileCurrentOwner()

            is OwnerTokenValidationResult.Valid,
            is OwnerTokenValidationResult.TransientFailure -> Unit
        }
    }

    private suspend fun reconcileCurrentOwner() {
        transitionMutex.withLock {
            var attempts = 0

            while (attempts < MAX_RECONCILE_ATTEMPTS) {
                attempts += 1
                val expectedOwnerUid = ownerProvider.currentOwnerUid()

                val resolution = try {
                    sessionResolver.resolve()
                } catch (error: Throwable) {
                    if (error is CancellationException) {
                        throw error
                    }

                    _state.value = State.Failure(
                        ownerUid = expectedOwnerUid,
                        error = error
                    )
                    return@withLock
                }

                val currentOwnerUid = ownerProvider.currentOwnerUid()
                if (currentOwnerUid != expectedOwnerUid) {
                    continue
                }

                when (resolution) {
                    is ForegroundSessionResolution.Authenticated -> {
                        if (resolution.ownerUid != currentOwnerUid) {
                            continue
                        }
                        _state.value = State.Authenticated(
                            ownerUid = resolution.ownerUid
                        )
                    }

                    ForegroundSessionResolution.Unauthenticated -> {
                        if (currentOwnerUid != null) {
                            continue
                        }
                        _state.value = State.Unauthenticated
                    }
                }

                return@withLock
            }

            _state.value = State.Failure(
                ownerUid = ownerProvider.currentOwnerUid(),
                error = IllegalStateException(
                    "Authentication owner changed repeatedly during startup reconciliation."
                )
            )
        }
    }

    override fun close() {
        val wasForeground = synchronized(lifecycleLock) {
            val active = foregroundConsumerCount > 0
            foregroundConsumerCount = 0
            active
        }
        if (wasForeground) {
            foregroundRuntimeController.setForeground(false)
        }
        ownerObservationJob?.cancel()
        validationJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val MAX_RECONCILE_ATTEMPTS = 3
        private const val REMOTE_VALIDATION_INTERVAL_MILLIS = 15L * 60L * 1_000L

        @Volatile
        private var INSTANCE: AppSessionCoordinator? = null

        fun create(
            context: Context
        ): AppSessionCoordinator {
            val appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSessionCoordinator(
                    ownerProvider = FirebaseAuthenticatedOwnerProvider.create(appContext),
                    sessionResolver = AuthSessionManagerForegroundResolver(
                        sessionManager = AuthSessionManager.create(appContext)
                    )
                ).also { coordinator ->
                    INSTANCE = coordinator
                }
            }
        }
    }
}

internal fun interface ForegroundRuntimeController {
    fun setForeground(isForeground: Boolean)
}

internal fun interface ForegroundSessionResolver {
    suspend fun resolve(): ForegroundSessionResolution
}

internal sealed interface ForegroundSessionResolution {
    data class Authenticated(
        val ownerUid: String
    ) : ForegroundSessionResolution

    data object Unauthenticated : ForegroundSessionResolution
}

private class AuthSessionManagerForegroundResolver(
    private val sessionManager: AuthSessionManager
) : ForegroundSessionResolver {

    override suspend fun resolve(): ForegroundSessionResolution {
        return when (
            val sessionState = sessionManager.currentSessionState()
        ) {
            is AuthSessionManager.SessionState.Authenticated -> {
                ForegroundSessionResolution.Authenticated(
                    ownerUid = sessionState.session.uid
                )
            }

            AuthSessionManager.SessionState.Unauthenticated -> {
                ForegroundSessionResolution.Unauthenticated
            }
        }
    }
}
