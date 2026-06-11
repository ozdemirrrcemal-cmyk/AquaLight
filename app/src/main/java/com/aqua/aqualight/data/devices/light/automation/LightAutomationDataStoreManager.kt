package com.aqua.aqualight.data.devices.light.automation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.light.automation.model.CloudSimulationSettings
import com.aqua.aqualight.data.devices.light.automation.model.LightAutomationSettings
import com.aqua.aqualight.data.devices.light.automation.model.MoonlightSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.lightAutomationDataStore: DataStore<LightAutomationPreferences> by dataStore(
    fileName = "light_automation.pb",
    serializer = LightAutomationSerializer
)

class LightAutomationDataStoreManager(
    private val context: Context
) {

    fun observeSettings(
        deviceId: Long
    ): Flow<LightAutomationSettings> {
        val id = deviceId.coerceAtLeast(0L)

        return context.lightAutomationDataStore.data.map { preferences ->
            preferences.devicesList
                .firstOrNull { device ->
                    device.deviceId == id
                }
                ?.let(LightAutomationProtoMapper::fromProto)
                ?: LightAutomationSettings.default(id)
        }
    }

    suspend fun getSettings(
        deviceId: Long
    ): LightAutomationSettings {
        return observeSettings(
            deviceId = deviceId
        ).first()
    }

    suspend fun saveSettings(
        settings: LightAutomationSettings
    ) {
        persist(
            settings = settings,
            pendingDeviceSync = true
        )
    }

    suspend fun markSynced(
        deviceId: Long
    ) {
        persist(
            settings = getSettings(
                deviceId = deviceId
            ),
            pendingDeviceSync = false
        )
    }

    suspend fun updateMoonlight(
        deviceId: Long,
        moonlight: MoonlightSettings
    ): LightAutomationSettings {
        val updated = getSettings(
            deviceId = deviceId
        ).copy(
            moonlight = moonlight
        )

        saveSettings(
            settings = updated
        )

        return getSettings(
            deviceId = deviceId
        )
    }

    suspend fun updateCloudSimulation(
        deviceId: Long,
        cloudSimulation: CloudSimulationSettings
    ): LightAutomationSettings {
        val updated = getSettings(
            deviceId = deviceId
        ).copy(
            cloudSimulation = cloudSimulation
        )

        saveSettings(
            settings = updated
        )

        return getSettings(
            deviceId = deviceId
        )
    }

    suspend fun clearAllSettings() {
        context.lightAutomationDataStore.updateData { preferences ->
            preferences.toBuilder()
                .clearDevices()
                .build()
        }
    }

    private suspend fun persist(
        settings: LightAutomationSettings,
        pendingDeviceSync: Boolean
    ) {
        val safe = LightAutomationProtoMapper.sanitize(
            settings.copy(
                updatedAt = System.currentTimeMillis(),
                pendingDeviceSync = pendingDeviceSync
            )
        )

        context.lightAutomationDataStore.updateData { preferences ->
            preferences.toBuilder()
                .clearDevices()
                .addAllDevices(
                    preferences.devicesList.filterNot { device ->
                        device.deviceId == safe.deviceId
                    }
                )
                .addDevices(
                    LightAutomationProtoMapper.toProto(safe)
                )
                .build()
        }
    }
}
