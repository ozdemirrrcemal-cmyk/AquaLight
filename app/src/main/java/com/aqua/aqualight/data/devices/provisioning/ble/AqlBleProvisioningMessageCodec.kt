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

    fun startSessionJson(draft: AqlProvisioningDraft, deviceNonce: String = ""): String {
        val json = JSONObject()
            .put(AqlBleProvisioningContract.Json.KEY_APP_NONCE, draft.sessionId)

        val normalizedDeviceNonce = deviceNonce.trim()
        if (normalizedDeviceNonce.isNotBlank()) {
            json.put(AqlBleProvisioningContract.Json.KEY_DEVICE_NONCE, normalizedDeviceNonce)
        }

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
        val wifiCredentials = draft.wifiCredentials

        return JSONObject()
            .put(AqlBleProvisioningContract.Json.KEY_WIFI_SSID, wifiCredentials.ssid)
            .put(AqlBleProvisioningContract.Json.KEY_WIFI_PASSWORD, wifiCredentials.password)
            .put(
                AqlBleProvisioningContract.Json.KEY_WIFI_UTC_OFFSET_MINUTES,
                wifiCredentials.utcOffsetMinutes
            )
            .apply {
                if (wifiCredentials.bssid.isNotBlank()) {
                    put(AqlBleProvisioningContract.Json.KEY_WIFI_BSSID, wifiCredentials.bssid)
                }
                if (wifiCredentials.channel > 0) {
                    put(AqlBleProvisioningContract.Json.KEY_WIFI_CHANNEL, wifiCredentials.channel)
                }
                if (wifiCredentials.timezone.isNotBlank()) {
                    put(AqlBleProvisioningContract.Json.KEY_WIFI_TIMEZONE, wifiCredentials.timezone)
                }
            }
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

            val expectedDeviceUid = fallbackDeviceUid
                .trim()
                .takeUnless { value -> value.isLikelyBleAddress() }
                .orEmpty()

            val deviceUidText = requiredJsonString(
                json = json,
                key = AqlBleProvisioningContract.Json.KEY_DEVICE_UID,
                label = "RuntimeEndpoint"
            )

            require(expectedDeviceUid.isBlank() || deviceUidText.equals(expectedDeviceUid, ignoreCase = true)) {
                "RuntimeEndpoint deviceUid does not match the provisioning draft."
            }

            val ip = requiredJsonString(
                json = json,
                key = AqlBleProvisioningContract.Json.KEY_IP,
                label = "RuntimeEndpoint"
            )
            val wsPort = requiredJsonInt(
                json = json,
                key = AqlBleProvisioningContract.Json.KEY_WS_PORT,
                label = "RuntimeEndpoint"
            )
            val wsPath = requiredJsonString(
                json = json,
                key = AqlBleProvisioningContract.Json.KEY_WS_PATH,
                label = "RuntimeEndpoint"
            )
            require(wsPath == AqlWsContract.DEFAULT_PATH) {
                "RuntimeEndpoint path is not supported: $wsPath"
            }

            val productModel = requiredJsonString(
                json = json,
                key = KEY_PRODUCT_MODEL,
                label = "RuntimeEndpoint"
            )
            val firmwareVersion = requiredJsonString(
                json = json,
                key = KEY_FIRMWARE_VERSION,
                label = "RuntimeEndpoint"
            )
            val runtimeToken = requiredJsonString(
                json = json,
                key = AqlBleProvisioningContract.Json.KEY_TOKEN,
                label = "RuntimeEndpoint"
            )
            require(runtimeToken.isRuntimeTokenHex()) {
                "RuntimeEndpoint token is missing or invalid."
            }

            val endpoint = DeviceRuntimeEndpoint(
                ip = ip,
                wifiConnected = true,
                runtimeTransport = "websocket",
                wsPort = wsPort,
                wsPath = wsPath,
                wsProtocol = AqlWsContract.DEFAULT_PROTOCOL,
                wsProtocolVersion = AqlWsContract.PROTOCOL_VERSION
            )

            AqlProvisioningRuntimeHandoff(
                deviceUid = DeviceUid(deviceUidText),
                endpoint = endpoint,
                webSocketToken = runtimeToken,
                productModel = productModel,
                firmwareVersion = firmwareVersion
            )
        }
    }

    private fun provisioningIdFromRawQrPayload(rawQrPayload: String): String {
        val normalized = rawQrPayload.trim()
        if (normalized.isBlank()) return ""

        return runCatching {
            if (normalized.startsWith("{")) {
                JSONObject(normalized)
                    .optString(AqlBleProvisioningContract.Qr.KEY_PROVISIONING_ID)
                    .trim()
            } else {
                parseQueryFields(normalized)[AqlBleProvisioningContract.Qr.KEY_PROVISIONING_ID].orEmpty()
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

    private fun requiredJsonString(
        json: JSONObject,
        key: String,
        label: String
    ): String {
        return json.optString(key)
            .trim()
            .takeIf { value -> value.isNotBlank() }
            ?: error("$label field '$key' is missing.")
    }

    private fun requiredJsonInt(
        json: JSONObject,
        key: String,
        label: String
    ): Int {
        val value = json.opt(key)
        val intValue = when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }

        return intValue
            ?.takeIf { number -> number > 0 }
            ?: error("$label field '$key' is missing or invalid.")
    }

    private fun String.isLikelyBleAddress(): Boolean {
        return matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))
    }

    private fun String.isRuntimeTokenHex(): Boolean {
        return length == AqlBleProvisioningContract.RUNTIME_TOKEN_HEX_LENGTH &&
            matches(Regex("(?i)^[0-9a-f]+$"))
    }

    private fun decode(value: String): String {
        return runCatching {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        }.getOrDefault(value)
    }

    private companion object {
        const val KEY_PRODUCT_MODEL = "productModel"
        const val KEY_FIRMWARE_VERSION = "firmwareVersion"
    }
}
