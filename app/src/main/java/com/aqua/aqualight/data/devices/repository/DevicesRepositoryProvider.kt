package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.monitor.DeviceConnectivityObserver
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * Process-level repository holder.
 *
 * A real owner change tears down old runtime/discovery state before loading the newly authenticated
 * owner's durable devices. Production callers must provide an Android context; there is no
 * persistence-free singleton fallback.
 */
object DevicesRepositoryProvider {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionState = OwnerSessionStateMachine()

    @Volatile
    private var instance: DevicesRepository? = null

    @Volatile
    private var sessionTransitionJob: Job? = null

    fun get(
        context: Context
    ): DevicesRepository {
        return repository(context.applicationContext).also { deviceRepository ->
            deviceRepository.start(repositoryScope)
        }
    }

    fun restartForCurrentOwner(
        context: Context
    ) {
        val decision = sessionState.start(
            ownerUid = UserDataScope.currentUid()
        ) ?: return
        val deviceRepository = repository(context.applicationContext)

        if (!decision.requiresRestart) {
            if (sessionTransitionJob?.isActive != true) {
                deviceRepository.start(repositoryScope)
            }
            return
        }

        synchronized(this) {
            sessionTransitionJob?.cancel()

            val transitionJob = repositoryScope.launch {
                deviceRepository.stopSession()

                val firebaseOwnerStillMatches = UserDataScope.normalizeOwnerUid(
                    UserDataScope.currentUid()
                ) == decision.ownerUid

                if (
                    firebaseOwnerStillMatches &&
                    sessionState.isCurrent(
                        ownerUid = decision.ownerUid,
                        expectedGeneration = decision.generation
                    )
                ) {
                    deviceRepository.start(repositoryScope)
                }
            }

            sessionTransitionJob = transitionJob
            transitionJob.invokeOnCompletion {
                synchronized(this@DevicesRepositoryProvider) {
                    if (sessionTransitionJob === transitionJob) {
                        sessionTransitionJob = null
                    }
                }
            }
        }
    }

    suspend fun stopSession() {
        sessionState.stop()

        val transitionJob = synchronized(this) {
            sessionTransitionJob.also {
                sessionTransitionJob = null
            }
        }

        transitionJob?.cancelAndJoin()
        instance?.stopSession()
    }

    private fun repository(
        context: Context
    ): DevicesRepository {
        return instance ?: synchronized(this) {
            instance ?: DevicesRepository(
                knownStore = DeviceKnownStore(context.applicationContext),
                runtimeRepository = DeviceRuntimeRepository.withCredentialStore(
                    context.applicationContext
                ),
                connectivityObserver = DeviceConnectivityObserver(
                    context.applicationContext
                )
            ).also { created ->
                instance = created
            }
        }
    }
}
