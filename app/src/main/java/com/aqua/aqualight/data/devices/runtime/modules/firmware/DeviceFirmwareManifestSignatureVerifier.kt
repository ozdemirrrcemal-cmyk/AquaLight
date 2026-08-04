package com.aqua.aqualight.data.devices.runtime.modules.firmware

import android.util.Base64
import com.aqua.aqualight.BuildConfig
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import org.json.JSONArray
import org.json.JSONObject

class DeviceFirmwareManifestSignatureVerifier(
    private val publicKeyPem: String = BuildConfig.AQL_OTA_MANIFEST_PUBLIC_KEY_PEM,
    private val expectedKeyId: String = BuildConfig.AQL_OTA_MANIFEST_KEY_ID
) {

    fun verifyAndParse(rawManifest: String): Result<DeviceFirmwareManifest> = runCatching {
        val root = JSONObject(rawManifest)
        val manifest = DeviceFirmwareManifestParser.parse(rawManifest).getOrThrow()
        DeviceFirmwareManifestContractValidator.validate(manifest)
        verify(root = root, signature = manifest.signature)
        manifest
    }

    fun verify(
        root: JSONObject,
        signature: DeviceFirmwareManifestSignature
    ) {
        require(signature.scheme == DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256) {
            "Unsupported OTA manifest signature scheme: ${signature.scheme}"
        }
        require(expectedKeyId.isNotBlank()) {
            "OTA manifest signature keyId is not configured in this Android build."
        }
        require(signature.keyId == expectedKeyId) {
            "OTA manifest signature keyId mismatch."
        }
        require(publicKeyPem.isNotBlank()) {
            "OTA manifest public key is not configured in this Android build."
        }

        val payload = canonicalManifestPayload(root)
        val payloadHash = sha256Hex(payload)
        require(payloadHash.equals(signature.payloadHash, ignoreCase = true)) {
            "OTA manifest payload hash does not match signature metadata."
        }

        val publicKey = publicKeyFromPem(publicKeyPem)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(payload)

        val signatureBytes = Base64.decode(signature.value, Base64.DEFAULT)
        require(verifier.verify(signatureBytes)) {
            "OTA manifest signature verification failed."
        }
    }

    companion object {
        fun canonicalManifestPayload(root: JSONObject): ByteArray {
            return canonicalizeObject(
                json = root,
                excludedRootKeys = setOf(DeviceFirmwareRuntimeContract.Signature.FIELD)
            ).toByteArray(StandardCharsets.UTF_8)
        }

        private fun canonicalize(value: Any?): String {
            return when (value) {
                null, JSONObject.NULL -> "null"
                is JSONObject -> canonicalizeObject(value)
                is JSONArray -> canonicalizeArray(value)
                is String -> canonicalJsonString(value)
                is Number -> value.toString()
                is Boolean -> value.toString()
                else -> canonicalJsonString(value.toString())
            }
        }

        private fun canonicalizeObject(
            json: JSONObject,
            excludedRootKeys: Set<String> = emptySet()
        ): String {
            val keys = buildList {
                val iterator = json.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    if (!excludedRootKeys.contains(key)) {
                        add(key)
                    }
                }
            }.sorted()

            return keys.joinToString(
                separator = ",",
                prefix = "{",
                postfix = "}"
            ) { key ->
                "${canonicalJsonString(key)}:${canonicalize(json.opt(key))}"
            }
        }

        private fun canonicalizeArray(array: JSONArray): String {
            return buildString {
                append('[')
                for (index in 0 until array.length()) {
                    if (index > 0) {
                        append(',')
                    }
                    append(canonicalize(array.opt(index)))
                }
                append(']')
            }
        }

        private fun canonicalJsonString(value: String): String {
            return JSONObject.quote(value)
                .replace("\\/", "/")
        }

        private fun publicKeyFromPem(pem: String): java.security.PublicKey {
            val base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\n", "")
                .replace("\r", "")
                .trim()

            val bytes = Base64.decode(base64, Base64.DEFAULT)
            return KeyFactory
                .getInstance("EC")
                .generatePublic(X509EncodedKeySpec(bytes))
        }

        private fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString(separator = "") { value ->
                "%02x".format(value.toInt() and 0xff)
            }
        }
    }
}
