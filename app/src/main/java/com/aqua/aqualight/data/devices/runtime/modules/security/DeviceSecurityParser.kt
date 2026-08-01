package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeJson
import org.json.JSONObject

internal object DeviceSecurityParser {
    fun parseStatus(data: JSONObject, expectedDeviceUid: DeviceUid): DeviceSecurityStatus =
        parseStatus(data, expectedDeviceUid, commandAliases = true)

    fun parsePair(data: JSONObject): DeviceSecurityPairResult {
        DeviceRuntimeJson.requireExactKeys(data, PAIR_KEYS, PAIR_LABEL)
        require(DeviceRuntimeJson.stringValue(data, "operation") == "pair")
        return DeviceSecurityPairResult(
            paired = DeviceRuntimeJson.booleanValue(data, "paired"),
            tokenReturned = DeviceRuntimeJson.booleanValue(data, "tokenReturned"),
            runtimeTransport = DeviceRuntimeJson.stringValue(data, "runtimeTransport"),
            authMessageType = DeviceRuntimeJson.stringValue(data, "authMessageType"),
            authScheme = DeviceRuntimeJson.stringValue(data, "authScheme"),
            credentialSerialized = DeviceRuntimeJson.booleanValue(data, "credentialSerialized"),
            credentialRotationTransport = DeviceRuntimeJson.stringValue(
                data,
                "credentialRotationTransport"
            ),
            credentialSerializedOnWebSocket = DeviceRuntimeJson.booleanValue(
                data,
                "credentialSerializedOnWebSocket"
            )
        ).also(::validatePair)
    }

    fun parseRevocation(
        data: JSONObject,
        expectedDeviceUid: DeviceUid,
        expectedAction: String
    ): DeviceSecurityRevocationResult {
        DeviceRuntimeJson.requireExactKeys(data, REVOCATION_KEYS, REVOCATION_LABEL)
        val operation = DeviceRuntimeJson.stringValue(data, "operation")
        require(operation == expectedAction)
        require(!DeviceRuntimeJson.booleanValue(data, "paired"))
        require(!DeviceRuntimeJson.booleanValue(data, "credentialReturned"))
        require(DeviceRuntimeJson.stringValue(data, "runtimeTransport") == "websocket")
        val command = DeviceRuntimeJson.stringValue(data, "command")
        require(command == "${AqlWsContract.MODULE_SECURITY}.$expectedAction")
        val status = parseStatus(
            data = DeviceRuntimeJson.objectValue(data, "status"),
            expectedDeviceUid = expectedDeviceUid,
            commandAliases = false
        )
        require(!status.paired)
        return DeviceSecurityRevocationResult(
            operation = operation,
            command = command,
            message = DeviceRuntimeJson.stringValue(data, "message"),
            status = status
        )
    }

    private fun parseStatus(
        data: JSONObject,
        expectedDeviceUid: DeviceUid,
        commandAliases: Boolean
    ): DeviceSecurityStatus {
        val dynamicPairing = DeviceRuntimeJson.booleanValue(data, FIELD_DYNAMIC_PAIRING_ENABLED)
        val paired = DeviceRuntimeJson.booleanValue(data, FIELD_PAIRED)
        DeviceRuntimeJson.requireExactKeys(
            data,
            expectedStatusKeys(dynamicPairing, paired, commandAliases),
            STATUS_LABEL
        )
        if (commandAliases) validateCommandAliases(data)
        return DeviceSecurityStatus(
            tokenGateEnabled = DeviceRuntimeJson.booleanValue(data, "tokenGateEnabled"),
            dynamicPairingEnabled = dynamicPairing,
            paired = paired,
            runtime = parseRuntimePolicy(data),
            ownership = parseOwnershipPolicy(data),
            storage = parseCredentialStorage(data),
            deviceUid = DeviceUid(DeviceRuntimeJson.stringValue(data, "deviceUid")),
            shortId = DeviceRuntimeJson.stringValue(data, "shortId"),
            serialNumber = DeviceRuntimeJson.stringValue(data, "serialNumber")
        ).also { status -> validateStatus(status, expectedDeviceUid) }
    }

