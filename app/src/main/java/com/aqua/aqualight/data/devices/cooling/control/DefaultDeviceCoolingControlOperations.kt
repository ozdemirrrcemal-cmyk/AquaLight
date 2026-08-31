package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingMode
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatus
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Maps the current Cooling firmware contract into stable product semantics.
 *
 * Firmware AUTO/ON/OFF are intentionally contained here. PROGRAM and arbitrary manual-percent
 * writes remain typed Unsupported until firmware exposes authoritative commands for them.
 */
internal class DefaultDeviceCoolingControlOperations(
    private val devicesRepository: DevicesRepository
) : DeviceCoolingControlOperations {

    override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> {
        val access = resolveRuntime(deviceUid) ?: return flowOf(invalidRuntimeResult(deviceUid))
        return access.runtime.states
            .map { states -> states[access.deviceUid]?.status.toControlResult() }
            .distinctUntilChanged()
    }

    override fun currentControl(deviceUid: String): DeviceCoolingControlResult {
        val access = resolveRuntime(deviceUid) ?: return invalidRuntimeResult(deviceUid)
        return access.runtime.states.value[access.deviceUid]?.status.toControlResult()
    }

    override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult {
        val access = resolveRuntime(deviceUid) ?: return invalidRuntimeResult(deviceUid)
        return access.refreshStatus()
    }

    override suspend fun setMode(
        deviceUid: String,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult {
        val access = resolveRuntime(deviceUid) ?: return invalidRuntimeResult(deviceUid)
        if (mode == DeviceCoolingControlMode.PROGRAM) {
            return unsupportedResult()
        }
        val status = access.loadStatus() ?: return access.lastStatusFailure
        if (!status.fanSupported || status.fans.isEmpty()) {
            return DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
        }
        val capabilities = status.toControlCapabilities()
        if (!capabilities.modeSelectionWritable || mode !in capabilities.supportedModes) {
            return unsupportedResult()
        }

        val mutation = when (mode) {
            DeviceCoolingControlMode.AUTOMATIC -> access.runtime.setAuto(access.deviceUid)
            DeviceCoolingControlMode.MANUAL -> {
                if (status.manualTargetPercent() > 0) {
                    access.runtime.setOn(access.deviceUid)
                } else {
                    access.runtime.setOff(access.deviceUid)
                }
            }
            DeviceCoolingControlMode.PROGRAM -> error("PROGRAM is rejected before firmware mapping.")
        }
        return when (mutation) {
            is DeviceRuntimeCommandOutcome.Success -> access.refreshStatus()
            else -> mutation.toFailureResult()
        }
    }

    override suspend fun setManualFanPercent(
        deviceUid: String,
        percent: Int
    ): DeviceCoolingControlResult {
        if (percent !in MINIMUM_PERCENT..MAXIMUM_PERCENT) {
            return DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
        }
        if (resolveRuntime(deviceUid) == null) return invalidRuntimeResult(deviceUid)
        return unsupportedResult()
    }

    private fun resolveRuntime(deviceUid: String): CoolingRuntimeAccess? {
        val uid = deviceUid.trim().takeIf(String::isNotEmpty)?.let(::DeviceUid) ?: return null
        if (devicesRepository.currentDevice(uid) == null) return null
        val runtime = devicesRepository.runtimeModules()?.cooling ?: return null
        return CoolingRuntimeAccess(uid, runtime)
    }
}

private class CoolingRuntimeAccess(
    val deviceUid: DeviceUid,
    val runtime: DeviceCoolingRuntimeRepository
) {
    var lastStatusFailure: DeviceCoolingControlResult =
        DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
        private set

    suspend fun loadStatus(): DeviceCoolingStatus? {
        runtime.states.value[deviceUid]?.status?.let { return it }
        return when (val outcome = runtime.requestStatus(deviceUid)) {
            is DeviceRuntimeCommandOutcome.Success -> outcome.value
            else -> {
                lastStatusFailure = outcome.toFailureResult()
                null
            }
        }
    }

    suspend fun refreshStatus(): DeviceCoolingControlResult =
        when (val outcome = runtime.requestStatus(deviceUid)) {
            is DeviceRuntimeCommandOutcome.Success -> outcome.value.toControlResult()
            else -> outcome.toFailureResult()
        }
}

private fun invalidRuntimeResult(deviceUid: String): DeviceCoolingControlResult =
    DeviceCoolingControlResult.Failed(
        if (deviceUid.isBlank()) {
            DeviceCoolingControlFailure.InvalidData
        } else {
            DeviceCoolingControlFailure.Unavailable
        }
    )

private fun unsupportedResult(): DeviceCoolingControlResult =
    DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unsupported)

