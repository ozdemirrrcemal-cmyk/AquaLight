package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.system.DeviceLightFanMode
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemCondition
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemFanSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemMutationResult
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemOperations
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemReadResult
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSettings
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemTemperaturePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Stable debug state for inspecting and interacting with the approved System design. */
internal class DebugFixtureLightSystemOperations(
    private val delegate: DeviceLightSystemOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceLightSystemOperations {

    private val fixtureStates = MutableStateFlow<Map<String, DeviceLightSystemSnapshot>>(emptyMap())

    override fun observe(deviceUid: String): Flow<DeviceLightSystemReadResult> =
        if (isLightFixture(deviceUid)) {
            ensureFixture(deviceUid)
            fixtureStates.map { states ->
                DeviceLightSystemReadResult.Available(states.getValue(deviceUid))
            }
        } else {
            delegate.observe(deviceUid)
        }

    override fun current(deviceUid: String): DeviceLightSystemReadResult =
        if (isLightFixture(deviceUid)) {
            DeviceLightSystemReadResult.Available(ensureFixture(deviceUid))
        } else {
            delegate.current(deviceUid)
        }

    override suspend fun refresh(deviceUid: String): DeviceLightSystemReadResult =
        if (isLightFixture(deviceUid)) current(deviceUid) else delegate.refresh(deviceUid)

    override suspend fun save(
        deviceUid: String,
        settings: DeviceLightSystemSettings
    ): DeviceLightSystemMutationResult {
        if (!isLightFixture(deviceUid)) return delegate.save(deviceUid, settings)
        val current = ensureFixture(deviceUid)
        val updated = current.copy(
            mode = settings.mode,
            startTemperatureCelsius = settings.startTemperatureCelsius,
            fullSpeedTemperatureCelsius = settings.fullSpeedTemperatureCelsius,
            protectionThresholdCelsius = settings.protectionThresholdCelsius
        )
        fixtureStates.value = fixtureStates.value + (deviceUid to updated)
        return DeviceLightSystemMutationResult.Success(updated)
    }

    private fun ensureFixture(deviceUid: String): DeviceLightSystemSnapshot =
        fixtureStates.value[deviceUid] ?: fixture(deviceUid).also { snapshot ->
            fixtureStates.value = fixtureStates.value + (deviceUid to snapshot)
        }

    private fun isLightFixture(deviceUid: String): Boolean =
        fixtures.rootSnapshot(deviceUid)?.family == OwnerDeviceFamily.LIGHT

    private fun fixture(deviceUid: String) = DeviceLightSystemSnapshot(
        deviceUid = deviceUid,
        temperatureCelsius = 42.8,
        condition = DeviceLightSystemCondition.NORMAL,
        sensorHealthy = true,
        fans = listOf(
            DeviceLightSystemFanSnapshot(key = "FAN_1", percent = 35, healthy = true),
            DeviceLightSystemFanSnapshot(key = "FAN_2", percent = 35, healthy = true)
        ),
        mode = DeviceLightFanMode.AUTOMATIC,
        startTemperatureCelsius = 30,
        fullSpeedTemperatureCelsius = 50,
        startTemperaturePolicy = DeviceLightSystemTemperaturePolicy(0, 80),
        fullSpeedTemperaturePolicy = DeviceLightSystemTemperaturePolicy(1, 90),
        protectionThresholdCelsius = 60,
        protectionThresholdPolicy = DeviceLightSystemTemperaturePolicy(50, 70),
        protectionActive = false
    )
}
