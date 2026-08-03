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
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStatusResponse> = gateway.execute(
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
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStartAccepted> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceFirmwareRuntimeContract.MODULE,
            action = DeviceFirmwareRuntimeContract.Action.OTA_START,
            dataFactory = payload::toJson,
            successParser = { data ->
                val compatibleData = DeviceFirmwareOtaStartCompatibility
                    .normalizeAcceptedResponse(data, payload)
                DeviceFirmwareStatusParser
                    .parseOtaStartAcceptedExact(compatibleData)
                    .getOrThrow()
            }
        )
    )

    suspend fun startUpdate(
        plan: DeviceFirmwareUpdatePlan
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStartAccepted> =
        startOta(
            deviceUid = plan.deviceUid,
            payload = plan.payload
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

/**
 * Normalizes the single known pre-model OTA start response emitted by firmware 1.0.0.
 *
 * The compatibility path stays fail-closed: it runs only when the request echo has exactly the
 * legacy key set and every echoed value matches the signed command payload Android just sent.
 */
internal object DeviceFirmwareOtaStartCompatibility {

    fun normalizeAcceptedResponse(
        data: JSONObject,
        payload: DeviceFirmwareOtaStartPayload
    ): JSONObject {
        val normalized = JSONObject(data.toString())
        val request = normalized.optJSONObject("request") ?: return normalized
        if (request.has(DeviceFirmwareRuntimeContract.Field.MODEL)) return normalized
        if (request.keySet() != LEGACY_REQUEST_KEYS) return normalized

        require(request.requiredString("urlScheme") == "https")
        require(request.requiredString("version") == payload.version)
        require(request.requiredInt("expectedSize") == payload.expectedSize)
        require(request.requiredBoolean("applyNow") == payload.applyNow)
        require(!request.requiredBoolean("allowInsecureHttp"))
        require(request.requiredString("productKey") == payload.productKey)
        require(request.requiredString("productId") == payload.productId)
        require(request.requiredString("hardwareRevision") == payload.hardwareRevision)

        request.put(DeviceFirmwareRuntimeContract.Field.MODEL, payload.model)
        return normalized
    }

    private fun JSONObject.keySet(): Set<String> = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }

    private fun JSONObject.requiredString(key: String): String =
        get(key) as? String ?: error("$key must be a string.")

    private fun JSONObject.requiredBoolean(key: String): Boolean =
        get(key) as? Boolean ?: error("$key must be a boolean.")

    private fun JSONObject.requiredInt(key: String): Int {
        val value = get(key) as? Number ?: error("$key must be an integer.")
        val asLong = value.toLong()
        require(value.toDouble().isFinite() && value.toDouble() == asLong.toDouble())
        require(asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return asLong.toInt()
    }

    private val LEGACY_REQUEST_KEYS = setOf(
        "urlScheme",
        "version",
        "expectedSize",
        "applyNow",
        "allowInsecureHttp",
        "productKey",
        "productId",
        "hardwareRevision"
    )
}
