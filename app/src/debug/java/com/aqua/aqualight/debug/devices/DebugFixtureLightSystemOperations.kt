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
        fixtures.rootSnapshot(deviceUid)?.let { root ->
            root.family == OwnerDeviceFamily.LIGHT &&
                LIGHT_FAN_CONTROL in root.supportedFeatures &&
                LIGHT_TEMPERATURE_PROTECTION in root.supportedFeatures
        } == true

    private fun fixture(deviceUid: String) = DeviceLightSystemSnapshot(
        deviceUid = deviceUid,
        temperatureCelsius = FIXTURE_TEMPERATURE_CELSIUS,
        condition = DeviceLightSystemCondition.NORMAL,
        sensorHealthy = true,
        fans = listOf(
            DeviceLightSystemFanSnapshot(
                key = "FAN_1",
                percent = FIXTURE_FAN_PERCENT,
                healthy = true
            ),
            DeviceLightSystemFanSnapshot(
                key = "FAN_2",
                percent = FIXTURE_FAN_PERCENT,
                healthy = true
            )
        ),
        mode = DeviceLightFanMode.AUTOMATIC,
        startTemperatureCelsius = FIXTURE_START_TEMPERATURE_CELSIUS,
        fullSpeedTemperatureCelsius = FIXTURE_FULL_SPEED_TEMPERATURE_CELSIUS,
        startTemperaturePolicy = DeviceLightSystemTemperaturePolicy(
            FIXTURE_START_TEMPERATURE_MINIMUM,
            FIXTURE_START_TEMPERATURE_MAXIMUM
        ),
        fullSpeedTemperaturePolicy = DeviceLightSystemTemperaturePolicy(
            FIXTURE_FULL_SPEED_TEMPERATURE_MINIMUM,
            FIXTURE_FULL_SPEED_TEMPERATURE_MAXIMUM
        ),
        protectionThresholdCelsius = FIXTURE_PROTECTION_THRESHOLD_CELSIUS,
        protectionThresholdPolicy = DeviceLightSystemTemperaturePolicy(
            FIXTURE_PROTECTION_TEMPERATURE_MINIMUM,
            FIXTURE_PROTECTION_TEMPERATURE_MAXIMUM
        ),
        protectionActive = false,
        firmwareWriteAuthoritative = true
    )
}

private const val FIXTURE_TEMPERATURE_CELSIUS = 42.8
private const val FIXTURE_FAN_PERCENT = 35
private const val FIXTURE_START_TEMPERATURE_CELSIUS = 30
private const val FIXTURE_FULL_SPEED_TEMPERATURE_CELSIUS = 50
private const val FIXTURE_START_TEMPERATURE_MINIMUM = 0
private const val FIXTURE_START_TEMPERATURE_MAXIMUM = 80
private const val FIXTURE_FULL_SPEED_TEMPERATURE_MINIMUM = 1
private const val FIXTURE_FULL_SPEED_TEMPERATURE_MAXIMUM = 90
private const val FIXTURE_PROTECTION_THRESHOLD_CELSIUS = 60
private const val FIXTURE_PROTECTION_TEMPERATURE_MINIMUM = 50
private const val FIXTURE_PROTECTION_TEMPERATURE_MAXIMUM = 70
private const val LIGHT_FAN_CONTROL = "LIGHT_FAN_CONTROL"
private const val LIGHT_TEMPERATURE_PROTECTION = "LIGHT_TEMPERATURE_PROTECTION"
