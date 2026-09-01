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
    ): DeviceCoolingControlResult =
        resolveRuntime(deviceUid)?.setMode(mode) ?: invalidRuntimeResult(deviceUid)

    override suspend fun setManualFanPercent(
        deviceUid: String,
        percent: Int
    ): DeviceCoolingControlResult {
        val access = resolveRuntime(deviceUid)
        return when {
            percent !in MINIMUM_PERCENT..MAXIMUM_PERCENT ->
                DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
            access == null -> invalidRuntimeResult(deviceUid)
            else -> unsupportedResult()
        }
    }

    private fun resolveRuntime(deviceUid: String): CoolingRuntimeAccess? {
        val uid = deviceUid.trim().takeIf(String::isNotEmpty)?.let(::DeviceUid)
        return uid
            ?.takeIf { validUid -> devicesRepository.currentDevice(validUid) != null }
            ?.let { validUid ->
                devicesRepository.runtimeModules()?.cooling?.let { runtime ->
                    CoolingRuntimeAccess(validUid, runtime)
                }
            }
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

    suspend fun setMode(mode: DeviceCoolingControlMode): DeviceCoolingControlResult {
        val status = if (mode == DeviceCoolingControlMode.PROGRAM) null else loadStatus()
        val capabilities = status
            ?.takeIf { loaded -> loaded.fanSupported && loaded.fans.isNotEmpty() }
            ?.toControlCapabilities()
        return when {
            mode == DeviceCoolingControlMode.PROGRAM -> unsupportedResult()
            status == null -> lastStatusFailure
            capabilities == null ->
                DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
            !capabilities.modeSelectionWritable -> unsupportedResult()
            mode !in capabilities.supportedModes -> unsupportedResult()
            else -> applyModeMutation(status, mode)
        }
    }

    private suspend fun applyModeMutation(
        status: DeviceCoolingStatus,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult = when (mode) {
        DeviceCoolingControlMode.AUTOMATIC -> mutationResult(runtime.setAuto(deviceUid))
        DeviceCoolingControlMode.MANUAL -> mutationResult(
            if (status.manualTargetPercent() > 0) {
                runtime.setOn(deviceUid)
            } else {
                runtime.setOff(deviceUid)
            }
        )
        DeviceCoolingControlMode.PROGRAM -> unsupportedResult()
    }

    private suspend fun mutationResult(
        outcome: DeviceRuntimeCommandOutcome<*>
    ): DeviceCoolingControlResult = when (outcome) {
        is DeviceRuntimeCommandOutcome.Success -> refreshStatus()
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
    return when {
        status == null -> DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
        !status.supported || !status.fanSupported -> unsupportedResult()
        status.fans.isEmpty() ->
            DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
        else -> {
            val fan = status.fans.first()
            val capabilities = status.toControlCapabilities()
            val tankTemperature = status.temperature
                .takeIf { temperature -> temperature.readingValid }
                ?.temperatureC
                ?.takeIf(Double::isFinite)
            DeviceCoolingControlResult.Available(
                DeviceCoolingControlSnapshot(
                    mode = status.mode.toProductMode(),
                    manualFanPercent = status.manualTargetPercent(),
                    actualFanPercent = fan.percentNow.toSafePercentOrNull(),
                    tankTemperatureC = tankTemperature,
                    capabilities = capabilities
                )
            )
        }
    }
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
