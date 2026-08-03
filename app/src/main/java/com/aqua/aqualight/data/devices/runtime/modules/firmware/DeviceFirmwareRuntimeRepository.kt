package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import org.json.JSONObject

class DeviceFirmwareRuntimeRepository(
    private val gateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareStatus> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceFirmwareRuntimeContract.MODULE,
            action = DeviceFirmwareRuntimeContract.Action.STATUS_GET,
            successParser = DeviceFirmwareReadParser::parseStatus
        )
    )

    suspend fun readOtaStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaSnapshot> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceFirmwareRuntimeContract.MODULE,
            action = DeviceFirmwareRuntimeContract.Action.OTA_STATUS,
            successParser = DeviceFirmwareReadParser::parseOtaStatus
        )
    )

    suspend fun startOta(
        deviceUid: DeviceUid,
        payload: DeviceFirmwareOtaStartPayload
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStartAccepted> = executeStart(
        deviceUid = deviceUid,
        payload = payload,
        legacyModelEcho = null
    )

    suspend fun startUpdate(
        plan: DeviceFirmwareUpdatePlan
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStartAccepted> = executeStart(
        deviceUid = plan.deviceUid,
        payload = plan.payload,
        legacyModelEcho = plan.legacyStartEchoModelOrNull()
    )

    private suspend fun executeStart(
        deviceUid: DeviceUid,
        payload: DeviceFirmwareOtaStartPayload,
        legacyModelEcho: String?
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStartAccepted> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceFirmwareRuntimeContract.MODULE,
            action = DeviceFirmwareRuntimeContract.Action.OTA_START,
            dataFactory = payload::toJson,
            successParser = { data ->
                DeviceFirmwareStatusParser.parseOtaStartAcceptedExact(
                    data.withReleasedV1ModelEcho(legacyModelEcho)
                ).getOrThrow()
            }
        )
    )

    suspend fun clearOtaStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaClearResult> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceFirmwareRuntimeContract.MODULE,
            action = DeviceFirmwareRuntimeContract.Action.OTA_CLEAR,
            successParser = { data ->
                DeviceFirmwareStatusParser.parseOtaClearResultExact(data).getOrThrow()
            }
        )
    )
}

private fun DeviceFirmwareUpdatePlan.legacyStartEchoModelOrNull(): String? =
    payload.model.takeIf {
        currentVersion.trim().removePrefix("v") == RELEASED_V1_WITHOUT_MODEL_ECHO
    }

private fun JSONObject.withReleasedV1ModelEcho(expectedModel: String?): JSONObject {
    if (expectedModel == null) return this
    val request = optJSONObject("request") ?: return this
    if (request.exactKeys() != RELEASED_V1_REQUEST_ECHO_KEYS) return this

    return JSONObject(toString()).apply {
        getJSONObject("request").put(DeviceFirmwareRuntimeContract.Field.MODEL, expectedModel)
    }
}

private fun JSONObject.exactKeys(): Set<String> = buildSet {
    val iterator = keys()
    while (iterator.hasNext()) add(iterator.next())
}

private const val RELEASED_V1_WITHOUT_MODEL_ECHO = "1.0.0"

private val RELEASED_V1_REQUEST_ECHO_KEYS = setOf(
    "urlScheme",
    "version",
    "expectedSize",
    "applyNow",
    "allowInsecureHttp",
    "productKey",
    "productId",
    "hardwareRevision"
)
