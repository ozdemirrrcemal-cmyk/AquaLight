package com.aqua.aqualight.data.devices.provisioning.qr

import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.net.URLDecoder
import java.util.Locale
import org.json.JSONObject

class AqlProvisioningQrParser {

    fun parse(raw: String): Result<AqlProvisioningQrPayload> {
        return runCatching {
            val normalizedRaw = raw.trim()
            require(normalizedRaw.isNotBlank()) { "QR payload is blank." }
            require(normalizedRaw.toByteArray(Charsets.UTF_8).size <= AqlBleProvisioningContract.QR_MAX_BYTES) {
                "QR payload is too large."
            }

            val fields = parseFields(normalizedRaw)
            val version = requiredField(fields, AqlBleProvisioningContract.Qr.KEY_VERSION).toIntOrNull()
                ?: error("QR contract version is invalid.")

            require(version == AqlBleProvisioningContract.CONTRACT_VERSION) {
                "Unsupported QR contract version: $version"
            }

            val brand = requiredField(fields, AqlBleProvisioningContract.Qr.KEY_BRAND)
            require(brand.equals(AqlBleProvisioningContract.BRAND, ignoreCase = true)) {
                "Unsupported QR brand: $brand"
            }

            val claimCode = requiredField(fields, AqlBleProvisioningContract.Qr.KEY_CLAIM_CODE)
            require(claimCode.length in AqlBleProvisioningContract.CLAIM_CODE_MIN_LENGTH..AqlBleProvisioningContract.CLAIM_CODE_MAX_LENGTH) {
                "Claim code length is invalid."
            }

            AqlProvisioningQrPayload(
                version = version,
                brand = brand,
                model = requiredField(fields, AqlBleProvisioningContract.Qr.KEY_MODEL),
                hardwareRevision = fields[AqlBleProvisioningContract.Qr.KEY_HARDWARE_REVISION].orEmpty(),
                deviceUid = DeviceUid(requiredField(fields, AqlBleProvisioningContract.Qr.KEY_DEVICE_UID)),
                productId = requiredField(fields, AqlBleProvisioningContract.Qr.KEY_PRODUCT_ID),
                claimCode = claimCode,
                bleName = requiredField(fields, AqlBleProvisioningContract.Qr.KEY_BLE_NAME),
                raw = normalizedRaw,
                fields = fields
            )
        }
    }

    private fun parseFields(raw: String): Map<String, String> {
        return if (raw.startsWith("{")) {
            parseJsonFields(raw)
        } else {
            parseQueryFields(raw)
        }
    }

    private fun parseJsonFields(raw: String): Map<String, String> {
        val json = JSONObject(raw)
        val fields = mutableMapOf<String, String>()
        val keys = json.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            fields[key.trim().lowercase(Locale.US)] = json.optString(key).trim()
        }

        return fields
    }

    private fun parseQueryFields(raw: String): Map<String, String> {
        val query = raw.substringAfter("?", raw)
        val fields = mutableMapOf<String, String>()

        query.split("&")
            .asSequence()
            .filter { part -> part.isNotBlank() && part.contains("=") }
            .forEach { part ->
                val key = part.substringBefore("=")
                val value = part.substringAfter("=")
                fields[decode(key).trim().lowercase(Locale.US)] = decode(value).trim()
            }

        return fields
    }

    private fun requiredField(
        fields: Map<String, String>,
        key: String
    ): String {
        return fields[key]
            ?.trim()
            ?.takeIf { value -> value.isNotBlank() }
            ?: error("QR field '$key' is missing.")
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}
