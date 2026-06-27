package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.store.DeviceKnownStore

/**
 * Process-level Devices V2 repository holder.
 *
 * The first UI integration steps need DevicesFragment and later detail screens to observe the
 * same in-memory device registry. Durable dependency injection can replace this provider later,
 * but UI code should still depend on [DevicesRepository], not on UDP/BLE/WebSocket internals.
 */
object DevicesRepositoryProvider {

    @Volatile
    private var instance: DevicesRepository? = null

    fun get(context: Context? = null): DevicesRepository = instance ?: synchronized(this) {
        instance ?: createRepository(context).also { repository ->
            instance = repository
        }
    }

    private fun createRepository(context: Context?): DevicesRepository {
        val appContext = context?.applicationContext
        return if (appContext != null) {
            DevicesRepository(
                knownStore = DeviceKnownStore(appContext),
                runtimeRepository = DeviceRuntimeRepository.withCredentialStore(appContext)
            )
        } else {
            DevicesRepository()
        }
    }
}
