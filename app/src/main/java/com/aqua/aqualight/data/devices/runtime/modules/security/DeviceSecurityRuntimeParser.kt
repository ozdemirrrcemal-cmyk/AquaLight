package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeJson
import org.json.JSONObject

internal object DeviceSecurityRuntimeParser {
    fun parseStatusResponse(
        expectedDeviceUid: DeviceUid,
        data: JSONObject
    ): DeviceSecurityStatus {
        val status = parseStatus(expectedDeviceUid, data, commandAliases = true)
        require(DeviceRuntimeJson.stringValue(data, FIELD_AUTH_MESSAGE_TYPE) == VALUE_AUTH)
        require(DeviceRuntimeJson.stringValue(data, FIELD_AUTH_SCHEME) == VALUE_HMAC_SHA256)
        require(!DeviceRuntimeJson.booleanValue(data, FIELD_CREDENTIAL_SERIALIZED))
        return status
    }

    fun parsePair(data: JSONObject): DeviceSecurityPairResult {
        DeviceRuntimeJson.requireExactKeys(data, PAIR_KEYS, LABEL_PAIR)
        require(DeviceRuntimeJson.stringValue(data, FIELD_OPERATION) == VALUE_PAIR)
        require(DeviceRuntimeJson.booleanValue(data, FIELD_PAIRED))
        require(!DeviceRuntimeJson.booleanValue(data, FIELD_TOKEN_RETURNED))
        require(DeviceRuntimeJson.stringValue(data, FIELD_RUNTIME_TRANSPORT) == VALUE_WEBSOCKET)
        require(DeviceRuntimeJson.stringValue(data, FIELD_AUTH_MESSAGE_TYPE) == VALUE_AUTH)
        require(DeviceRuntimeJson.stringValue(data, FIELD_AUTH_SCHEME) == VALUE_HMAC_SHA256)
        require(!DeviceRuntimeJson.booleanValue(data, FIELD_CREDENTIAL_SERIALIZED))
        require(!DeviceRuntimeJson.booleanValue(data, FIELD_CREDENTIAL_SERIALIZED_ON_WEBSOCKET))
        val rotationTransport = DeviceRuntimeJson.stringValue(
            data,
            FIELD_CREDENTIAL_ROTATION_TRANSPORT
        )
        require(rotationTransport == VALUE_BLE_RUNTIME_ENDPOINT)
        return DeviceSecurityPairResult(
            paired = true,
            tokenReturned = false,
            credentialRotationTransport = rotationTransport
        )
    }

    fun parseRevocation(
        expectedDeviceUid: DeviceUid,
        expectedOperation: DeviceSecurityRevocationAck.Operation,
        data: JSONObject
    ): DeviceSecurityRevocationAck {
        DeviceRuntimeJson.requireExactKeys(data, REVOCATION_KEYS, LABEL_REVOCATION)
        require(DeviceRuntimeJson.stringValue(data, FIELD_OPERATION) == expectedOperation.wireValue)
        require(!DeviceRuntimeJson.booleanValue(data, FIELD_PAIRED))
        require(!DeviceRuntimeJson.booleanValue(data, FIELD_CREDENTIAL_RETURNED))
        require(DeviceRuntimeJson.stringValue(data, FIELD_RUNTIME_TRANSPORT) == VALUE_WEBSOCKET)
        require(
            DeviceRuntimeJson.stringValue(data, FIELD_COMMAND) ==
                "security.${expectedOperation.action}"
        )
        DeviceRuntimeJson.stringValue(data, FIELD_MESSAGE)
        val status = parseStatus(
            expectedDeviceUid,
            DeviceRuntimeJson.objectValue(data, FIELD_STATUS),
            commandAliases = false
        )
        require(!status.paired)
        return DeviceSecurityRevocationAck(
            operation = expectedOperation,
            paired = false,
            credentialReturned = false,
            status = status
        )
    }

