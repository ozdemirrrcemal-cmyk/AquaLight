package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingFanStatus
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
            .map { states ->
                states[access.deviceUid]?.status.toControlResult(access.expectedFanOutputCount)
            }
            .distinctUntilChanged()
    }

    override fun currentControl(deviceUid: String): DeviceCoolingControlResult {
        val access = resolveRuntime(deviceUid) ?: return invalidRuntimeResult(deviceUid)
        return access.runtime.states.value[access.deviceUid]?.status
            .toControlResult(access.expectedFanOutputCount)
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
        val snapshot = uid?.let(devicesRepository::currentDevice)
        val catalogProduct = when (
            val validation = snapshot?.let(AqlCommercialDeviceCatalog::validateSnapshot)
        ) {
            is AqlCommercialCatalogValidation.Valid -> validation.product
            is AqlCommercialCatalogValidation.Invalid,
            null -> null
        }
        val runtime = devicesRepository.runtimeModules()?.cooling
        return if (uid != null && catalogProduct != null && runtime != null) {
            CoolingRuntimeAccess(
                deviceUid = uid,
                runtime = runtime,
                expectedFanOutputCount = catalogProduct.limits.fanOutputCount
            )
        } else {
            null
        }
    }
}

private class CoolingRuntimeAccess(
    val deviceUid: DeviceUid,
    val runtime: DeviceCoolingRuntimeRepository,
    val expectedFanOutputCount: Int
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
            is DeviceRuntimeCommandOutcome.Success ->
                outcome.value.toControlResult(expectedFanOutputCount)
            else -> outcome.toFailureResult()
        }

    suspend fun setMode(mode: DeviceCoolingControlMode): DeviceCoolingControlResult {
        val status = if (mode == DeviceCoolingControlMode.PROGRAM) null else loadStatus()
        val fan = status
            ?.takeIf { loaded -> loaded.supported && loaded.fanSupported }
            ?.authoritativeSingleFanOrNull(expectedFanOutputCount)
        val capabilities = if (status != null && fan != null) {
            status.toControlCapabilities(fan)
        } else {
            null
        }
        return when {
            mode == DeviceCoolingControlMode.PROGRAM -> unsupportedResult()
            status == null -> lastStatusFailure
            fan == null || capabilities == null ->
                DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
            !capabilities.modeSelectionWritable -> unsupportedResult()
            mode !in capabilities.supportedModes -> unsupportedResult()
            else -> applyModeMutation(status, fan, mode)
        }
    }

    private suspend fun applyModeMutation(
        status: DeviceCoolingStatus,
        fan: DeviceCoolingFanStatus,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult = when (mode) {
        DeviceCoolingControlMode.AUTOMATIC -> mutationResult(runtime.setAuto(deviceUid))
        DeviceCoolingControlMode.MANUAL -> mutationResult(
            if (status.manualTargetPercent(fan) > 0) {
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

internal fun DeviceCoolingStatus?.toControlResult(
    expectedFanOutputCount: Int
): DeviceCoolingControlResult {
    val status = this
    return when {
        status == null -> DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
        !status.supported || !status.fanSupported -> unsupportedResult()
        else -> {
            val fan = status.authoritativeSingleFanOrNull(expectedFanOutputCount)
                ?: return DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
            val capabilities = status.toControlCapabilities(fan)
            val tankTemperature = status.temperature
                .takeIf { temperature -> temperature.readingValid }
                ?.temperatureC
                ?.takeIf(Double::isFinite)
            DeviceCoolingControlResult.Available(
                DeviceCoolingControlSnapshot(
                    mode = status.mode.toProductMode(),
                    manualFanPercent = status.manualTargetPercent(fan),
                    actualFanPercent = fan.percentNow.toSafePercentOrNull(),
                    tankTemperatureC = tankTemperature,
                    capabilities = capabilities
                )
            )
        }
    }
}

private fun DeviceCoolingStatus.authoritativeSingleFanOrNull(
    expectedFanOutputCount: Int
): DeviceCoolingFanStatus? {
    val topologyMatches = expectedFanOutputCount == SINGLE_FAN_OUTPUT_COUNT &&
        fanOutputCount == expectedFanOutputCount &&
        fans.size == expectedFanOutputCount
    return if (topologyMatches) fans.singleOrNull() else null
}

private fun DeviceCoolingStatus.toControlCapabilities(
    fan: DeviceCoolingFanStatus
): DeviceCoolingControlCapabilities {
    val minimum = ceil(fan.percentMin).toInt().coerceIn(MINIMUM_PERCENT, MAXIMUM_PERCENT)
    val maximum = floor(fan.percentMax).toInt().coerceIn(minimum, MAXIMUM_PERCENT)
    val manualCapabilities = DeviceCoolingManualFanCapabilities(
        minimumPercent = minimum,
        maximumPercent = maximum,
        stepPercent = null,
        writable = false
    )
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

private fun DeviceCoolingStatus.manualTargetPercent(fan: DeviceCoolingFanStatus): Int = when (mode) {
    DeviceCoolingMode.OFF -> MINIMUM_PERCENT
    DeviceCoolingMode.AUTO,
    DeviceCoolingMode.ON -> fan.percentManual.toSafePercentOrNull() ?: MINIMUM_PERCENT
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

private const val SINGLE_FAN_OUTPUT_COUNT = 1
private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100
