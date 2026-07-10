package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.monitor.DeviceConnectivityObserver
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-level repository holder.
 *
 * Session restart tears down old runtime/discovery state before loading the newly authenticated
 * owner's durable devices. This prevents one account's in-memory registry from surviving into the
 * next account on the same installation.
 */
object DevicesRepositoryProvider {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var instance: DevicesRepository? = null

    fun get(context: Context? = null): DevicesRepository {
        return repository(context).also { deviceRepository ->
            deviceRepository.start(repositoryScope)
        }
    }

    fun restartForCurrentOwner(
        context: Context
    ) {
        val deviceRepository = repository(context.applicationContext)

        repositoryScope.launch {
            deviceRepository.stopSession()
            deviceRepository.start(repositoryScope)
        }
    }

    suspend fun stopSession() {
        instance?.stopSession()
    }

    fun clearInMemoryRegistryIfCreated() {
        instance?.clearInMemoryRegistry()
    }

    private fun repository(context: Context?): DevicesRepository {
        return instance ?: synchronized(this) {
            instance ?: createRepository(context).also { created ->
                instance = created
            }
        }
    }

    private fun createRepository(context: Context?): DevicesRepository {
        val appContext = context?.applicationContext
        return if (appContext != null) {
            DevicesRepository(
                knownStore = DeviceKnownStore(appContext),
                runtimeRepository = DeviceRuntimeRepository.withCredentialStore(appContext),
                connectivityObserver = DeviceConnectivityObserver(appContext)
            )
        } else {
            DevicesRepository()
        }
    }
}