    private fun parseStatus(
        expectedDeviceUid: DeviceUid,
        data: JSONObject,
        commandAliases: Boolean
    ): DeviceSecurityStatus {
        val dynamic = DeviceRuntimeJson.booleanValue(data, FIELD_DYNAMIC_PAIRING_ENABLED)
        val paired = DeviceRuntimeJson.booleanValue(data, FIELD_PAIRED)
        val expectedKeys = when {
            dynamic && paired -> PAIRED_DYNAMIC_STATUS_KEYS
            dynamic -> DYNAMIC_STATUS_KEYS
            else -> STATIC_STATUS_KEYS
        } + if (commandAliases) STATUS_COMMAND_ALIAS_KEYS else emptySet()
        DeviceRuntimeJson.requireExactKeys(data, expectedKeys, LABEL_STATUS)

        require(DeviceRuntimeJson.stringValue(data, FIELD_RUNTIME_TRANSPORT) == VALUE_WEBSOCKET)
        require(DeviceRuntimeJson.stringValue(data, FIELD_RUNTIME_AUTH_MESSAGE_TYPE) == VALUE_AUTH)
        require(DeviceRuntimeJson.stringValue(data, FIELD_RUNTIME_AUTH_SCHEME) == VALUE_HMAC_SHA256)
        require(!DeviceRuntimeJson.booleanValue(data, FIELD_RUNTIME_CREDENTIAL_SERIALIZED))
        require(
            DeviceRuntimeJson.stringValue(data, FIELD_RUNTIME_REPLAY_PROTECTION) ==
                VALUE_REPLAY_PROTECTION
        )
        require(
            DeviceRuntimeJson.stringValue(data, FIELD_INITIAL_OWNERSHIP_TRANSPORT) ==
                VALUE_BLE_QR
        )
        require(
            DeviceRuntimeJson.stringValue(data, FIELD_FIRST_TOKEN_TRANSPORT) ==
                VALUE_BLE_RUNTIME_ENDPOINT
        )
        require(
            DeviceRuntimeJson.stringValue(data, FIELD_WEBSOCKET_PAIRING_COMMAND) ==
                VALUE_SECURITY_PAIR
        )
        require(
            DeviceRuntimeJson.stringValue(data, FIELD_WEBSOCKET_PAIRING_COMMAND_AUTH) ==
                VALUE_AUTHENTICATED
        )
        require(
            DeviceRuntimeJson.stringValue(data, FIELD_WEBSOCKET_PAIRING_PURPOSE) ==
                VALUE_OWNERSHIP_STATUS_ONLY
        )
        require(!DeviceRuntimeJson.booleanValue(data, FIELD_PUBLIC_FIRST_PAIRING_SUPPORTED))
        require(!DeviceRuntimeJson.booleanValue(data, FIELD_TOKEN_RETURNED_BY_STATUS))
        require(DeviceRuntimeJson.stringValue(data, FIELD_TOKEN_STORAGE_BACKEND) == VALUE_NVS)
        require(DeviceRuntimeJson.stringValue(data, FIELD_TOKEN_STORAGE_FORMAT) == VALUE_SHA256_HASH)
        require(!DeviceRuntimeJson.booleanValue(data, FIELD_TOKEN_STORED_PLAINTEXT))
        require(DeviceRuntimeJson.stringValue(data, FIELD_TOKEN_FORMAT) == VALUE_TOKEN_FORMAT)
        require(DeviceRuntimeJson.intValue(data, FIELD_TOKEN_HEX_LENGTH) == TOKEN_HEX_LENGTH)

        val reportedDeviceUid = DeviceUid(DeviceRuntimeJson.stringValue(data, FIELD_DEVICE_UID))
        require(reportedDeviceUid == expectedDeviceUid) {
            "security.status.get returned another deviceUid."
        }

        return DeviceSecurityStatus(
            tokenGateEnabled = DeviceRuntimeJson.booleanValue(data, FIELD_TOKEN_GATE_ENABLED),
            dynamicPairingEnabled = dynamic,
            paired = paired,
            mutatingCommandsRequireAuth = DeviceRuntimeJson.booleanValue(
                data,
                FIELD_MUTATING_COMMANDS_REQUIRE_AUTH
            ),
            deviceUid = reportedDeviceUid,
            shortId = DeviceRuntimeJson.stringValue(data, FIELD_SHORT_ID),
            serialNumber = DeviceRuntimeJson.stringValue(data, FIELD_SERIAL_NUMBER),
            tokenVersion = if (dynamic && paired) {
                DeviceRuntimeJson.longValue(data, FIELD_TOKEN_VERSION).also { require(it > 0L) }
            } else {
                null
            },
            pairedAtMillis = if (dynamic && paired) {
                DeviceRuntimeJson.longValue(data, FIELD_PAIRED_AT_MS).also { require(it >= 0L) }
            } else {
                null
            },
            lastRotatedAtMillis = if (dynamic && paired) {
                DeviceRuntimeJson.longValue(data, FIELD_LAST_ROTATED_AT_MS).also { require(it >= 0L) }
            } else {
                null
            },
            provisioningTokenPending = if (dynamic) {
                DeviceRuntimeJson.booleanValue(data, FIELD_PROVISIONING_TOKEN_PENDING)
            } else {
                false
            }
        )
    }