    private fun parseRuntimePolicy(data: JSONObject): DeviceSecurityRuntimePolicy =
        DeviceSecurityRuntimePolicy(
            transport = DeviceRuntimeJson.stringValue(data, "runtimeTransport"),
            authMessageType = DeviceRuntimeJson.stringValue(data, "runtimeAuthMessageType"),
            authScheme = DeviceRuntimeJson.stringValue(data, "runtimeAuthScheme"),
            credentialSerialized = DeviceRuntimeJson.booleanValue(
                data,
                "runtimeCredentialSerialized"
            ),
            replayProtection = DeviceRuntimeJson.stringValue(data, "runtimeReplayProtection"),
            mutatingCommandsRequireAuth = DeviceRuntimeJson.booleanValue(
                data,
                "mutatingCommandsRequireAuth"
            )
        )

    private fun parseOwnershipPolicy(data: JSONObject): DeviceSecurityOwnershipPolicy =
        DeviceSecurityOwnershipPolicy(
            initialOwnershipTransport = DeviceRuntimeJson.stringValue(
                data,
                "initialOwnershipTransport"
            ),
            firstTokenTransport = DeviceRuntimeJson.stringValue(data, "firstTokenTransport"),
            webSocketPairingCommand = DeviceRuntimeJson.stringValue(
                data,
                "webSocketPairingCommand"
            ),
            webSocketPairingCommandAuth = DeviceRuntimeJson.stringValue(
                data,
                "webSocketPairingCommandAuth"
            ),
            webSocketPairingPurpose = DeviceRuntimeJson.stringValue(
                data,
                "webSocketPairingPurpose"
            ),
            publicFirstPairingSupported = DeviceRuntimeJson.booleanValue(
                data,
                "publicFirstPairingSupported"
            ),
            tokenReturnedByStatus = DeviceRuntimeJson.booleanValue(data, "tokenReturnedByStatus")
        )

    private fun parseCredentialStorage(data: JSONObject): DeviceSecurityCredentialStorage =
        DeviceSecurityCredentialStorage(
            backend = DeviceRuntimeJson.stringValue(data, "tokenStorageBackend"),
            format = DeviceRuntimeJson.stringValue(data, "tokenStorageFormat"),
            storedPlaintext = DeviceRuntimeJson.booleanValue(data, "tokenStoredPlaintext"),
            tokenFormat = DeviceRuntimeJson.stringValue(data, "tokenFormat"),
            tokenHexLength = DeviceRuntimeJson.intValue(data, "tokenHexLength"),
            tokenVersion = data.optionalPositiveLong(FIELD_TOKEN_VERSION),
            pairedAtMs = data.optionalNonNegativeLong(FIELD_PAIRED_AT_MS),
            lastRotatedAtMs = data.optionalNonNegativeLong(FIELD_LAST_ROTATED_AT_MS),
            provisioningTokenPending = data.optionalBoolean(FIELD_PROVISIONING_TOKEN_PENDING)
        )

    private fun expectedStatusKeys(
        dynamicPairing: Boolean,
        paired: Boolean,
        commandAliases: Boolean
    ): Set<String> = buildSet {
        addAll(BASE_STATUS_KEYS)
        if (dynamicPairing) add(FIELD_PROVISIONING_TOKEN_PENDING)
        if (dynamicPairing && paired) addAll(PAIRED_METADATA_KEYS)
        if (commandAliases) addAll(STATUS_COMMAND_ALIAS_KEYS)
    }

    private fun validateCommandAliases(data: JSONObject) {
        require(DeviceRuntimeJson.stringValue(data, "authMessageType") == AqlWsContract.TYPE_AUTH)
        require(DeviceRuntimeJson.stringValue(data, "authScheme") == AqlWsContract.AUTH_SCHEME)
        require(!DeviceRuntimeJson.booleanValue(data, "credentialSerialized"))
    }

