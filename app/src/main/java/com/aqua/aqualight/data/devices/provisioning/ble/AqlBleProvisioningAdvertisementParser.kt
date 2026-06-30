package com.aqua.aqualight.data.devices.provisioning.ble

import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import java.net.URLDecoder
import java.util.Locale
import org.json.JSONObject

class AqlBleProvisioningAdvertisementParser {

    fun parse(
        scanRecord: ScanRecord?,
        fallbackBleName: String
    ): AqlBleProvisioningAdvertisement {
        val fields = linkedMapOf<String, String>()
        val payloads = mutableListOf<String>()

        scanRecord?.serviceData
            ?.forEach { (uuid, payload) ->
                if (uuid == PROVISIONING_SERVICE_UUID) {
                    decodePayload(payload)?.let { decoded ->
                        payloads += decoded
                        fields.putAll(parseFields(decoded))
                    }
                }
            }

        val manufacturerData = scanRecord?.manufacturerSpecificData
        if (manufacturerData != null) {
            for (index in 0 until manufacturerData.size()) {
                decodePayload(manufacturerData.valueAt(index))?.let { decoded ->
                    payloads += decoded
                    fields.putAll(parseFields(decoded))
                }
            }
        }

        val advertisedName = scanRecord?.deviceName.orEmpty()
        val bleName = firstValue(
            fields,
            "ble",
            "bleName",
            "ble_name"
        )
            .ifBlank { advertisedName }
            .ifBlank { fallbackBleName }

        return AqlBleProvisioningAdvertisement(
            deviceUid = firstValue(
                fields,
                "uid",
                "deviceUid",
                "device_uid"
            ),
            productName = firstValue(
                fields,
                "name",
                "productName",
                "product_name",
                "displayName",
                "display_name",
                "product"
            ),
            model = firstValue(
                fields,
                "model",
                "modelName",
                "model_name"
            ),
            serialNumber = firstValue(
                fields,
                "serial",
                "serialNo",
                "serial_no",
                "serialNumber",
                "serial_number",
                "sn"
            ),
            claimState = firstValue(
                fields,
                "claimState",
                "claim_state",
                "setupState",
                "setup_state",
                "status",
                "state"
            ),
            bleName = bleName,
            rawPayload = payloads.joinToString(separator = "\n"),
            fields = fields
        )
    }

    private fun decodePayload(payload: ByteArray?): String? {
        if (payload == null || payload.isEmpty()) {
            return null
        }

        val decoded = runCatching {
            String(payload, Charsets.UTF_8)
                .trim()
                .trim('\u0000')
        }.getOrNull().orEmpty()

        return decoded.takeIf { value ->
            value.isNotBlank() &&
                value.any { char -> char.isLetterOrDigit() }
        }
    }

    private fun parseFields(payload: String): Map<String, String> {
        val normalized = payload.trim()
        if (normalized.isBlank()) {
            return emptyMap()
        }

        return if (normalized.startsWith("{")) {
            parseJsonFields(normalized)
        } else {
            parseDelimitedFields(normalized)
        }
    }

    private fun parseJsonFields(payload: String): Map<String, String> {
        return runCatching {
            val json = JSONObject(payload)
            val fields = linkedMapOf<String, String>()
            val keys = json.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                fields[normalizeKey(key)] = json.optString(key).trim()
            }

            fields
        }.getOrDefault(emptyMap())
    }

    private fun parseDelimitedFields(payload: String): Map<String, String> {
        return payload
            .replace(";", "&")
            .replace(",", "&")
            .split("&")
            .asSequence()
            .filter { part -> part.contains("=") }
            .map { part ->
                val key = part.substringBefore("=")
                val value = part.substringAfter("=")
                normalizeKey(decode(key)) to decode(value).trim()
            }
            .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
            .toMap(linkedMapOf())
    }

    private fun firstValue(
        fields: Map<String, String>,
        vararg keys: String
    ): String {
        return keys
            .asSequence()
            .map { key -> fields[normalizeKey(key)].orEmpty().trim() }
            .firstOrNull { value -> value.isNotBlank() }
            .orEmpty()
    }

    private fun normalizeKey(key: String): String {
        return key
            .trim()
            .replace("-", "_")
            .lowercase(Locale.US)
    }

    private fun decode(value: String): String {
        return runCatching {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        }.getOrDefault(value)
    }

    private companion object {
        val PROVISIONING_SERVICE_UUID: ParcelUuid =
            ParcelUuid.fromString(AqlBleProvisioningContract.SERVICE_UUID)
    }
}
