package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.json.JSONObject

internal object DeviceSecurityParsers {

    fun parseStatusResponse(
        expectedDeviceUid: DeviceUid,
        data: JSONObject
    ): DeviceSecurityStatusResponse {
        val status = parseStatus(
            expectedDeviceUid = expectedDeviceUid,
            json = data,
            additionalKeys = STATUS_RESPONSE_ALIAS_KEYS
        )
        require(data.requiredString("authMessageType") == AUTH_MESSAGE_TYPE)
        require(data.requiredString("authScheme") == AqlWsContract.AUTH_SCHEME)
        require(!data.requiredBoolean("credentialSerialized"))
        return DeviceSecurityStatusResponse(status)
    }

    fun parsePairResult(data: JSONObject): DeviceSecurityPairResult {
        data.requireExactKeys(PAIR_RESULT_KEYS, "security.pair.data")
        return DeviceSecurityPairResult(
            operation = data.requiredString("operation").also { require(it == "pair") },
            paired = data.requiredBoolean("paired").also(::requireTrue),
            tokenReturned = data.requiredBoolean("tokenReturned").also(::requireFalse),
            credentialRotationTransport = data.requiredString("credentialRotationTransport")
                .also { require(it == CREDENTIAL_ROTATION_TRANSPORT) },
            credentialSerializedOnWebSocket =
                data.requiredBoolean("credentialSerializedOnWebSocket").also(::requireFalse),
            runtimeTransport = data.requiredString("runtimeTransport")
                .also { require(it == RUNTIME_TRANSPORT) },
            authMessageType = data.requiredString("authMessageType")
                .also { require(it == AUTH_MESSAGE_TYPE) },
            authScheme = data.requiredString("authScheme")
                .also { require(it == AqlWsContract.AUTH_SCHEME) },
            credentialSerialized = data.requiredBoolean("credentialSerialized")
                .also(::requireFalse),
            command = data.requiredString("command").also { require(it == SECURITY_PAIR_COMMAND) },
            authenticatedSessionUsed = data.requiredBoolean("authenticatedSessionUsed")
                .also(::requireTrue)
        )
    }

    fun parseOwnershipResetResult(
        expectedDeviceUid: DeviceUid,
        expectedOperation: String,
        data: JSONObject
    ): DeviceSecurityOwnershipResetResult {
        require(expectedOperation == "unpair" || expectedOperation == "reset")
        data.requireExactKeys(OWNERSHIP_RESET_KEYS, "security.$expectedOperation.data")
        val expectedCommand = "security.$expectedOperation"
        val status = parseStatus(
            expectedDeviceUid = expectedDeviceUid,
            json = data.requiredObject("status")
        )
        require(!status.paired)
        require(status.tokenMetadata == null)
        require(!status.provisioningTokenPending)
        return DeviceSecurityOwnershipResetResult(
            operation = data.requiredString("operation").also { require(it == expectedOperation) },
            paired = data.requiredBoolean("paired").also(::requireFalse),
            credentialReturned = data.requiredBoolean("credentialReturned").also(::requireFalse),
            runtimeTransport = data.requiredString("runtimeTransport")
                .also { require(it == RUNTIME_TRANSPORT) },
            command = data.requiredString("command").also { require(it == expectedCommand) },
            message = data.requiredString("message").also { require(it == OWNERSHIP_RESET_MESSAGE) },
            status = status
        )
    }

