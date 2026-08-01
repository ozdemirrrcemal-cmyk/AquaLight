package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

class DeviceLightRuntimeRepository internal constructor(
    private val gateway: DeviceRuntimeCommandGateway,
    private val stateStore: DeviceLightRuntimeStateStore
) {
    constructor(gateway: DeviceRuntimeCommandGateway) : this(
        gateway = gateway,
        stateStore = DeviceLightRuntimeStateStore()
    )

    val states: StateFlow<Map<DeviceUid, DeviceLightStatus>> = stateStore.statuses

    fun observeStatus(deviceUid: DeviceUid): Flow<DeviceLightStatus?> =
        stateStore.observeStatus(deviceUid)

    fun currentStatus(deviceUid: DeviceUid): DeviceLightStatus? =
        stateStore.currentStatus(deviceUid)

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightStatus> = gateway.execute(
        deviceUid,
        jsonCommand(
            action = DeviceLightRuntimeContract.Action.STATUS_GET,
            parser = DeviceLightStatusParser::parse
        )
    ).recordSuccess { status -> stateStore.recordStatus(deviceUid, status) }

    suspend fun setManual(
        deviceUid: DeviceUid,
        payload: DeviceLightManualSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightManualMutationResult> = gateway.execute(
        deviceUid,
        jsonCommand(
            action = DeviceLightRuntimeContract.Action.MANUAL_SET,
            dataFactory = payload::toJson,
            parser = DeviceLightMutationParser::parseManual
        )
    ).recordSuccess { result -> stateStore.recordManual(deviceUid, result) }

    suspend fun clearManual(
        deviceUid: DeviceUid,
        channelKeys: List<String> = emptyList()
    ): DeviceRuntimeCommandOutcome<DeviceLightManualMutationResult> = setManual(
        deviceUid = deviceUid,
        payload = DeviceLightManualSetPayload(
            clear = true,
            durationMs = null,
            channels = channelKeys.map { key ->
                DeviceLightManualChannelPayload(channelKey = key, percent = 0.0)
            }
        )
    )

    suspend fun setChannelRegime(
        deviceUid: DeviceUid,
        payload: DeviceLightChannelRegimeSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightChannelRegimeMutationResult> = gateway.execute(
        deviceUid,
        jsonCommand(
            action = DeviceLightRuntimeContract.Action.CHANNEL_REGIME_SET,
            dataFactory = payload::toJson,
            parser = DeviceLightMutationParser::parseChannelRegime
        )
    ).recordSuccess { result -> stateStore.recordChannelRegime(deviceUid, result) }

    suspend fun setChannelRegime(
        deviceUid: DeviceUid,
        channelKey: String,
        regime: DeviceLightRegime,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceLightChannelRegimeMutationResult> = setChannelRegime(
        deviceUid = deviceUid,
        payload = DeviceLightChannelRegimeSetPayload(
            channelKey = channelKey,
            regime = regime,
            save = save
        )
    )

    suspend fun applyProgram(
        deviceUid: DeviceUid,
        payload: DeviceLightProgramApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightProgramApplyResult> = gateway.execute(
        deviceUid,
        jsonCommand(
            action = DeviceLightRuntimeContract.Action.PROGRAM_APPLY,
            dataFactory = payload::toJson,
            parser = DeviceLightMutationParser::parseProgramApply
        )
    ).recordSuccess { result -> stateStore.recordProgramApply(deviceUid, result) }

    suspend fun deleteProgram(
        deviceUid: DeviceUid,
        payload: DeviceLightProgramDeletePayload
    ): DeviceRuntimeCommandOutcome<DeviceLightProgramDeleteResult> = gateway.execute(
        deviceUid,
        jsonCommand(
            action = DeviceLightRuntimeContract.Action.PROGRAM_DELETE,
            dataFactory = payload::toJson,
            parser = DeviceLightMutationParser::parseProgramDelete
        )
    ).recordSuccess { result -> stateStore.recordProgramDelete(deviceUid, result) }

    suspend fun deleteProgram(
        deviceUid: DeviceUid,
        programIndex: Int,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceLightProgramDeleteResult> = deleteProgram(
        deviceUid = deviceUid,
        payload = DeviceLightProgramDeletePayload(
            programIndex = programIndex,
            save = save
        )
    )

    internal fun applyTypedEvent(event: DeviceRuntimeTypedEvent): DeviceLightEventApplyResult =
        stateStore.applyTypedEvent(event)

    internal fun clearState(deviceUid: DeviceUid) {
        stateStore.clear(deviceUid)
    }

    private fun <T> jsonCommand(
        action: String,
        dataFactory: () -> JSONObject = ::JSONObject,
        parser: (JSONObject) -> T
    ): DeviceRuntimeJsonCommand<T> = DeviceRuntimeJsonCommand(
        module = DeviceLightRuntimeContract.MODULE,
        action = action,
        dataFactory = dataFactory,
        successParser = parser
    )
}

private fun <T> DeviceRuntimeCommandOutcome<T>.recordSuccess(
    recorder: (T) -> Unit
): DeviceRuntimeCommandOutcome<T> = also { outcome ->
    if (outcome is DeviceRuntimeCommandOutcome.Success) recorder(outcome.value)
}
