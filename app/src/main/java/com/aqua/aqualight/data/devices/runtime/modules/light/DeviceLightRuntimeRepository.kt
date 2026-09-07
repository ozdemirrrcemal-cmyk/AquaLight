package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

class DeviceLightRuntimeRepository internal constructor(
    private val gateway: DeviceRuntimeCommandGateway,
    internal val stateStore: DeviceLightRuntimeStateStore
) {
    constructor(gateway: DeviceRuntimeCommandGateway) : this(
        gateway = gateway,
        stateStore = DeviceLightRuntimeStateStore()
    )

    val states: StateFlow<Map<DeviceUid, DeviceLightStatus>> = stateStore.statuses

    internal fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ) = stateStore.beginGeneration(deviceUid, generation)

    internal fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) = stateStore.invalidate(deviceUid, generation)

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightStatus> {
        val outcome = gateway.execute(
            deviceUid,
            jsonCommand(
                action = DeviceLightRuntimeContract.Action.STATUS_GET,
                parser = DeviceLightStatusParser::parse
            )
        )
        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            stateStore.recordStatus(deviceUid, outcome.generation, outcome.value)
        }
        return outcome
    }

    suspend fun setManual(
        deviceUid: DeviceUid,
        payload: DeviceLightManualSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightManualMutationResult> {
        val status = stateStore.currentAuthoritativeStatus(deviceUid)
        if (status != null && (!status.supported || !status.manualSupported)) {
            return unsupported(deviceUid, DeviceLightRuntimeContract.Action.MANUAL_SET)
        }
        val outcome = gateway.execute(
            deviceUid,
            jsonCommand(
                action = DeviceLightRuntimeContract.Action.MANUAL_SET,
                dataFactory = {
                    DeviceLightCommandValidation.validateManualRequest(payload)
                    payload.toJson()
                },
                parser = { data ->
                    DeviceLightMutationParser.parseManual(data).also { result ->
                        DeviceLightCommandValidation.validateManual(payload, result)
                    }
                }
            )
        )
        if (
            outcome is DeviceRuntimeCommandOutcome.Success &&
            !stateStore.recordManual(deviceUid, outcome.generation, outcome.value)
        ) {
            requestStatus(deviceUid)
        }
        return outcome
    }

    suspend fun clearManual(
        deviceUid: DeviceUid,
        channelKeys: List<String> = emptyList()
    ): DeviceRuntimeCommandOutcome<DeviceLightManualMutationResult> = setManual(
        deviceUid = deviceUid,
        payload = DeviceLightManualSetPayload(
            clear = true,
            durationMs = null,
            channels = channelKeys.map { key ->
                DeviceLightManualChannelPayload(channelKey = key)
            }
        )
    )

    suspend fun setChannelRegime(
        deviceUid: DeviceUid,
        payload: DeviceLightChannelRegimeSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightChannelRegimeMutationResult> {
        val status = stateStore.currentAuthoritativeStatus(deviceUid)
        if (status != null && (!status.supported || !status.runtime.supportsChannelRegimeSet)) {
            return unsupported(deviceUid, DeviceLightRuntimeContract.Action.CHANNEL_REGIME_SET)
        }
        val outcome = gateway.execute(
            deviceUid,
            jsonCommand(
                action = DeviceLightRuntimeContract.Action.CHANNEL_REGIME_SET,
                dataFactory = {
                    DeviceLightCommandValidation.validateChannelRegimeRequest(payload)
                    payload.toJson()
                },
                parser = { data ->
                    DeviceLightMutationParser.parseChannelRegime(data).also { result ->
                        DeviceLightCommandValidation.validateChannelRegime(payload, result)
                    }
                }
            )
        )
        if (
            outcome is DeviceRuntimeCommandOutcome.Success &&
            !stateStore.recordChannelRegime(deviceUid, outcome.generation, outcome.value)
        ) {
            requestStatus(deviceUid)
        }
        return outcome
    }

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
    ): DeviceRuntimeCommandOutcome<DeviceLightProgramApplyResult> {
        val status = stateStore.currentAuthoritativeStatus(deviceUid)
        if (status != null && (!status.supported || !status.programSupported)) {
            return unsupported(deviceUid, DeviceLightRuntimeContract.Action.PROGRAM_APPLY)
        }
        val outcome = gateway.execute(
            deviceUid,
            jsonCommand(
                action = DeviceLightRuntimeContract.Action.PROGRAM_APPLY,
                dataFactory = {
                    DeviceLightCommandValidation.validateProgramRequest(payload)
                    payload.toJson()
                },
                parser = { data ->
                    DeviceLightMutationParser.parseProgramApply(data).also { result ->
                        DeviceLightCommandValidation.validateProgramApply(payload, result)
                    }
                }
            )
        )
        if (
            outcome is DeviceRuntimeCommandOutcome.Success &&
            !stateStore.recordProgramApply(deviceUid, outcome.generation, outcome.value)
        ) {
            requestStatus(deviceUid)
        }
        return outcome
    }

    suspend fun deleteProgram(
        deviceUid: DeviceUid,
        payload: DeviceLightProgramDeletePayload
    ): DeviceRuntimeCommandOutcome<DeviceLightProgramDeleteResult> {
        val status = stateStore.currentAuthoritativeStatus(deviceUid)
        if (status != null && (!status.supported || !status.programSupported)) {
            return unsupported(deviceUid, DeviceLightRuntimeContract.Action.PROGRAM_DELETE)
        }
        val outcome = gateway.execute(
            deviceUid,
            jsonCommand(
                action = DeviceLightRuntimeContract.Action.PROGRAM_DELETE,
                dataFactory = payload::toJson,
                parser = { data ->
                    DeviceLightMutationParser.parseProgramDelete(data).also { result ->
                        DeviceLightCommandValidation.validateProgramDelete(payload, result)
                    }
                }
            )
        )
        if (
            outcome is DeviceRuntimeCommandOutcome.Success &&
            !stateStore.recordProgramDelete(deviceUid, outcome.generation, outcome.value)
        ) {
            requestStatus(deviceUid)
        }
        return outcome
    }

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
}

internal fun DeviceLightRuntimeRepository.isAuthoritative(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration
): Boolean = stateStore.isStatusAuthoritative(deviceUid, generation)

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

private fun unsupported(
    deviceUid: DeviceUid,
    action: String
): DeviceRuntimeCommandOutcome.UnsupportedByDevice =
    DeviceRuntimeCommandOutcome.UnsupportedByDevice(
        deviceUid = deviceUid,
        module = DeviceLightRuntimeContract.MODULE,
        action = action
    )