    private fun parseStatus(
        expectedDeviceUid: DeviceUid,
        json: JSONObject,
        additionalKeys: Set<String> = emptySet()
    ): DeviceSecurityStatus {
        val paired = json.requiredBoolean("paired")
        val expectedKeys = STATUS_BASE_KEYS + additionalKeys +
            if (paired) TOKEN_METADATA_KEYS else emptySet()
        json.requireExactKeys(expectedKeys, "security status")

        val reportedDeviceUid = DeviceUid(json.requiredString("deviceUid"))
        require(reportedDeviceUid == expectedDeviceUid) {
            "Security status belongs to another device."
        }
        val tokenMetadata = if (paired) {
            DeviceSecurityTokenMetadata(
                tokenVersion = json.requiredUnsigned32("tokenVersion").also {
                    require(it > 0L) { "tokenVersion must be positive while paired." }
                },
                pairedAtMs = json.requiredUnsigned32("pairedAtMs"),
                lastRotatedAtMs = json.requiredUnsigned32("lastRotatedAtMs")
            )
        } else {
            null
        }

        return DeviceSecurityStatus(
            tokenGateEnabled = json.requiredBoolean("tokenGateEnabled").also(::requireTrue),
            dynamicPairingEnabled = json.requiredBoolean("dynamicPairingEnabled")
                .also(::requireTrue),
            paired = paired,
            runtimeTransport = json.requiredString("runtimeTransport")
                .also { require(it == RUNTIME_TRANSPORT) },
            runtimeAuthMessageType = json.requiredString("runtimeAuthMessageType")
                .also { require(it == AUTH_MESSAGE_TYPE) },
            runtimeAuthScheme = json.requiredString("runtimeAuthScheme")
                .also { require(it == AqlWsContract.AUTH_SCHEME) },
            runtimeCredentialSerialized = json.requiredBoolean("runtimeCredentialSerialized")
                .also(::requireFalse),
            runtimeReplayProtection = json.requiredString("runtimeReplayProtection")
                .also { require(it == REPLAY_PROTECTION) },
            initialOwnershipTransport = json.requiredString("initialOwnershipTransport")
                .also { require(it == INITIAL_OWNERSHIP_TRANSPORT) },
            firstTokenTransport = json.requiredString("firstTokenTransport")
                .also { require(it == CREDENTIAL_ROTATION_TRANSPORT) },
            webSocketPairingCommand = json.requiredString("webSocketPairingCommand")
                .also { require(it == SECURITY_PAIR_COMMAND) },
            webSocketPairingCommandAuth = json.requiredString("webSocketPairingCommandAuth")
                .also { require(it == "authenticated") },
            webSocketPairingPurpose = json.requiredString("webSocketPairingPurpose")
                .also { require(it == "ownership_status_only") },
            publicFirstPairingSupported = json.requiredBoolean("publicFirstPairingSupported")
                .also(::requireFalse),
            mutatingCommandsRequireAuth = json.requiredBoolean("mutatingCommandsRequireAuth")
                .also(::requireTrue),
            tokenReturnedByStatus = json.requiredBoolean("tokenReturnedByStatus")
                .also(::requireFalse),
            tokenStorageBackend = json.requiredString("tokenStorageBackend")
                .also { require(it == "NVS") },
            tokenStorageFormat = json.requiredString("tokenStorageFormat")
                .also { require(it == "sha256_hash") },
            tokenStoredPlaintext = json.requiredBoolean("tokenStoredPlaintext")
                .also(::requireFalse),
            tokenFormat = json.requiredString("tokenFormat").also { require(it == "64_hex") },
            tokenHexLength = json.requiredInt("tokenHexLength").also {
                require(it == RUNTIME_TOKEN_HEX_LENGTH)
            },
            deviceUid = reportedDeviceUid,
            shortId = json.requiredString("shortId"),
            serialNumber = json.requiredString("serialNumber"),
            provisioningTokenPending = json.requiredBoolean("provisioningTokenPending"),
            tokenMetadata = tokenMetadata
        )
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
        val actual = keys().asSequence().toSet()
        require(actual == expected) {
            "$label keys differ from firmware; expected=$expected actual=$actual"
        }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject =
        get(key) as? JSONObject ?: error("$key must be a JSON object.")

    private fun JSONObject.requiredString(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        require(value.isNotEmpty()) { "$key must not be empty." }
        require(value == value.trim()) { "$key must not contain surrounding whitespace." }
        require(value.none(Char::isISOControl)) { "$key must not contain control characters." }
        return value
    }

    private fun JSONObject.requiredBoolean(key: String): Boolean =
        get(key) as? Boolean ?: error("$key must be a boolean.")

    private fun JSONObject.requiredInt(key: String): Int {
        val number = get(key) as? Number ?: error("$key must be an integer.")
        val asLong = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == asLong.toDouble())
        require(asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return asLong.toInt()
    }

    private fun JSONObject.requiredUnsigned32(key: String): Long {
        val number = get(key) as? Number ?: error("$key must be an unsigned integer.")
        val asLong = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == asLong.toDouble())
        require(asLong in 0L..UINT32_MAX)
        return asLong
    }

    private fun requireTrue(value: Boolean) {
        require(value)
    }

    private fun requireFalse(value: Boolean) {
        require(!value)
    }

    private const val RUNTIME_TRANSPORT = "websocket"
    private const val AUTH_MESSAGE_TYPE = "auth"
    private const val REPLAY_PROTECTION = "session_nonce_and_monotonic_sequence"
    private const val INITIAL_OWNERSHIP_TRANSPORT = "ble_qr"
    private const val CREDENTIAL_ROTATION_TRANSPORT = "ble_runtime_endpoint"
    private const val SECURITY_PAIR_COMMAND = "security.pair"
    private const val RUNTIME_TOKEN_HEX_LENGTH = 64
    private const val UINT32_MAX = 4_294_967_295L
    private const val OWNERSHIP_RESET_MESSAGE =
        "runtime credential cleared; encrypted BLE ownership is required again"

    private val STATUS_BASE_KEYS = setOf(
        "tokenGateEnabled",
        "dynamicPairingEnabled",
        "paired",
        "runtimeTransport",
        "runtimeAuthMessageType",
        "runtimeAuthScheme",
        "runtimeCredentialSerialized",
        "runtimeReplayProtection",
        "initialOwnershipTransport",
        "firstTokenTransport",
        "webSocketPairingCommand",
        "webSocketPairingCommandAuth",
        "webSocketPairingPurpose",
        "publicFirstPairingSupported",
        "mutatingCommandsRequireAuth",
        "tokenReturnedByStatus",
        "tokenStorageBackend",
        "tokenStorageFormat",
        "tokenStoredPlaintext",
        "tokenFormat",
        "tokenHexLength",
        "deviceUid",
        "shortId",
        "serialNumber",
        "provisioningTokenPending"
    )
    private val TOKEN_METADATA_KEYS = setOf(
        "tokenVersion",
        "pairedAtMs",
        "lastRotatedAtMs"
    )
    private val STATUS_RESPONSE_ALIAS_KEYS = setOf(
        "authMessageType",
        "authScheme",
        "credentialSerialized"
    )
    private val PAIR_RESULT_KEYS = setOf(
        "operation",
        "paired",
        "tokenReturned",
        "credentialRotationTransport",
        "credentialSerializedOnWebSocket",
        "runtimeTransport",
        "authMessageType",
        "authScheme",
        "credentialSerialized",
        "command",
        "authenticatedSessionUsed"
    )
    private val OWNERSHIP_RESET_KEYS = setOf(
        "operation",
        "paired",
        "credentialReturned",
        "runtimeTransport",
        "command",
        "message",
        "status"
    )
}
