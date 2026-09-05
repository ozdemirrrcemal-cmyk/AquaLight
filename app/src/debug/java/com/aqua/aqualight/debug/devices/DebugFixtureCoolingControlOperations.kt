package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Supplies deterministic Cooling V1 control telemetry for installable-debug catalog fixtures.
 *
 * Real devices always stay on the production control adapter. The fixture reports full applied fan
 * output so the live hero exercises its maximum-speed rotor path without changing production
 * telemetry semantics or hard-coding presentation behavior.
 */
internal class DebugFixtureCoolingControlOperations(
    private val delegate: DeviceCoolingControlOperations,
    fixtures: DebugDeviceFixtureCatalog
) : DeviceCoolingControlOperations {

    private val fixtureStates: Map<String, MutableStateFlow<DeviceCoolingControlSnapshot>> =
        fixtures.snapshots
            .filter { snapshot ->
                snapshot.product.family == DeviceFamily.COOLING &&
                    snapshot.product.productKey == DeviceCoolingV1Contract.PRODUCT_KEY
            }
            .associate { snapshot ->
                snapshot.deviceUid.value to MutableStateFlow(fullPowerFixtureSnapshot())
            }

    override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> {
        val fixtureState = fixtureState(deviceUid)
        return if (fixtureState == null) {
            delegate.observeControl(deviceUid)
        } else {
            fixtureState.map { snapshot -> DeviceCoolingControlResult.Available(snapshot) }
        }
    }

    override fun currentControl(deviceUid: String): DeviceCoolingControlResult {
        val fixtureState = fixtureState(deviceUid)
        return fixtureState?.value
            ?.let(DeviceCoolingControlResult::Available)
            ?: delegate.currentControl(deviceUid)
    }

    override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult {
        val fixtureState = fixtureState(deviceUid)
        return fixtureState?.value
            ?.let(DeviceCoolingControlResult::Available)
            ?: delegate.refreshControl(deviceUid)
    }

    override suspend fun setMode(
        deviceUid: String,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult {
        val fixtureState = fixtureState(deviceUid)
        return if (fixtureState == null) {
            delegate.setMode(deviceUid, mode)
        } else if (mode !in fixtureState.value.capabilities.supportedModes) {
            DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
        } else {
            val updated = fixtureState.value.copy(mode = mode)
            fixtureState.value = updated
            DeviceCoolingControlResult.Available(updated)
        }
    }

    override suspend fun setManualFanPercent(
        deviceUid: String,
        percent: Int
    ): DeviceCoolingControlResult {
        val fixtureState = fixtureState(deviceUid)
        val manualCapabilities = fixtureState?.value?.capabilities?.manualFan
        val validPercent = manualCapabilities != null &&
            percent in manualCapabilities.minimumPercent..manualCapabilities.maximumPercent
        return when {
            fixtureState == null -> delegate.setManualFanPercent(deviceUid, percent)
            !validPercent -> DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
            else -> {
                val updated = fixtureState.value.copy(manualFanPercent = percent)
                fixtureState.value = updated
                DeviceCoolingControlResult.Available(updated)
            }
        }
    }

    private fun fixtureState(deviceUid: String): MutableStateFlow<DeviceCoolingControlSnapshot>? =
        fixtureStates[deviceUid.trim()]
}

private fun fullPowerFixtureSnapshot(): DeviceCoolingControlSnapshot {
    val fullPowerPercent = DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM.toInt()
    return DeviceCoolingControlSnapshot(
        mode = DeviceCoolingControlMode.AUTOMATIC,
        manualFanPercent = fullPowerPercent,
        actualFanPercent = fullPowerPercent,
        tankTemperatureC = FIXTURE_TANK_TEMPERATURE_C,
        capabilities = FIXTURE_CONTROL_CAPABILITIES
    )
}

private val FIXTURE_CONTROL_CAPABILITIES = DeviceCoolingControlCapabilities(
    supportedModes = setOf(
        DeviceCoolingControlMode.AUTOMATIC,
        DeviceCoolingControlMode.MANUAL,
        DeviceCoolingControlMode.PROGRAM
    ),
    modeSelectionWritable = true,
    manualFan = DeviceCoolingManualFanCapabilities(
        minimumPercent = DeviceCoolingV1Contract.Limit.FAN_PERCENT_MINIMUM.toInt(),
        maximumPercent = DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM.toInt(),
        stepPercent = DeviceCoolingV1Contract.Limit.FAN_PERCENT_STEP.toInt(),
        writable = true
    )
)

private const val FIXTURE_TANK_TEMPERATURE_C = 25.6