    private const val LABEL_STATUS = "security status"
    private const val LABEL_PAIR = "security.pair.data"
    private const val LABEL_REVOCATION = "security revocation data"

    private const val FIELD_TOKEN_GATE_ENABLED = "tokenGateEnabled"
    private const val FIELD_DYNAMIC_PAIRING_ENABLED = "dynamicPairingEnabled"
    private const val FIELD_PAIRED = "paired"
    private const val FIELD_RUNTIME_TRANSPORT = "runtimeTransport"
    private const val FIELD_RUNTIME_AUTH_MESSAGE_TYPE = "runtimeAuthMessageType"
    private const val FIELD_RUNTIME_AUTH_SCHEME = "runtimeAuthScheme"
    private const val FIELD_RUNTIME_CREDENTIAL_SERIALIZED = "runtimeCredentialSerialized"
    private const val FIELD_RUNTIME_REPLAY_PROTECTION = "runtimeReplayProtection"
    private const val FIELD_INITIAL_OWNERSHIP_TRANSPORT = "initialOwnershipTransport"
    private const val FIELD_FIRST_TOKEN_TRANSPORT = "firstTokenTransport"
    private const val FIELD_WEBSOCKET_PAIRING_COMMAND = "webSocketPairingCommand"
    private const val FIELD_WEBSOCKET_PAIRING_COMMAND_AUTH = "webSocketPairingCommandAuth"
    private const val FIELD_WEBSOCKET_PAIRING_PURPOSE = "webSocketPairingPurpose"
    private const val FIELD_PUBLIC_FIRST_PAIRING_SUPPORTED = "publicFirstPairingSupported"
    private const val FIELD_MUTATING_COMMANDS_REQUIRE_AUTH = "mutatingCommandsRequireAuth"
    private const val FIELD_TOKEN_RETURNED_BY_STATUS = "tokenReturnedByStatus"
    private const val FIELD_TOKEN_STORAGE_BACKEND = "tokenStorageBackend"
    private const val FIELD_TOKEN_STORAGE_FORMAT = "tokenStorageFormat"
    private const val FIELD_TOKEN_STORED_PLAINTEXT = "tokenStoredPlaintext"
    private const val FIELD_TOKEN_FORMAT = "tokenFormat"
    private const val FIELD_TOKEN_HEX_LENGTH = "tokenHexLength"
    private const val FIELD_DEVICE_UID = "deviceUid"
    private const val FIELD_SHORT_ID = "shortId"
    private const val FIELD_SERIAL_NUMBER = "serialNumber"
    private const val FIELD_TOKEN_VERSION = "tokenVersion"
    private const val FIELD_PAIRED_AT_MS = "pairedAtMs"
    private const val FIELD_LAST_ROTATED_AT_MS = "lastRotatedAtMs"
    private const val FIELD_PROVISIONING_TOKEN_PENDING = "provisioningTokenPending"
    private const val FIELD_AUTH_MESSAGE_TYPE = "authMessageType"
    private const val FIELD_AUTH_SCHEME = "authScheme"
    private const val FIELD_CREDENTIAL_SERIALIZED = "credentialSerialized"
    private const val FIELD_OPERATION = "operation"
    private const val FIELD_TOKEN_RETURNED = "tokenReturned"
    private const val FIELD_CREDENTIAL_ROTATION_TRANSPORT = "credentialRotationTransport"
    private const val FIELD_CREDENTIAL_SERIALIZED_ON_WEBSOCKET =
        "credentialSerializedOnWebSocket"
    private const val FIELD_CREDENTIAL_RETURNED = "credentialReturned"
    private const val FIELD_COMMAND = "command"
    private const val FIELD_MESSAGE = "message"
    private const val FIELD_STATUS = "status"

