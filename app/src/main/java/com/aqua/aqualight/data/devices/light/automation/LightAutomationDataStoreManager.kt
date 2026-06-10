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
    suspend fun saveSettings(settings: LightAutomationSettings) { val safe = LightAutomationProtoMapper.sanitize(settings.copy(updatedAt = System.currentTimeMillis(), pendingDeviceSync = true)); context.lightAutomationDataStore.updateData { prefs -> prefs.toBuilder().clearDevices().addAllDevices(prefs.devicesList.filterNot { it.deviceId == safe.deviceId }).addDevices(LightAutomationProtoMapper.toProto(safe)).build() } }
    suspend fun updateMoonlight(deviceId: Long, moonlight: MoonlightSettings) = saveSettings(getSettings(deviceId).copy(moonlight = moonlight))
    suspend fun updateCloudSimulation(deviceId: Long, cloudSimulation: CloudSimulationSettings) = saveSettings(getSettings(deviceId).copy(cloudSimulation = cloudSimulation))
}
