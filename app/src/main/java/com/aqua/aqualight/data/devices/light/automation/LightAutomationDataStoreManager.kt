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

private val Context.lightAutomationDataStore: DataStore<LightAutomationPreferences> by dataStore(fileName = "light_automation.pb", serializer = LightAutomationSerializer)

class LightAutomationDataStoreManager(private val context: Context) {
    fun observeSettings(deviceId: Long): Flow<LightAutomationSettings> { val id = deviceId.coerceAtLeast(0L); return context.lightAutomationDataStore.data.map { prefs -> prefs.devicesList.firstOrNull { it.deviceId == id }?.let(LightAutomationProtoMapper::fromProto) ?: LightAutomationSettings.default(id) } }
    suspend fun getSettings(deviceId: Long): LightAutomationSettings = observeSettings(deviceId).first()
    suspend fun saveSettings(settings: LightAutomationSettings) = persist(settings, pendingDeviceSync = true)
    suspend fun markSynced(deviceId: Long) = persist(getSettings(deviceId), pendingDeviceSync = false)
    suspend fun updateMoonlight(deviceId: Long, moonlight: MoonlightSettings): LightAutomationSettings { val updated = getSettings(deviceId).copy(moonlight = moonlight); saveSettings(updated); return getSettings(deviceId) }
    suspend fun updateCloudSimulation(deviceId: Long, cloudSimulation: CloudSimulationSettings): LightAutomationSettings { val updated = getSettings(deviceId).copy(cloudSimulation = cloudSimulation); saveSettings(updated); return getSettings(deviceId) }
    private suspend fun persist(settings: LightAutomationSettings, pendingDeviceSync: Boolean) { val safe = LightAutomationProtoMapper.sanitize(settings.copy(updatedAt = System.currentTimeMillis(), pendingDeviceSync = pendingDeviceSync)); context.lightAutomationDataStore.updateData { prefs -> prefs.toBuilder().clearDevices().addAllDevices(prefs.devicesList.filterNot { it.deviceId == safe.deviceId }).addDevices(LightAutomationProtoMapper.toProto(safe)).build() } }
}
