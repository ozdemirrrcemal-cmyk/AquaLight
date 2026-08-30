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

    private var secureSession: AqlBleProvisioningCrypto.Session? = null

    fun resetSecureSession() {
        secureSession = null
    }

    fun startSessionJson(
        draft: AqlProvisioningDraft,
        deviceInfo: AqlBleProvisioningCrypto.DeviceInfo
    ): Result<String> {
        return AqlBleProvisioningCrypto
            .startSessionJson(draft = draft, deviceInfo = deviceInfo)
            .map { (json, session) ->
                secureSession = session
                json
            }
    }

    fun wifiCredentialsJson(draft: AqlProvisioningDraft): Result<String> {
        return runCatching {
            val session = secureSession ?: error("Secure BLE provisioning session is not active.")
            val wifiCredentials = draft.wifiCredentials
            val plaintext = JSONObject()
                .put(AqlBleProvisioningContract.Json.KEY_WIFI_SSID, wifiCredentials.ssid)
                .put(AqlBleProvisioningContract.Json.KEY_WIFI_PASSWORD, wifiCredentials.password)
                .put(AqlBleProvisioningContract.Json.KEY_WIFI_UTC_OFFSET_MINUTES, wifiCredentials.utcOffsetMinutes)
                .apply {
                    if (wifiCredentials.bssid.isNotBlank()) put(AqlBleProvisioningContract.Json.KEY_WIFI_BSSID, wifiCredentials.bssid)
                    if (wifiCredentials.channel > 0) put(AqlBleProvisioningContract.Json.KEY_WIFI_CHANNEL, wifiCredentials.channel)
                    if (wifiCredentials.timezone.isNotBlank()) put(AqlBleProvisioningContract.Json.KEY_WIFI_TIMEZONE, wifiCredentials.timezone)
                }
                .toString()
            AqlBleProvisioningCrypto.encryptJson(plaintext, session, PURPOSE_WIFI_CREDENTIALS)
        }
    }

    fun finalizeSetupJson(handoff: AqlProvisioningRuntimeHandoff): Result<String> {
        return runCatching {
            val session = secureSession ?: error("Secure BLE provisioning session is not active.")
            val plaintext = JSONObject()
                .put(AqlBleProvisioningContract.Json.KEY_DEVICE_UID, handoff.deviceUid.value)
                .put(AqlBleProvisioningContract.Json.KEY_FINALIZE_ACCEPTED, true)
                .toString()
            AqlBleProvisioningCrypto.encryptJson(plaintext, session, PURPOSE_FINALIZE_SETUP)
        }
    }

    fun parseStatus(raw: String): AqlBleProvisioningStatusMessage {
        val normalizedRaw = raw.trim()
        if (normalizedRaw.isBlank()) {
            return AqlBleProvisioningStatusMessage(status = AqlProvisioningStatus.UNKNOWN, raw = raw)
        }

        val json = runCatching { JSONObject(normalizedRaw) }.getOrNull()
        if (json == null) {
            return AqlBleProvisioningStatusMessage(
                status = AqlProvisioningStatus.fromWireValue(normalizedRaw),
                raw = raw
            )
        }

        return AqlBleProvisioningStatusMessage(
            status = AqlProvisioningStatus.fromWireValue(json.optString(AqlBleProvisioningContract.Json.KEY_STATUS)),
            message = json.optString(AqlBleProvisioningContract.Json.KEY_MESSAGE).trim().ifBlank {
                json.optString(AqlBleProvisioningContract.Json.KEY_LAST_ERROR).trim()
            },
            errorCode = json.optString(AqlBleProvisioningContract.Json.KEY_ERROR_CODE).trim(),
            retryable = json.optBoolean(AqlBleProvisioningContract.Json.KEY_RETRYABLE, false),
            raw = raw
        )
    }

    fun parseRuntimeHandoff(raw: String, fallbackDeviceUid: String): Result<AqlProvisioningRuntimeHandoff> {
        return runCatching {
            val session = secureSession ?: error("Secure BLE provisioning session is not active.")
            val decryptedRaw = AqlBleProvisioningCrypto
                .decryptJson(raw = raw, session = session, purpose = PURPOSE_RUNTIME_ENDPOINT)
                .getOrThrow()
            val json = JSONObject(decryptedRaw.trim())
            val expectedDeviceUid = fallbackDeviceUid.trim().takeUnless { value -> value.isLikelyBleAddress() }.orEmpty()
            val deviceUidText = requiredJsonString(json, AqlBleProvisioningContract.Json.KEY_DEVICE_UID, "RuntimeEndpoint")
            require(expectedDeviceUid.isBlank() || deviceUidText.equals(expectedDeviceUid, ignoreCase = true)) {
                "RuntimeEndpoint deviceUid does not match the provisioning draft."
            }

            val ip = requiredJsonString(json, AqlBleProvisioningContract.Json.KEY_IP, "RuntimeEndpoint")
            val wsPort = requiredJsonInt(json, AqlBleProvisioningContract.Json.KEY_WS_PORT, "RuntimeEndpoint")
            val wsPath = requiredJsonString(json, AqlBleProvisioningContract.Json.KEY_WS_PATH, "RuntimeEndpoint")
            require(wsPath == AqlWsContract.DEFAULT_PATH) { "RuntimeEndpoint path is not supported: $wsPath" }
            val forbiddenMetadataKey = RUNTIME_METADATA_KEYS.firstOrNull { key -> json.has(key) }
            require(forbiddenMetadataKey == null) {
                "RuntimeEndpoint must not include product metadata field '$forbiddenMetadataKey'."
            }
            val runtimeToken = requiredJsonString(json, AqlBleProvisioningContract.Json.KEY_TOKEN, "RuntimeEndpoint")
            require(runtimeToken.isRuntimeTokenHex()) { "RuntimeEndpoint token is missing or invalid." }

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
                webSocketToken = runtimeToken
            )
        }
    }

    private fun requiredJsonString(json: JSONObject, key: String, label: String): String {
        return json.optString(key).trim().takeIf { value -> value.isNotBlank() }
            ?: error("$label field '$key' is missing.")
    }

    private fun requiredJsonInt(json: JSONObject, key: String, label: String): Int {
        val value = json.opt(key)
        val intValue = when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
        return intValue?.takeIf { number -> number > 0 }
            ?: error("$label field '$key' is missing or invalid.")
    }

    private fun String.isLikelyBleAddress(): Boolean {
        return matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))
    }

    private fun String.isRuntimeTokenHex(): Boolean {
        return length == AqlBleProvisioningContract.RUNTIME_TOKEN_HEX_LENGTH && matches(Regex("(?i)^[0-9a-f]+$"))
    }

    private fun decode(value: String): String {
        return runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
    }

    private companion object {
        const val PURPOSE_WIFI_CREDENTIALS = "wifiCredentials"
        const val PURPOSE_RUNTIME_ENDPOINT = "runtimeEndpoint"
        const val PURPOSE_FINALIZE_SETUP = "finalizeSetup"
        val RUNTIME_METADATA_KEYS = setOf(
            "productFamily",
            "productName",
            "productModel",
            "firmwareVersion"
        )
    }
}
