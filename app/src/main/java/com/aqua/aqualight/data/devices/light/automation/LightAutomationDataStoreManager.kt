package com.aqua.aqualight.data.devices.light.automation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.light.automation.model.CloudSimulationSettings
import com.aqua.aqualight.data.devices.light.automation.model.LightAutomationSettings
import com.aqua.aqualight.data.devices.light.automation.model.MoonlightSettings
import com.aqua.aqualight.data.user.UserDataScope
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
                    device.deviceId == id && device.belongsToCurrentUser()
                }
                ?.let(LightAutomationProtoMapper::fromProto)
                ?: LightAutomationSettings.default(
                    deviceId = id,
                    ownerUid = UserDataScope.currentUid()
                )
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

    suspend fun clearAllSettings(
        ownerUid: String? = null
    ) {
        val targetOwnerUid = ownerUid.orCurrentOwnerUidOrReturn()

        context.lightAutomationDataStore.updateData { preferences ->
            val remainingDevices = preferences.devicesList.filterNot { device ->
                device.belongsToOwner(targetOwnerUid)
            }

            preferences.toBuilder()
                .clearDevices()
                .addAllDevices(remainingDevices)
                .build()
        }
    }

    suspend fun assignLegacySettingsToOwner(
        ownerUid: String
    ) {
        val targetOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)

        if (targetOwnerUid.isBlank()) {
            return
        }

        context.lightAutomationDataStore.updateData { preferences ->
            val updatedDevices = preferences.devicesList.map { device ->
                if (device.ownerUid.isBlank()) {
                    device.toBuilder()
                        .setOwnerUid(targetOwnerUid)
                        .build()
                } else {
                    device
                }
            }

            preferences.toBuilder()
                .clearDevices()
                .addAllDevices(updatedDevices)
                .build()
        }
    }

    private suspend fun persist(
        settings: LightAutomationSettings,
        pendingDeviceSync: Boolean
    ) {
        val ownerUid = settings.ownerUid.ifBlank {
            UserDataScope.requireCurrentUid()
        }

        val safe = LightAutomationProtoMapper.sanitize(
            settings.copy(
                ownerUid = ownerUid,
                updatedAt = System.currentTimeMillis(),
                pendingDeviceSync = pendingDeviceSync
            )
        )

        context.lightAutomationDataStore.updateData { preferences ->
            preferences.toBuilder()
                .clearDevices()
                .addAllDevices(
                    preferences.devicesList.filterNot { device ->
                        device.deviceId == safe.deviceId &&
                            device.belongsToOwner(safe.ownerUid)
                    }
                )
                .addDevices(
                    LightAutomationProtoMapper.toProto(safe)
                )
                .build()
        }
    }
    private fun String?.orCurrentOwnerUidOrReturn(): String {
        val explicitOwnerUid = UserDataScope.normalizeOwnerUid(this)

        if (explicitOwnerUid.isNotBlank()) {
            return explicitOwnerUid
        }

        return UserDataScope.currentUid()
    }

    private fun LightAutomationDeviceSettings.belongsToCurrentUser(): Boolean {
        return UserDataScope.belongsToCurrentUser(
            recordOwnerUid = ownerUid
        )
    }

    private fun LightAutomationDeviceSettings.belongsToOwner(
        ownerUid: String
    ): Boolean {
        return UserDataScope.belongsToOwner(
            recordOwnerUid = this.ownerUid,
            ownerUid = ownerUid
        )
    }

}
