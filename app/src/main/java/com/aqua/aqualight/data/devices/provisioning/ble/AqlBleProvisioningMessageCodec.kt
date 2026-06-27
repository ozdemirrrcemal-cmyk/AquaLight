package com.aqua.aqualight.data.devices.provisioning.ble

import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningStatus
import org.json.JSONObject

class AqlBleProvisioningMessageCodec {

    fun startSessionJson(draft: AqlProvisioningDraft): String {
        val json = JSONObject()
            .put(AqlBleProvisioningContract.Json.KEY_DEVICE_UID, draft.candidateId)
            .put("bleAddress", draft.bleAddress)
            .put("bleName", draft.bleName)
            .put("deviceTitle", draft.deviceTitle)
            .put("createdAt", draft.createdAtMillis)

        if (draft.claimCode.isNotBlank()) {
            json.put(
                AqlBleProvisioningContract.Json.KEY_CLAIM_CODE,
                draft.claimCode
            )
        }

        return json.toString()
    }

    fun wifiCredentialsJson(draft: AqlProvisioningDraft): String {
        return JSONObject()
            .put(AqlBleProvisioningContract.Json.KEY_WIFI_SSID, draft.wifiCredentials.ssid)
            .put(AqlBleProvisioningContract.Json.KEY_WIFI_PASSWORD, draft.wifiCredentials.password)
            .toString()
    }

    fun parseStatus(raw: String): AqlBleProvisioningStatusMessage {
        val normalizedRaw = raw.trim()
        if (normalizedRaw.isBlank()) {
            return AqlBleProvisioningStatusMessage(
                status = AqlProvisioningStatus.UNKNOWN,
                raw = raw
            )
        }

        val json = runCatching {
            JSONObject(normalizedRaw)
        }.getOrNull()

        if (json == null) {
            return AqlBleProvisioningStatusMessage(
                status = AqlProvisioningStatus.fromWireValue(normalizedRaw),
                raw = raw
            )
        }

        return AqlBleProvisioningStatusMessage(
            status = AqlProvisioningStatus.fromWireValue(
                json.optString(AqlBleProvisioningContract.Json.KEY_STATUS)
            ),
            message = json.optString(AqlBleProvisioningContract.Json.KEY_MESSAGE).trim(),
            raw = raw
        )
    }

    fun parseRuntimeHandoff(
        raw: String,
        fallbackDeviceUid: String
    ): Result<AqlProvisioningRuntimeHandoff> {
        return runCatching {
            val json = JSONObject(raw.trim())
            val deviceUidText = json
                .optString(AqlBleProvisioningContract.Json.KEY_DEVICE_UID)
                .trim()
                .ifBlank { fallbackDeviceUid.trim() }

            val endpoint = DeviceRuntimeEndpoint(
                ip = json.optString(AqlBleProvisioningContract.Json.KEY_IP).trim(),
                wifiConnected = true,
                runtimeTransport = "websocket",
                wsPort = json.optInt(AqlBleProvisioningContract.Json.KEY_WS_PORT, 0),
                wsPath = json
                    .optString(
                        AqlBleProvisioningContract.Json.KEY_WS_PATH,
                        AqlWsContract.DEFAULT_PATH
                    )
                    .trim()
                    .ifBlank { AqlWsContract.DEFAULT_PATH },
                wsProtocol = json
                    .optString(
                        AqlBleProvisioningContract.Json.KEY_WS_PROTOCOL,
                        AqlWsContract.DEFAULT_PROTOCOL
                    )
                    .trim()
                    .ifBlank { AqlWsContract.DEFAULT_PROTOCOL },
                wsProtocolVersion = json.optInt("wsProtocolVersion", 0)
            )

            AqlProvisioningRuntimeHandoff(
                deviceUid = DeviceUid(deviceUidText),
                endpoint = endpoint,
                webSocketToken = json
                    .optString(AqlBleProvisioningContract.Json.KEY_TOKEN)
                    .trim(),
                productFamily = firstJsonValue(
                    json,
                    "family",
                    "productFamily",
                    "product_family"
                ),
                productName = firstJsonValue(
                    json,
                    "productName",
                    "product_name",
                    "displayName",
                    "display_name",
                    "product"
                ),
                productModel = firstJsonValue(
                    json,
                    "model",
                    "productModel",
                    "product_model"
                ),
                firmwareVersion = firstJsonValue(
                    json,
                    "firmwareVersion",
                    "firmware_version",
                    "fw"
                ),
                firmwareBuild = firstJsonValue(
                    json,
                    "firmwareBuild",
                    "firmware_build",
                    "build"
                )
            )
        }
    }

    private fun firstJsonValue(
        json: JSONObject,
        vararg keys: String
    ): String {
        return keys
            .asSequence()
            .map { key -> json.optString(key).trim() }
            .firstOrNull { value -> value.isNotBlank() }
            .orEmpty()
    }
}
