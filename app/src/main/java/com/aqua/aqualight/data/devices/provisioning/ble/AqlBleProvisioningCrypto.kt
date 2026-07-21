package com.aqua.aqualight.data.devices.provisioning.ble

import android.util.Base64
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import java.math.BigInteger
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * AquaLight BLE provisioning app-layer security.
 *
 * QR path: claim code proves factory ownership and derives the session key.
 * Physical reset path: the setup button opens a QR-free recovery window and
 * ephemeral P-256 ECDH derives the session key.
 */
object AqlBleProvisioningCrypto {

    private const val VERSION = 2
    private const val ALGORITHM = "A256GCM"
    private const val TRANSCRIPT_PREFIX = "AQL-BLE-PROVISIONING-V2"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val AES_ALGORITHM = "AES"
    private const val EC_ALGORITHM = "EC"
    private const val ECDH_ALGORITHM = "ECDH"
    private const val EC_CURVE = "secp256r1"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BYTES = 16
    private const val P256_PUBLIC_KEY_BYTES = 65
    private const val P256_COORDINATE_BYTES = 32
    private const val DIRECTION_APP_TO_DEVICE = "appToDevice"
    private const val DIRECTION_DEVICE_TO_APP = "deviceToApp"

    private val secureRandom = SecureRandom()

    data class DeviceInfo(
        val deviceUid: String,
        val deviceNonce: String,
        val sessionMode: String,
        val devicePublicKey: String
    )

    data class Session(
        val sessionMode: String,
        val appNonce: String,
        val deviceNonce: String,
        val deviceUid: String,
        val provisioningId: String,
        val key: ByteArray,
        var nextAppToDeviceSequence: Int = 1,
        var expectedDeviceToAppSequence: Int = 1
    )

    fun startSessionJson(draft: AqlProvisioningDraft, deviceInfo: DeviceInfo): Result<Pair<String, Session>> {
        return when (deviceInfo.sessionMode) {
            AqlBleProvisioningContract.SessionMode.QR_CLAIM_SECURE -> qrClaimStartSessionJson(draft, deviceInfo)
            AqlBleProvisioningContract.SessionMode.PHYSICAL_RESET_SECURE -> physicalResetStartSessionJson(draft, deviceInfo)
            else -> Result.failure(IllegalStateException("Secure provisioning sessionMode is not supported."))
        }
    }

