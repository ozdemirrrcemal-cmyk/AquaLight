package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.json.JSONArray
import org.json.JSONObject

object DeviceFirmwareManifestParser {

    fun parse(raw: String): Result<DeviceFirmwareManifest> {
        return runCatching {
            val root = JSONObject(raw)
            val manifest = DeviceFirmwareManifest(
                schema = root.requiredString("schema"),
                brand = root.requiredString("brand"),
                channel = root.requiredString("channel"),
                version = root.requiredString("version"),
                tag = root.requiredString("tag"),
                releaseRepo = root.requiredString("releaseRepo"),
                generatedAt = root.optString("generatedAt", "").trim(),
                artifacts = parseArtifacts(root.optJSONArray("artifacts")),
                signature = parseSignature(root.requiredObject("signature"))
            )

            require(manifest.isSupportedSchema) {
                "Unsupported OTA manifest schema or brand: ${manifest.schema} / ${manifest.brand}"
            }
            require(manifest.artifacts.isNotEmpty()) {
                "OTA manifest does not contain any artifacts."
            }
            require(manifest.signature.scheme == DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256) {
                "Unsupported OTA manifest signature scheme: ${manifest.signature.scheme}"
            }
            require(manifest.signature.payloadHash.isSha256Hex()) {
                "OTA manifest signature payloadHash must be 64 hex characters."
            }
            require(manifest.signature.value.isNotBlank()) {
                "OTA manifest signature value is missing."
            }

            manifest
        }
    }

    private fun parseSignature(json: JSONObject): DeviceFirmwareManifestSignature {
        return DeviceFirmwareManifestSignature(
            scheme = json.requiredString("scheme"),
            keyId = json.requiredString("keyId"),
            payloadHash = json.requiredString("payloadHash").lowercase(),
            value = json.requiredString("value")
        )
    }

    private fun parseArtifacts(array: JSONArray?): List<DeviceFirmwareManifestArtifact> {
        require(array != null) { "OTA manifest artifacts array is missing." }

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val artifact = parseArtifact(item)
                validateArtifact(artifact)
                add(artifact)
            }
        }
    }

    private fun parseArtifact(json: JSONObject): DeviceFirmwareManifestArtifact {
        return DeviceFirmwareManifestArtifact(
            env = json.requiredString("env"),
            product = parseProduct(json.requiredObject("product")),
            compatibility = parseCompatibility(json.requiredObject("compatibility")),
            firmware = parseAsset(json.requiredObject("firmware")),
            factory = json.optJSONObject("factory")?.let { parseAsset(it) }
        )
    }

    private fun parseProduct(json: JSONObject): DeviceFirmwareManifestProduct {
        return DeviceFirmwareManifestProduct(
            productKey = json.requiredString("productKey"),
            productId = json.requiredString("productId"),
            brand = json.optString("brand", DeviceFirmwareRuntimeContract.Manifest.BRAND).trim(),
            family = json.requiredString("family"),
            line = json.optString("line", "").trim(),
            model = json.requiredString("model"),
            displayName = json.optString("displayName", "").trim(),
            skuCode = json.optString("skuCode", "").trim(),
            hardwareRevision = json.requiredString("hardwareRevision")
        )
    }

    private fun parseCompatibility(json: JSONObject): DeviceFirmwareCompatibility {
        return DeviceFirmwareCompatibility(
            productKey = json.requiredString("productKey"),
            productId = json.requiredString("productId"),
            family = json.requiredString("family"),
            line = json.optString("line", "").trim(),
            model = json.requiredString("model"),
            hardwareRevision = json.requiredString("hardwareRevision")
        )
    }

    private fun parseAsset(json: JSONObject): DeviceFirmwareAsset {
        return DeviceFirmwareAsset(
            filename = json.requiredString("filename"),
            url = json.requiredString("url"),
            sha256 = json.requiredString("sha256").lowercase(),
            size = json.requiredPositiveInt("size"),
            format = json.optString("format", "").trim(),
            otaSlotCompatible = json.optBoolean("otaSlotCompatible", false)
        )
    }

    private fun validateArtifact(artifact: DeviceFirmwareManifestArtifact) {
        require(artifact.product.productKey == artifact.compatibility.productKey) {
            "Manifest productKey mismatch for ${artifact.env}."
        }
        require(artifact.product.productId == artifact.compatibility.productId) {
            "Manifest productId mismatch for ${artifact.env}."
        }
        require(artifact.product.model == artifact.compatibility.model) {
            "Manifest model mismatch for ${artifact.env}."
        }
        require(artifact.product.hardwareRevision == artifact.compatibility.hardwareRevision) {
            "Manifest hardwareRevision mismatch for ${artifact.env}."
        }
        require(artifact.firmware.url.startsWith(DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX)) {
            "OTA firmware URL must target the official AquaLight release repository."
        }
        require(artifact.firmware.url.startsWith("https://")) {
            "OTA firmware URL must use HTTPS."
        }
        require(artifact.firmware.filename.endsWith("-ota.bin")) {
            "OTA firmware filename must end with -ota.bin."
        }
        require(artifact.firmware.sha256.isSha256Hex()) {
            "OTA firmware sha256 must be 64 hex characters."
        }
        require(artifact.firmware.size > 0) {
            "OTA firmware size must be greater than zero."
        }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject {
        return optJSONObject(key) ?: error("OTA manifest object '$key' is missing.")
    }

    private fun JSONObject.requiredString(key: String): String {
        return optString(key)
            .trim()
            .takeIf { value -> value.isNotBlank() }
            ?: error("OTA manifest field '$key' is missing.")
    }

    private fun JSONObject.requiredPositiveInt(key: String): Int {
        val number = when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        } ?: error("OTA manifest field '$key' is missing or invalid.")

        require(number in 1..Int.MAX_VALUE) {
            "OTA manifest field '$key' is outside Android/firmware supported range."
        }

        return number.toInt()
    }
}
