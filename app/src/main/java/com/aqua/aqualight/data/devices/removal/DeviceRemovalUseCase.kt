package com.aqua.aqualight.data.devices.removal

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import kotlinx.coroutines.flow.first

class DeviceRemovalUseCase private constructor(
    private val devicesStore: DevicesDataStoreManager,
    private val credentialStore: DeviceCredentialStore
) {

    suspend fun removeDevices(
        ids: Set<Long>
    ) {
        if (ids.isEmpty()) {
            return
        }

        val deviceUids =
            devicesStore.devicesFlow.first()
                .asSequence()
                .filter { device ->
                    device.id in ids
                }
                .map { device ->
                    device.deviceUid.trim()
                }
                .filter { deviceUid ->
                    deviceUid.isNotBlank()
                }
                .distinct()
                .toList()

        deviceUids.forEach { deviceUid ->
            credentialStore.clearToken(
                DeviceUid(deviceUid)
            )
        }

        devicesStore.deleteDevices(
            ids = ids
        )
    }

    companion object {

        fun create(
            context: Context,
            devicesStore: DevicesDataStoreManager
        ): DeviceRemovalUseCase {
            return DeviceRemovalUseCase(
                devicesStore = devicesStore,
                credentialStore = DeviceCredentialStore(
                    context.applicationContext
                )
            )
        }
    }
}
