package com.aqua.aqualight.data.devices

import com.aqua.aqualight.data.UserPreferencesManager
import kotlinx.coroutines.flow.first

object DevicesLegacyMigrationManager {

    suspend fun migrateIfNeeded(
        legacyUserPrefs: UserPreferencesManager,
        devicesStore: DevicesDataStoreManager
    ) {
        val legacyDevices = legacyUserPrefs.devicesFlow.first()

        if (legacyDevices.isEmpty()) {
            return
        }

        val devicesToImport = legacyDevices.map { device ->
            DevicesDataStoreManager.DeviceInfoUi(
                id = device.id,
                aquaName = device.aquaName,
                name = device.name,
                ip = device.ip,
                serial = device.serial,
                firmwareBuild = device.firmwareBuild,
                lastSeenMillis = device.lastSeenMillis,
                tankId = device.tankId
            )
        }

        devicesStore.importDevicesIfEmpty(devicesToImport)
    }
}