    fun encryptJson(plaintextJson: String, session: Session, purpose: String): String {
        val sequence = session.nextAppToDeviceSequence
        require(sequence > 0) { "Secure provisioning envelope sequence is invalid." }

        val iv = ByteArray(GCM_IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(session.key, AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        cipher.updateAAD(aad(purpose, DIRECTION_APP_TO_DEVICE, sequence, session))

        val encryptedWithTag = cipher.doFinal(plaintextJson.toByteArray(Charsets.UTF_8))
        require(encryptedWithTag.size > GCM_TAG_BYTES) { "Encrypted provisioning payload is empty." }
        val ciphertext = encryptedWithTag.copyOfRange(0, encryptedWithTag.size - GCM_TAG_BYTES)
        val tag = encryptedWithTag.copyOfRange(encryptedWithTag.size - GCM_TAG_BYTES, encryptedWithTag.size)

        return JSONObject()
            .put("v", VERSION)
            .put("alg", ALGORITHM)
            .put(AqlBleProvisioningContract.Json.KEY_ENVELOPE_SEQUENCE, sequence)
            .put("iv", iv.toBase64())
            .put("ciphertext", ciphertext.toBase64())
            .put("tag", tag.toBase64())
            .toString()
            .also { session.nextAppToDeviceSequence = sequence + 1 }
    }

    fun decryptJson(raw: String, session: Session, purpose: String): Result<String> {
        return runCatching {
            val json = JSONObject(raw.trim())
            val version = json.optInt("v", 0)
            val algorithm = json.optString("alg").trim()
            require(version == VERSION && algorithm == ALGORITHM) {
                "Secure provisioning envelope is not supported."
            }

            val sequence = json.optInt(AqlBleProvisioningContract.Json.KEY_ENVELOPE_SEQUENCE, 0)
            require(sequence == session.expectedDeviceToAppSequence) {
                "Secure provisioning envelope sequence is invalid."
            }

            val iv = requiredString(json, "iv").fromBase64()
            val ciphertext = requiredString(json, "ciphertext").fromBase64()
            val tag = requiredString(json, "tag").fromBase64()
            require(iv.size == GCM_IV_BYTES) { "Secure provisioning envelope IV is invalid." }
            require(ciphertext.isNotEmpty()) { "Secure provisioning envelope ciphertext is empty." }
            require(tag.size == GCM_TAG_BYTES) { "Secure provisioning envelope tag is invalid." }

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(session.key, AES_ALGORITHM),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.updateAAD(aad(purpose, DIRECTION_DEVICE_TO_APP, sequence, session))
            String(cipher.doFinal(ciphertext + tag), Charsets.UTF_8).trim().also { plaintext ->
                require(plaintext.isNotBlank()) { "Secure provisioning envelope plaintext is empty." }
                session.expectedDeviceToAppSequence = sequence + 1
            }
        }
    }

    private fun qrClaimStartSessionJson(
        draft: AqlProvisioningDraft,
        deviceInfo: DeviceInfo
    ): Result<Pair<String, Session>> {
        return runCatching {
            val appNonce = draft.sessionId.trim()
            val deviceNonce = deviceInfo.deviceNonce.trim()
            val deviceUid = deviceInfo.deviceUid.trim()
            val provisioningId = provisioningIdFromRawQrPayload(draft.rawQrPayload)
            val claimCode = draft.claimCode.trim()
            val sessionMode = AqlBleProvisioningContract.SessionMode.QR_CLAIM_SECURE

            require(appNonce.isUuidV4()) { "App nonce is missing or invalid." }
            require(deviceNonce.isUuidV4()) { "Device nonce is missing or invalid." }
            require(deviceUid.isNotBlank()) { "Device UID is required for secure provisioning." }
            require(provisioningId.isNotBlank()) { "QR provisioning id is required for secure provisioning." }
            require(claimCode.length in AqlBleProvisioningContract.CLAIM_CODE_MIN_LENGTH..AqlBleProvisioningContract.CLAIM_CODE_MAX_LENGTH) {
                "QR claim code is missing or invalid."
            }

            val claimHashKey = sha256(claimCode.toByteArray(Charsets.UTF_8))
            val sessionKey = hmacSha256(
                key = claimHashKey,
                data = transcript(sessionMode, "key", deviceUid, provisioningId, deviceNonce, appNonce)
            )
            val proof = hmacSha256(
                key = claimHashKey,
                data = transcript(sessionMode, "proof", deviceUid, provisioningId, deviceNonce, appNonce)
            ).toHex()

            val session = Session(sessionMode, appNonce, deviceNonce, deviceUid, provisioningId, sessionKey)
            val json = JSONObject()
                .put(AqlBleProvisioningContract.Json.KEY_SECURITY_VERSION, VERSION)
                .put(AqlBleProvisioningContract.Json.KEY_SESSION_MODE, sessionMode)
                .put(AqlBleProvisioningContract.Json.KEY_APP_NONCE, appNonce)
                .put(AqlBleProvisioningContract.Json.KEY_DEVICE_NONCE, deviceNonce)
                .put(AqlBleProvisioningContract.Json.KEY_DEVICE_UID, deviceUid)
                .put(AqlBleProvisioningContract.Json.KEY_PROVISIONING_ID, provisioningId)
                .put(AqlBleProvisioningContract.Json.KEY_START_SESSION_PROOF, proof)
                .toString()
            json to session
        }
    }

    private fun physicalResetStartSessionJson(
        draft: AqlProvisioningDraft,
        deviceInfo: DeviceInfo
    ): Result<Pair<String, Session>> {
        return runCatching {
            val appNonce = draft.sessionId.trim()
            val deviceNonce = deviceInfo.deviceNonce.trim()
            val deviceUid = deviceInfo.deviceUid.trim()
            val sessionMode = AqlBleProvisioningContract.SessionMode.PHYSICAL_RESET_SECURE
            require(appNonce.isUuidV4()) { "App nonce is missing or invalid." }
            require(deviceNonce.isUuidV4()) { "Device nonce is missing or invalid." }
            require(deviceUid.isNotBlank()) { "Device UID is required for physical reset recovery." }
            require(deviceInfo.devicePublicKey.isNotBlank()) { "Device ECDH public key is missing." }

            val keyPair = generateEcKeyPair()
            val appPublicKey = (keyPair.public as ECPublicKey).toUncompressedBytes().toBase64()
            val devicePublicKey = decodeEcPublicKey(deviceInfo.devicePublicKey.fromBase64(), keyPair.public as ECPublicKey)
            val sharedSecret = KeyAgreement.getInstance(ECDH_ALGORITHM).run {
                init(keyPair.private)
                doPhase(devicePublicKey, true)
                generateSecret()
            }
            val sessionKey = hmacSha256(
                key = sharedSecret,
                data = transcript(sessionMode, "key", deviceUid, "", deviceNonce, appNonce)
            )
            val session = Session(sessionMode, appNonce, deviceNonce, deviceUid, "", sessionKey)

            val json = JSONObject()
                .put(AqlBleProvisioningContract.Json.KEY_SECURITY_VERSION, VERSION)
                .put(AqlBleProvisioningContract.Json.KEY_SESSION_MODE, sessionMode)
                .put(AqlBleProvisioningContract.Json.KEY_APP_NONCE, appNonce)
                .put(AqlBleProvisioningContract.Json.KEY_DEVICE_NONCE, deviceNonce)
                .put(AqlBleProvisioningContract.Json.KEY_DEVICE_UID, deviceUid)
                .put(AqlBleProvisioningContract.Json.KEY_APP_PUBLIC_KEY, appPublicKey)
                .toString()
            json to session
        }
    }

    private fun generateEcKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance(EC_ALGORITHM).run {
            initialize(ECGenParameterSpec(EC_CURVE), secureRandom)
            generateKeyPair()
        }
    }

    private fun decodeEcPublicKey(bytes: ByteArray, template: ECPublicKey): ECPublicKey {
        require(bytes.size == P256_PUBLIC_KEY_BYTES && bytes[0] == 0x04.toByte()) {
            "Device ECDH public key format is invalid."
        }
        val x = BigInteger(1, bytes.copyOfRange(1, 1 + P256_COORDINATE_BYTES))
        val y = BigInteger(1, bytes.copyOfRange(1 + P256_COORDINATE_BYTES, P256_PUBLIC_KEY_BYTES))
        return KeyFactory.getInstance(EC_ALGORITHM).generatePublic(
            ECPublicKeySpec(ECPoint(x, y), template.params)
        ) as ECPublicKey
    }

    private fun ECPublicKey.toUncompressedBytes(): ByteArray {
        return byteArrayOf(0x04) +
            w.affineX.toFixed32Bytes() +
            w.affineY.toFixed32Bytes()
    }

    private fun BigInteger.toFixed32Bytes(): ByteArray {
        val raw = toByteArray()
        return when {
            raw.size == P256_COORDINATE_BYTES -> raw
            raw.size == P256_COORDINATE_BYTES + 1 && raw[0] == 0.toByte() -> raw.copyOfRange(1, raw.size)
            raw.size < P256_COORDINATE_BYTES -> ByteArray(P256_COORDINATE_BYTES - raw.size) + raw
            else -> raw.copyOfRange(raw.size - P256_COORDINATE_BYTES, raw.size)
        }
    }

    private fun transcript(
        sessionMode: String,
        purpose: String,
        deviceUid: String,
        provisioningId: String,
        deviceNonce: String,
        appNonce: String
    ): ByteArray {
        return listOf(
            TRANSCRIPT_PREFIX,
            sessionMode,
            purpose,
            deviceUid,
            provisioningId,
            deviceNonce,
            appNonce
        ).joinToString(separator = "|").toByteArray(Charsets.UTF_8)
    }

    private fun aad(
        purpose: String,
        direction: String,
        sequence: Int,
        session: Session
    ): ByteArray {
        return listOf(
            TRANSCRIPT_PREFIX,
            "aad",
            session.sessionMode,
            purpose,
            direction,
            sequence.toString(),
            session.deviceUid,
            session.provisioningId,
            session.deviceNonce,
            session.appNonce
        ).joinToString(separator = "|").toByteArray(Charsets.UTF_8)
    }

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(data)
    }

    private fun provisioningIdFromRawQrPayload(rawQrPayload: String): String {
        val normalized = rawQrPayload.trim()
        if (normalized.isBlank()) return ""
        return runCatching {
            if (normalized.startsWith("{")) {
                JSONObject(normalized).optString(AqlBleProvisioningContract.Qr.KEY_PROVISIONING_ID).trim()
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
                val key = decode(part.substringBefore("=")).trim().lowercase(Locale.US)
                val value = decode(part.substringAfter("=")).trim()
                if (key.isNotBlank()) fields[key] = value
            }
        return fields
    }

    private fun requiredString(json: JSONObject, key: String): String {
        return json.optString(key).trim().takeIf { it.isNotBlank() }
            ?: error("Secure provisioning envelope field '$key' is missing.")
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        forEach { byte -> out.append(String.format(Locale.US, "%02x", byte)) }
        return out.toString()
    }

    private fun String.isUuidV4(): Boolean {
        return matches(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))
    }

    private fun decode(value: String): String {
        return runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
    }
}
