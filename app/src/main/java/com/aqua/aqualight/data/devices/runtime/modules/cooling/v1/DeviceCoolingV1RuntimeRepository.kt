package com.aqua.aqualight.data.devices.runtime.modules.cooling.v1

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import org.json.JSONObject

/**
 * Golden Contract V1 command surface.
 *
 * This repository intentionally has no presentation binding yet. It gives the
 * later data-connection work one exact, revision-aware firmware boundary.
 */
class DeviceCoolingV1RuntimeRepository(
    private val gateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceCoolingV1StatusDocument> = execute(
        deviceUid = deviceUid,
        action = DeviceCoolingV1Contract.Action.STATUS_GET,
        parser = DeviceCoolingV1ResponseParser::parseStatus
    )

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceCoolingV1ConfigApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceCoolingV1ConfigApplyResult> = execute(
        deviceUid = deviceUid,
        action = DeviceCoolingV1Contract.Action.CONFIG_APPLY,
        dataFactory = payload::toJson,
        parser = DeviceCoolingV1ResponseParser::parseConfigApply
    )

    suspend fun applyManual(
        deviceUid: DeviceUid,
        payload: DeviceCoolingV1ManualApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceCoolingV1ManualApplyResult> = execute(
        deviceUid = deviceUid,
        action = DeviceCoolingV1Contract.Action.MANUAL_APPLY,
        dataFactory = payload::toJson,
        parser = DeviceCoolingV1ResponseParser::parseManualApply
    )

    suspend fun requestProgram(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceCoolingV1ProgramSnapshot> = execute(
        deviceUid = deviceUid,
        action = DeviceCoolingV1Contract.Action.PROGRAM_GET,
        parser = DeviceCoolingV1ResponseParser::parseProgram
    )

    suspend fun applyProgram(
        deviceUid: DeviceUid,
        payload: DeviceCoolingV1ProgramApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceCoolingV1ProgramApplyResult> = execute(
        deviceUid = deviceUid,
        action = DeviceCoolingV1Contract.Action.PROGRAM_APPLY,
        dataFactory = payload::toJson,
        parser = DeviceCoolingV1ResponseParser::parseProgramApply
    )

    suspend fun requestHistory(
        deviceUid: DeviceUid,
        payload: DeviceCoolingV1HistoryGetPayload
    ): DeviceRuntimeCommandOutcome<DeviceCoolingV1History> = execute(
        deviceUid = deviceUid,
        action = DeviceCoolingV1Contract.Action.HISTORY_GET,
        dataFactory = payload::toJson,
        parser = DeviceCoolingV1ResponseParser::parseHistory
    )

    private suspend fun <T> execute(
        deviceUid: DeviceUid,
        action: String,
        dataFactory: () -> JSONObject = ::JSONObject,
        parser: (JSONObject) -> T
    ): DeviceRuntimeCommandOutcome<T> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = AqlWsContract.MODULE_COOLING,
            action = action,
            dataFactory = dataFactory,
            successParser = parser
        )
    )
}