    private const val VALUE_WEBSOCKET = "websocket"
    private const val VALUE_AUTH = "auth"
    private const val VALUE_HMAC_SHA256 = "hmac-sha256"
    private const val VALUE_REPLAY_PROTECTION = "session_nonce_and_monotonic_sequence"
    private const val VALUE_BLE_QR = "ble_qr"
    private const val VALUE_BLE_RUNTIME_ENDPOINT = "ble_runtime_endpoint"
    private const val VALUE_SECURITY_PAIR = "security.pair"
    private const val VALUE_AUTHENTICATED = "authenticated"
    private const val VALUE_OWNERSHIP_STATUS_ONLY = "ownership_status_only"
    private const val VALUE_NVS = "NVS"
    private const val VALUE_SHA256_HASH = "sha256_hash"
    private const val VALUE_TOKEN_FORMAT = "64_hex"
    private const val VALUE_PAIR = "pair"
    private const val TOKEN_HEX_LENGTH = 64

    private val STATIC_STATUS_KEYS = setOf(
        FIELD_TOKEN_GATE_ENABLED,
        FIELD_DYNAMIC_PAIRING_ENABLED,
        FIELD_PAIRED,
        FIELD_RUNTIME_TRANSPORT,
        FIELD_RUNTIME_AUTH_MESSAGE_TYPE,
        FIELD_RUNTIME_AUTH_SCHEME,
        FIELD_RUNTIME_CREDENTIAL_SERIALIZED,
        FIELD_RUNTIME_REPLAY_PROTECTION,
        FIELD_INITIAL_OWNERSHIP_TRANSPORT,
        FIELD_FIRST_TOKEN_TRANSPORT,
        FIELD_WEBSOCKET_PAIRING_COMMAND,
        FIELD_WEBSOCKET_PAIRING_COMMAND_AUTH,
        FIELD_WEBSOCKET_PAIRING_PURPOSE,
        FIELD_PUBLIC_FIRST_PAIRING_SUPPORTED,
        FIELD_MUTATING_COMMANDS_REQUIRE_AUTH,
        FIELD_TOKEN_RETURNED_BY_STATUS,
        FIELD_TOKEN_STORAGE_BACKEND,
        FIELD_TOKEN_STORAGE_FORMAT,
        FIELD_TOKEN_STORED_PLAINTEXT,
        FIELD_TOKEN_FORMAT,
        FIELD_TOKEN_HEX_LENGTH,
        FIELD_DEVICE_UID,
        FIELD_SHORT_ID,
        FIELD_SERIAL_NUMBER
    )
    private val DYNAMIC_STATUS_KEYS = STATIC_STATUS_KEYS + FIELD_PROVISIONING_TOKEN_PENDING
    private val PAIRED_DYNAMIC_STATUS_KEYS = DYNAMIC_STATUS_KEYS + setOf(
        FIELD_TOKEN_VERSION,
        FIELD_PAIRED_AT_MS,
        FIELD_LAST_ROTATED_AT_MS
    )
    private val STATUS_COMMAND_ALIAS_KEYS = setOf(
        FIELD_AUTH_MESSAGE_TYPE,
        FIELD_AUTH_SCHEME,
        FIELD_CREDENTIAL_SERIALIZED
    )
    private val PAIR_KEYS = setOf(
        FIELD_OPERATION,
        FIELD_PAIRED,
        FIELD_TOKEN_RETURNED,
        FIELD_CREDENTIAL_ROTATION_TRANSPORT,
        FIELD_CREDENTIAL_SERIALIZED_ON_WEBSOCKET,
        FIELD_RUNTIME_TRANSPORT,
        FIELD_AUTH_MESSAGE_TYPE,
        FIELD_AUTH_SCHEME,
        FIELD_CREDENTIAL_SERIALIZED
    )
    private val REVOCATION_KEYS = setOf(
        FIELD_OPERATION,
        FIELD_PAIRED,
        FIELD_CREDENTIAL_RETURNED,
        FIELD_RUNTIME_TRANSPORT,
        FIELD_COMMAND,
        FIELD_MESSAGE,
        FIELD_STATUS
    )
}