private fun DeviceCoolingStatus?.toControlResult(): DeviceCoolingControlResult {
    val status = this
        ?: return DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
    if (!status.supported || !status.fanSupported) return unsupportedResult()
    val fan = status.fans.firstOrNull()
        ?: return DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
    val capabilities = status.toControlCapabilities()
    val tankTemperature = status.temperature
        .takeIf { temperature -> temperature.readingValid }
        ?.temperatureC
        ?.takeIf(Double::isFinite)
    return DeviceCoolingControlResult.Available(
        DeviceCoolingControlSnapshot(
            mode = status.mode.toProductMode(),
            manualFanPercent = status.manualTargetPercent(),
            actualFanPercent = fan.percentNow.toSafePercentOrNull(),
            tankTemperatureC = tankTemperature,
            capabilities = capabilities
        )
    )
}

private fun DeviceCoolingStatus.toControlCapabilities(): DeviceCoolingControlCapabilities {
    val fan = fans.firstOrNull()
    val manualCapabilities = fan?.let {
        val minimum = ceil(it.percentMin).toInt().coerceIn(MINIMUM_PERCENT, MAXIMUM_PERCENT)
        val maximum = floor(it.percentMax).toInt().coerceIn(minimum, MAXIMUM_PERCENT)
        DeviceCoolingManualFanCapabilities(
            minimumPercent = minimum,
            maximumPercent = maximum,
            stepPercent = null,
            writable = false
        )
    }
    return DeviceCoolingControlCapabilities(
        supportedModes = setOf(
            DeviceCoolingControlMode.AUTOMATIC,
            DeviceCoolingControlMode.MANUAL
        ),
        modeSelectionWritable = runtime.supportsConfigApply &&
            runtime.supportsModeSet &&
            !runtime.readOnly,
        manualFan = manualCapabilities
    )
}

private fun DeviceCoolingStatus.manualTargetPercent(): Int = when (mode) {
    DeviceCoolingMode.OFF -> MINIMUM_PERCENT
    DeviceCoolingMode.AUTO,
    DeviceCoolingMode.ON -> fans.firstOrNull()?.percentManual.toSafePercentOrNull() ?: MINIMUM_PERCENT
}

private fun DeviceCoolingMode.toProductMode(): DeviceCoolingControlMode = when (this) {
    DeviceCoolingMode.AUTO -> DeviceCoolingControlMode.AUTOMATIC
    DeviceCoolingMode.ON,
    DeviceCoolingMode.OFF -> DeviceCoolingControlMode.MANUAL
}

private fun Double?.toSafePercentOrNull(): Int? = this
    ?.takeIf(Double::isFinite)
    ?.roundToInt()
    ?.coerceIn(MINIMUM_PERCENT, MAXIMUM_PERCENT)

private fun DeviceRuntimeCommandOutcome<*>.toFailureResult(): DeviceCoolingControlResult =
    DeviceCoolingControlResult.Failed(
        when (this) {
            is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> DeviceCoolingControlFailure.Unsupported
            is DeviceRuntimeCommandOutcome.NotConnected,
            is DeviceRuntimeCommandOutcome.NotAuthenticated -> DeviceCoolingControlFailure.NotConnected
            is DeviceRuntimeCommandOutcome.SendFailed,
            is DeviceRuntimeCommandOutcome.Timeout,
            is DeviceRuntimeCommandOutcome.Cancelled -> DeviceCoolingControlFailure.Unavailable
            is DeviceRuntimeCommandOutcome.FirmwareError -> DeviceCoolingControlFailure.Rejected
            is DeviceRuntimeCommandOutcome.ProtocolError -> DeviceCoolingControlFailure.InvalidData
            is DeviceRuntimeCommandOutcome.Success -> DeviceCoolingControlFailure.InvalidData
        }
    )

private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100