    private fun validateStatus(status: DeviceSecurityStatus, expectedDeviceUid: DeviceUid) {
        require(status.deviceUid == expectedDeviceUid)
        require(status.runtime.transport == "websocket")
        require(status.runtime.authMessageType == AqlWsContract.TYPE_AUTH)
        require(status.runtime.authScheme == AqlWsContract.AUTH_SCHEME)
        require(!status.runtime.credentialSerialized)
        require(status.runtime.replayProtection == "session_nonce_and_monotonic_sequence")
        require(status.ownership.initialOwnershipTransport == "ble_qr")
        require(status.ownership.firstTokenTransport == "ble_runtime_endpoint")
        require(status.ownership.webSocketPairingCommand == "security.pair")
        require(status.ownership.webSocketPairingCommandAuth == "authenticated")
        require(status.ownership.webSocketPairingPurpose == "ownership_status_only")
        require(!status.ownership.publicFirstPairingSupported)
        require(!status.ownership.tokenReturnedByStatus)
        require(status.storage.backend == "NVS")
        require(status.storage.format == "sha256_hash")
        require(!status.storage.storedPlaintext)
        require(status.storage.tokenFormat == "64_hex")
        require(status.storage.tokenHexLength == 64)
        require(!status.dynamicPairingEnabled || status.storage.provisioningTokenPending != null)
        require(!status.paired || !status.dynamicPairingEnabled || status.storage.tokenVersion != null)
    }

    private fun validatePair(result: DeviceSecurityPairResult) {
        require(result.paired)
        require(!result.tokenReturned)
        require(result.runtimeTransport == "websocket")
        require(result.authMessageType == AqlWsContract.TYPE_AUTH)
        require(result.authScheme == AqlWsContract.AUTH_SCHEME)
        require(!result.credentialSerialized)
        require(result.credentialRotationTransport == "ble_runtime_endpoint")
        require(!result.credentialSerializedOnWebSocket)
    }

    private fun JSONObject.optionalPositiveLong(key: String): Long? =
        if (has(key)) {
            DeviceRuntimeJson.longValue(this, key).also { value -> require(value > 0L) }
        } else {
            null
        }

    private fun JSONObject.optionalNonNegativeLong(key: String): Long? =
        if (has(key)) {
            DeviceRuntimeJson.longValue(this, key).also { value -> require(value >= 0L) }
        } else {
            null
        }

    private fun JSONObject.optionalBoolean(key: String): Boolean? =
        if (has(key)) DeviceRuntimeJson.booleanValue(this, key) else null

    private const val STATUS_LABEL = "security.status.get.data"
    private const val PAIR_LABEL = "security.pair.data"
    private const val REVOCATION_LABEL = "security.revoke.data"
    private const val FIELD_DYNAMIC_PAIRING_ENABLED = "dynamicPairingEnabled"
    private const val FIELD_PAIRED = "paired"
    private const val FIELD_TOKEN_VERSION = "tokenVersion"
    private const val FIELD_PAIRED_AT_MS = "pairedAtMs"
    private const val FIELD_LAST_ROTATED_AT_MS = "lastRotatedAtMs"
    private const val FIELD_PROVISIONING_TOKEN_PENDING = "provisioningTokenPending"

    private val PAIRED_METADATA_KEYS = setOf(
        FIELD_TOKEN_VERSION,
        FIELD_PAIRED_AT_MS,
        FIELD_LAST_ROTATED_AT_MS
    )
    private val STATUS_COMMAND_ALIAS_KEYS = setOf(
        "authMessageType",
        "authScheme",
        "credentialSerialized"
    )
    private val BASE_STATUS_KEYS = setOf(
        "tokenGateEnabled", FIELD_DYNAMIC_PAIRING_ENABLED, FIELD_PAIRED,
        "runtimeTransport", "runtimeAuthMessageType", "runtimeAuthScheme",
        "runtimeCredentialSerialized", "runtimeReplayProtection", "initialOwnershipTransport",
        "firstTokenTransport", "webSocketPairingCommand", "webSocketPairingCommandAuth",
        "webSocketPairingPurpose", "publicFirstPairingSupported", "mutatingCommandsRequireAuth",
        "tokenReturnedByStatus", "tokenStorageBackend", "tokenStorageFormat",
        "tokenStoredPlaintext", "tokenFormat", "tokenHexLength", "deviceUid", "shortId",
        "serialNumber"
    )
    private val PAIR_KEYS = setOf(
        "operation", FIELD_PAIRED, "tokenReturned", "credentialRotationTransport",
        "credentialSerializedOnWebSocket", "runtimeTransport", "authMessageType", "authScheme",
        "credentialSerialized"
    )
    private val REVOCATION_KEYS = setOf(
        "operation", FIELD_PAIRED, "credentialReturned", "runtimeTransport",
        "command", "message", "status"
    )
}
