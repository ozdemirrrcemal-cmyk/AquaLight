package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class AqlWsHelloChallenge(
    val id: String,
    val deviceUid: String,
    val sessionId: String,
    val serverNonce: String,
    val firmwareVersion: String
)

internal data class AqlWsPendingAuthentication(
    val requestId: String,
    val hello: AqlWsHelloChallenge,
    val clientNonce: String,
    val clientProof: String,
    val expectedServerProof: String,
    val sessionKey: ByteArray
) : AutoCloseable {
    override fun close() {
        Arrays.fill(sessionKey, 0)
    }
}

internal data class AqlWsSecurityEnvelope(
    val sessionId: String,
    val sequence: Long,
    val mac: String
)

internal data class AqlWsMacFrame(
    val id: String,
    val type: String,
    val module: String,
    val action: String,
    val dataBase64Url: String,
    val status: Int? = null,
    val ok: Boolean? = null,
    val errorCode: String = "",
    val errorField: String = "",
    val errorMessage: String = ""
)

internal object AqlWsCrypto {
    private const val HMAC_SHA_256 = "HmacSHA256"
    private const val SHA_256 = "SHA-256"
    private const val CLIENT_AUTH_LABEL = "client-auth"
    private const val SERVER_AUTH_LABEL = "server-auth"
    private const val SESSION_KEY_LABEL = "session-key"
    private const val MESSAGE_LABEL = "message"
    private const val CLIENT_TO_DEVICE = "c2d"
    private const val DEVICE_TO_CLIENT = "d2c"
    private val runtimeTokenRegex = Regex("^[0-9a-fA-F]{64}$")
    private val secureRandom = SecureRandom()

    fun prepareAuthentication(
        hello: AqlWsHelloChallenge,
        runtimeToken: String,
        requestId: String,
        clientNonce: String = randomNonceHex()
    ): AqlWsPendingAuthentication {
        val normalizedToken = runtimeToken.trim()
        require(runtimeTokenRegex.matches(normalizedToken)) {
            "Stored runtime credential is invalid."
        }

        val credentialKey = MessageDigest.getInstance(SHA_256)
            .digest(normalizedToken.toByteArray(StandardCharsets.UTF_8))
        return try {
            val transcriptFields = listOf(
                hello.deviceUid,
                hello.sessionId,
                hello.serverNonce,
                clientNonce,
                requestId
            )
            val clientProof = hmacHex(
                key = credentialKey,
                payload = canonical(CLIENT_AUTH_LABEL, transcriptFields)
            )
            val expectedServerProof = hmacHex(
                key = credentialKey,
                payload = canonical(SERVER_AUTH_LABEL, transcriptFields)
            )
            val sessionKey = hmac(
                key = credentialKey,
                payload = canonical(SESSION_KEY_LABEL, transcriptFields)
            )

            AqlWsPendingAuthentication(
                requestId = requestId,
                hello = hello,
                clientNonce = clientNonce,
                clientProof = clientProof,
                expectedServerProof = expectedServerProof,
                sessionKey = sessionKey
            )
        } finally {
            Arrays.fill(credentialKey, 0)
        }
    }

    fun serverProofMatches(
        pending: AqlWsPendingAuthentication,
        receivedProof: String
    ): Boolean {
        val expected = pending.expectedServerProof.hexToBytesOrNull() ?: return false
        val received = receivedProof.hexToBytesOrNull() ?: return false
        return try {
            MessageDigest.isEqual(expected, received)
        } finally {
            Arrays.fill(expected, 0)
            Arrays.fill(received, 0)
        }
    }

    fun clientMessageMac(
        sessionKey: ByteArray,
        sessionId: String,
        sequence: Long,
        frame: AqlWsMacFrame
    ): String = messageMac(
        sessionKey = sessionKey,
        direction = CLIENT_TO_DEVICE,
        sessionId = sessionId,
        sequence = sequence,
        frame = frame
    )

    fun deviceMessageMac(
        sessionKey: ByteArray,
        sessionId: String,
        sequence: Long,
        frame: AqlWsMacFrame
    ): String = messageMac(
        sessionKey = sessionKey,
        direction = DEVICE_TO_CLIENT,
        sessionId = sessionId,
        sequence = sequence,
        frame = frame
    )

