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

/** Supplies fixture-owned Cooling control state while preserving production operations for real UIDs. */
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
                snapshot.deviceUid.value to MutableStateFlow(fixtureSnapshot())
            }

    override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> {
        val fixtureState = fixtureState(deviceUid)
        return fixtureState?.map { snapshot -> DeviceCoolingControlResult.Available(snapshot) }
            ?: delegate.observeControl(deviceUid)
    }

    override fun currentControl(deviceUid: String): DeviceCoolingControlResult =
        fixtureState(deviceUid)?.value
            ?.let(DeviceCoolingControlResult::Available)
            ?: delegate.currentControl(deviceUid)

    override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult =
        fixtureState(deviceUid)?.value
            ?.let(DeviceCoolingControlResult::Available)
            ?: delegate.refreshControl(deviceUid)

    override suspend fun setMode(
        deviceUid: String,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult {
        val fixtureState = fixtureState(deviceUid)
        return when {
            fixtureState == null -> delegate.setMode(deviceUid, mode)
            mode !in fixtureState.value.capabilities.supportedModes ->
                DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
            else -> fixtureState.update { snapshot -> snapshot.copy(mode = mode) }
        }
    }

    override suspend fun setManualFanPercent(
        deviceUid: String,
        percent: Int
    ): DeviceCoolingControlResult {
        val fixtureState = fixtureState(deviceUid)
        val capabilities = fixtureState?.value?.capabilities?.manualFan
        return when {
            fixtureState == null -> delegate.setManualFanPercent(deviceUid, percent)
            capabilities == null ||
                percent !in capabilities.minimumPercent..capabilities.maximumPercent ->
                DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
            else -> fixtureState.update { snapshot ->
                snapshot.copy(
                    manualFanPercent = percent,
                    // Manual writes are discrete, while observed firmware output uses the
                    // continuous runtime representation shared with production telemetry.
                    actualFanPercent = percent.toDouble()
                )
            }
        }
    }

    private fun fixtureState(deviceUid: String): MutableStateFlow<DeviceCoolingControlSnapshot>? =
        fixtureStates[deviceUid.trim()]

    private fun MutableStateFlow<DeviceCoolingControlSnapshot>.update(
        transform: (DeviceCoolingControlSnapshot) -> DeviceCoolingControlSnapshot
    ): DeviceCoolingControlResult.Available {
        val updated = transform(value)
        value = updated
        return DeviceCoolingControlResult.Available(updated)
    }
}

private fun fixtureSnapshot(): DeviceCoolingControlSnapshot = DeviceCoolingControlSnapshot(
    mode = DeviceCoolingControlMode.AUTOMATIC,
    manualFanPercent = FIXTURE_MANUAL_FAN_PERCENT,
    actualFanPercent = FIXTURE_RUNTIME_FAN_PERCENT,
    tankTemperatureC = FIXTURE_TANK_TEMPERATURE_C,
    capabilities = FIXTURE_CONTROL_CAPABILITIES
)

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

private const val FIXTURE_MANUAL_FAN_PERCENT = 42
private const val FIXTURE_RUNTIME_FAN_PERCENT = 42.0
private const val FIXTURE_TANK_TEMPERATURE_C = 25.6
