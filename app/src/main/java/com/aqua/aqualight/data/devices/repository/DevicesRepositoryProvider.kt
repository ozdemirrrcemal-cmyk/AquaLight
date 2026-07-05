package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.monitor.DeviceConnectivityObserver
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-level Devices V2 repository holder.
 *
 * All device UI surfaces observe the same repository instance. The repository is started from this
 * provider so device presence is not tied to a single screen such as DevicesFragment.
 */
object DevicesRepositoryProvider {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var instance: DevicesRepository? = null

    fun get(context: Context? = null): DevicesRepository = instance ?: synchronized(this) {
        instance ?: createRepository(context).also { repository ->
            repository.start(repositoryScope)
            instance = repository
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