    fun constantTimeHexEquals(expectedHex: String, receivedHex: String): Boolean {
        val expected = expectedHex.hexToBytesOrNull() ?: return false
        val received = receivedHex.hexToBytesOrNull() ?: return false
        return try {
            MessageDigest.isEqual(expected, received)
        } finally {
            Arrays.fill(expected, 0)
            Arrays.fill(received, 0)
        }
    }

    fun randomNonceHex(): String {
        val bytes = ByteArray(AqlWsContract.Limit.NONCE_HEX_CHARS / 2)
        secureRandom.nextBytes(bytes)
        return try {
            bytes.toHex()
        } finally {
            Arrays.fill(bytes, 0)
        }
    }

    private fun messageMac(
        sessionKey: ByteArray,
        direction: String,
        sessionId: String,
        sequence: Long,
        frame: AqlWsMacFrame
    ): String {
        return hmacHex(
            key = sessionKey,
            payload = canonical(
                MESSAGE_LABEL,
                listOf(
                    direction,
                    sessionId,
                    sequence.toString(),
                    frame.id,
                    frame.type,
                    frame.module,
                    frame.action,
                    frame.dataBase64Url,
                    frame.status?.toString().orEmpty(),
                    when (frame.ok) {
                        true -> "1"
                        false -> "0"
                        null -> ""
                    },
                    frame.errorCode,
                    frame.errorField,
                    frame.errorMessage
                )
            )
        )
    }

    private fun canonical(label: String, fields: List<String>): ByteArray {
        val builder = StringBuilder(PROTOCOL_PREFIX.length + label.length + 128)
        builder.append(PROTOCOL_PREFIX).append('\n')
        builder.append(label).append('\n')
        fields.forEach { value ->
            val byteLength = value.toByteArray(StandardCharsets.UTF_8).size
            builder.append(byteLength).append(':').append(value).append('\n')
        }
        return builder.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun hmacHex(key: ByteArray, payload: ByteArray): String =
        hmac(key, payload).let { bytes ->
            try {
                bytes.toHex()
            } finally {
                Arrays.fill(bytes, 0)
            }
        }

    private fun hmac(key: ByteArray, payload: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA_256)
        mac.init(SecretKeySpec(key, HMAC_SHA_256))
        return mac.doFinal(payload)
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length != AqlWsContract.Limit.MAC_HEX_CHARS || any { !it.isHexDigit() }) {
            return null
        }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private const val PROTOCOL_PREFIX = "AQL-WS-V2"
}

internal class AqlWsSecureSession private constructor(
    val sessionId: String,
    private val sessionKey: ByteArray
) : AutoCloseable {
    private var nextClientSequence = 1L
    private var nextDeviceSequence = 1L
    private var closed = false

    @Synchronized
    fun signClient(frame: AqlWsMacFrame): AqlWsSecurityEnvelope {
        check(!closed) { "WebSocket security session is closed." }
        check(nextClientSequence <= AqlWsContract.Limit.MAX_SEQUENCE) {
            "WebSocket client sequence is exhausted."
        }
        val sequence = nextClientSequence
        val mac = AqlWsCrypto.clientMessageMac(
            sessionKey = sessionKey,
            sessionId = sessionId,
            sequence = sequence,
            frame = frame
        )
        nextClientSequence += 1L
        return AqlWsSecurityEnvelope(sessionId, sequence, mac)
    }

    @Synchronized
    fun verifyDevice(
        security: AqlWsSecurityEnvelope,
        frame: AqlWsMacFrame
    ): Boolean {
        if (
            closed ||
            security.sessionId != sessionId ||
            security.sequence != nextDeviceSequence ||
            security.sequence !in 1..AqlWsContract.Limit.MAX_SEQUENCE
        ) {
            return false
        }

        val expected = AqlWsCrypto.deviceMessageMac(
            sessionKey = sessionKey,
            sessionId = sessionId,
            sequence = security.sequence,
            frame = frame
        )
        if (!AqlWsCrypto.constantTimeHexEquals(expected, security.mac)) {
            return false
        }

        nextDeviceSequence += 1L
        return true
    }

    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
            Arrays.fill(sessionKey, 0)
        }
    }

    companion object {
        fun from(pending: AqlWsPendingAuthentication): AqlWsSecureSession {
            val keyCopy = pending.sessionKey.copyOf()
            return AqlWsSecureSession(
                sessionId = pending.hello.sessionId,
                sessionKey = keyCopy
            )
        }
    }
}
