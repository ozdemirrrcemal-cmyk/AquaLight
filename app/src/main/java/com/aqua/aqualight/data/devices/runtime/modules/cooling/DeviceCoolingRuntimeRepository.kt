package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

class DeviceCoolingRuntimeRepository internal constructor(
    private val gateway: DeviceRuntimeCommandGateway,
    private val stateStore: DeviceCoolingRuntimeStateStore
) {
    constructor(gateway: DeviceRuntimeCommandGateway) : this(
        gateway = gateway,
        stateStore = DeviceCoolingRuntimeStateStore()
    )

    val states: StateFlow<Map<DeviceUid, DeviceCoolingRuntimeState>> = stateStore.states

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceCoolingStatus> = gateway.execute(
        deviceUid,
        jsonCommand(
            action = DeviceCoolingRuntimeContract.Action.STATUS_GET,
            parser = DeviceCoolingStatusParser::parse
        )
    ).recordSuccess { status -> stateStore.recordStatus(deviceUid, status) }

    suspend fun requestHistory(
        deviceUid: DeviceUid,
        range: DeviceCoolingHistoryRange
    ): DeviceRuntimeCommandOutcome<DeviceCoolingHistorySnapshot> = gateway.execute(
        deviceUid,
        jsonCommand(
            action = DeviceCoolingRuntimeContract.Action.HISTORY_GET,
            dataFactory = { DeviceCoolingHistoryGetPayload(range).toJson() },
            parser = { data ->
                DeviceCoolingHistoryParser.parse(data).also { snapshot ->
                    require(snapshot.range == range) {
                        "Firmware returned a different cooling history range."
                    }
                }
            }
        )
    )

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceCoolingConfigApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> {
        val status = states.value[deviceUid]?.status
        coolingUnsupportedReason(payload, status)?.let {
            return coolingUnsupported(deviceUid, DeviceCoolingRuntimeContract.Action.CONFIG_APPLY)
        }
        return gateway.execute(
            deviceUid,
            jsonCommand(
                action = DeviceCoolingRuntimeContract.Action.CONFIG_APPLY,
                dataFactory = {
                    DeviceCoolingCommandValidation.validateRequest(payload, status)
                    payload.toJson()
                },
                parser = { data ->
                    DeviceCoolingMutationParser.parseConfigApply(data).also { result ->
                        DeviceCoolingCommandValidation.validateResult(payload, result)
                    }
                }
            )
        ).recordSuccess { result -> stateStore.recordConfig(deviceUid, result) }
    }

    suspend fun setMode(
        deviceUid: DeviceUid,
        mode: DeviceCoolingMode,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> = applyConfig(
        deviceUid,
        DeviceCoolingConfigApplyPayload(mode = mode, save = save)
    )

    suspend fun setTemperatureRange(
        deviceUid: DeviceUid,
        minTemperatureC: Double,
        maxTemperatureC: Double,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> = applyConfig(
        deviceUid,
        DeviceCoolingConfigApplyPayload(
            minTemperatureC = minTemperatureC,
            maxTemperatureC = maxTemperatureC,
            save = save
        )
    )

    suspend fun setFanDisplayNames(
        deviceUid: DeviceUid,
        fans: List<DeviceCoolingFanDisplayNamePayload>,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> = applyConfig(
        deviceUid,
        DeviceCoolingConfigApplyPayload(fans = fans, save = save)
    )

    suspend fun setAuto(
        deviceUid: DeviceUid,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> =
        setMode(deviceUid, DeviceCoolingMode.AUTO, save)

    suspend fun setOn(
        deviceUid: DeviceUid,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> =
        setMode(deviceUid, DeviceCoolingMode.ON, save)

    suspend fun setOff(
        deviceUid: DeviceUid,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> =
        setMode(deviceUid, DeviceCoolingMode.OFF, save)

    private fun <T> jsonCommand(
        action: String,
        dataFactory: () -> JSONObject = ::JSONObject,
        parser: (JSONObject) -> T
    ): DeviceRuntimeJsonCommand<T> = DeviceRuntimeJsonCommand(
        module = DeviceCoolingRuntimeContract.MODULE,
        action = action,
        dataFactory = dataFactory,
        successParser = parser
    )
}

private fun coolingUnsupportedReason(
    payload: DeviceCoolingConfigApplyPayload,
    status: DeviceCoolingStatus?
): String? = when {
    status == null -> null
    !status.supported || !status.runtime.supportsConfigApply -> "Cooling is unsupported."
    payload.mode != null && !status.runtime.supportsModeSet -> "Cooling mode is unsupported."
    (payload.minTemperatureC != null || payload.maxTemperatureC != null) &&
        !status.runtime.supportsTemperatureRange -> "Temperature range is unsupported."
    payload.fans.isNotEmpty() && !status.runtime.supportsFanDisplayName ->
        "Fan display names are unsupported."
    else -> null
}

private fun coolingUnsupported(
    deviceUid: DeviceUid,
    action: String
): DeviceRuntimeCommandOutcome.UnsupportedByDevice =
    DeviceRuntimeCommandOutcome.UnsupportedByDevice(
        deviceUid = deviceUid,
        module = DeviceCoolingRuntimeContract.MODULE,
        action = action
    )

private fun <T> DeviceRuntimeCommandOutcome<T>.recordSuccess(
    recorder: (T) -> Unit
): DeviceRuntimeCommandOutcome<T> = also { outcome ->
    if (outcome is DeviceRuntimeCommandOutcome.Success) recorder(outcome.value)
}
