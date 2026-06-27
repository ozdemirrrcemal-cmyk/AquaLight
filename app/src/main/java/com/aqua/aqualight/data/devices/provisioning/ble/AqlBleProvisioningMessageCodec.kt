package com.aqua.aqualight.data.devices.provisioning.ble

import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningStatus
import java.net.URLDecoder
import java.util.Locale
import org.json.JSONObject

class AqlBleProvisioningMessageCodec {

    fun startSessionJson(draft: AqlProvisioningDraft): String {
        val json = JSONObject()
            .put(AqlBleProvisioningContract.Json.KEY_APP_NONCE, draft.sessionId)

        val deviceUid = draft.candidateId
            .trim()
            .takeUnless { value -> value.isLikelyBleAddress() }
            .orEmpty()

        if (deviceUid.isNotBlank()) {
            json.put(AqlBleProvisioningContract.Json.KEY_DEVICE_UID, deviceUid)
        }

        val provisioningId = provisioningIdFromRawQrPayload(draft.rawQrPayload)
        if (provisioningId.isNotBlank()) {
            json.put(
                AqlBleProvisioningContract.Json.KEY_PROVISIONING_ID,
                provisioningId
            )
        }

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
            message = json
                .optString(AqlBleProvisioningContract.Json.KEY_MESSAGE)
                .trim()
                .ifBlank {
                    json.optString(AqlBleProvisioningContract.Json.KEY_LAST_ERROR).trim()
                },
            raw = raw
        )
    }

    fun parseRuntimeHandoff(
        raw: String,
        fallbackDeviceUid: String
    ): Result<AqlProvisioningRuntimeHandoff> {
        return runCatching {
            val json = JSONObject(raw.trim())

            val fallbackUid = fallbackDeviceUid
                .trim()
                .takeUnless { value -> value.isLikelyBleAddress() }
                .orEmpty()

            val deviceUidText = firstJsonValue(
                json,
                AqlBleProvisioningContract.Json.KEY_DEVICE_UID,
                "uid",
                "device_uid"
            ).ifBlank { fallbackUid }

            val wsPort = firstJsonInt(
                json,
                AqlBleProvisioningContract.Json.KEY_WS_PORT,
                "wsPort",
                "webSocketPort"
            )

            val wsPath = firstJsonValue(
                json,
                AqlBleProvisioningContract.Json.KEY_WS_PATH,
                "wsPath",
                "path"
            ).ifBlank { AqlWsContract.DEFAULT_PATH }

            val wsProtocol = firstJsonValue(
                json,
                AqlBleProvisioningContract.Json.KEY_WS_PROTOCOL,
                "wsProtocol",
                "protocol"
            ).ifBlank { AqlWsContract.DEFAULT_PROTOCOL }

            val endpoint = DeviceRuntimeEndpoint(
                ip = json.optString(AqlBleProvisioningContract.Json.KEY_IP).trim(),
                wifiConnected = true,
                runtimeTransport = "websocket",
                wsPort = wsPort,
                wsPath = wsPath,
                wsProtocol = wsProtocol,
                wsProtocolVersion = json.optInt("wsProtocolVersion", AqlWsContract.PROTOCOL_VERSION)
            )

            AqlProvisioningRuntimeHandoff(
                deviceUid = DeviceUid(deviceUidText),
                endpoint = endpoint,
                webSocketToken = firstJsonValue(
                    json,
                    AqlBleProvisioningContract.Json.KEY_TOKEN,
                    "pairingToken",
                    "webSocketToken"
                ),
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

    private fun provisioningIdFromRawQrPayload(rawQrPayload: String): String {
        val normalized = rawQrPayload.trim()
        if (normalized.isBlank()) return ""

        return runCatching {
            if (normalized.startsWith("{")) {
                JSONObject(normalized)
                    .optString(AqlBleProvisioningContract.Qr.KEY_PRODUCT_ID)
                    .trim()
            } else {
                parseQueryFields(normalized)[AqlBleProvisioningContract.Qr.KEY_PRODUCT_ID].orEmpty()
            }
        }.getOrDefault("")
    }

    private fun parseQueryFields(raw: String): Map<String, String> {
        val query = raw.substringAfter("?", raw)
        val fields = mutableMapOf<String, String>()

        query.split("&")
            .asSequence()
            .filter { part -> part.isNotBlank() && part.contains("=") }
            .forEach { part ->
                val key = decode(part.substringBefore("="))
                    .trim()
                    .lowercase(Locale.US)
                val value = decode(part.substringAfter("=")).trim()

                if (key.isNotBlank()) {
                    fields[key] = value
                }
            }

        return fields
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

    private fun firstJsonInt(
        json: JSONObject,
        vararg keys: String
    ): Int {
        return keys
            .asSequence()
            .map { key -> json.opt(key) }
            .mapNotNull { value ->
                when (value) {
                    is Number -> value.toInt()
                    is String -> value.trim().toIntOrNull()
                    else -> null
                }
            }
            .firstOrNull { value -> value > 0 }
            ?: 0
    }

    private fun String.isLikelyBleAddress(): Boolean {
        return matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))
    }

    private fun decode(value: String): String {
        return runCatching {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        }.getOrDefault(value)
    }
}
