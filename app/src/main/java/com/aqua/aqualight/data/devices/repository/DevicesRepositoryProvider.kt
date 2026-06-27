package com.aqua.aqualight.data.devices.repository

/**
 * Process-level Devices V2 repository holder.
 *
 * The first UI integration step needs DevicesFragment and later detail screens to observe the
 * same in-memory device registry. Durable dependency injection can replace this provider later,
 * but UI code should still depend on [DevicesRepository], not on UDP/BLE/WebSocket internals.
 */
object DevicesRepositoryProvider {

    @Volatile
    private var instance: DevicesRepository? = null

    fun get(): DevicesRepository = instance ?: synchronized(this) {
        instance ?: DevicesRepository().also { instance = it }
    }
}
