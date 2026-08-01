package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeJson
import org.json.JSONObject

internal object DeviceSecurityStatusParser {
    fun parseCommandStatus(
        data: JSONObject,
        expectedDeviceUid: DeviceUid
    ): DeviceSecurityStatus = parse(data, expectedDeviceUid, commandAliases = true)

    fun parseEmbeddedStatus(
        data: JSONObject,
        expectedDeviceUid: DeviceUid
    ): DeviceSecurityStatus = parse(data, expectedDeviceUid, commandAliases = false)

    private fun parse(
        data: JSONObject,
        expectedDeviceUid: DeviceUid,
        commandAliases: Boolean
    ): DeviceSecurityStatus {
        val dynamicPairing = DeviceRuntimeJson.booleanValue(data, FIELD_DYNAMIC_PAIRING_ENABLED)
        val paired = DeviceRuntimeJson.booleanValue(data, FIELD_PAIRED)
        DeviceRuntimeJson.requireExactKeys(
            data,
            expectedKeys(dynamicPairing, paired, commandAliases),
            STATUS_LABEL
        )
        if (commandAliases) validateCommandAliases(data)
        return DeviceSecurityStatus(
            tokenGateEnabled = DeviceRuntimeJson.booleanValue(data, "tokenGateEnabled"),
            dynamicPairingEnabled = dynamicPairing,
            paired = paired,
            runtime = runtimePolicy(data),
            ownership = ownershipPolicy(data),
            storage = credentialStorage(data),
            deviceUid = DeviceUid(DeviceRuntimeJson.stringValue(data, "deviceUid")),
            shortId = DeviceRuntimeJson.stringValue(data, "shortId"),
            serialNumber = DeviceRuntimeJson.stringValue(data, "serialNumber")
        ).also { status -> validate(status, expectedDeviceUid) }
    }

    private fun runtimePolicy(data: JSONObject): DeviceSecurityRuntimePolicy =
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

    private fun ownershipPolicy(data: JSONObject): DeviceSecurityOwnershipPolicy =
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

    private fun credentialStorage(data: JSONObject): DeviceSecurityCredentialStorage =
        DeviceSecurityCredentialStorage(
            backend = DeviceRuntimeJson.stringValue(data, "tokenStorageBackend"),
            format = DeviceRuntimeJson.stringValue(data, "tokenStorageFormat"),
            storedPlaintext = DeviceRuntimeJson.booleanValue(data, "tokenStoredPlaintext"),
            tokenFormat = DeviceRuntimeJson.stringValue(data, "tokenFormat"),
            tokenHexLength = DeviceRuntimeJson.intValue(data, "tokenHexLength"),
            tokenVersion = data.optionalLong(FIELD_TOKEN_VERSION, positive = true),
            pairedAtMs = data.optionalLong(FIELD_PAIRED_AT_MS, positive = false),
            lastRotatedAtMs = data.optionalLong(FIELD_LAST_ROTATED_AT_MS, positive = false),
            provisioningTokenPending = data.optionalBoolean(FIELD_PROVISIONING_TOKEN_PENDING)
        )

    private fun expectedKeys(
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

    private fun validate(status: DeviceSecurityStatus, expectedDeviceUid: DeviceUid) {
        require(status.deviceUid == expectedDeviceUid)
        require(status.runtime.transport == TRANSPORT_WEBSOCKET)
        require(status.runtime.authMessageType == AqlWsContract.TYPE_AUTH)
        require(status.runtime.authScheme == AqlWsContract.AUTH_SCHEME)
        require(!status.runtime.credentialSerialized)
        require(status.runtime.replayProtection == REPLAY_PROTECTION)
        require(status.ownership.initialOwnershipTransport == OWNERSHIP_TRANSPORT)
        require(status.ownership.firstTokenTransport == TOKEN_TRANSPORT)
        require(status.ownership.webSocketPairingCommand == PAIR_COMMAND)
        require(status.ownership.webSocketPairingCommandAuth == PAIR_COMMAND_AUTH)
        require(status.ownership.webSocketPairingPurpose == PAIR_COMMAND_PURPOSE)
        require(!status.ownership.publicFirstPairingSupported)
        require(!status.ownership.tokenReturnedByStatus)
        require(status.storage.backend == TOKEN_STORAGE_BACKEND)
        require(status.storage.format == TOKEN_STORAGE_FORMAT)
        require(!status.storage.storedPlaintext)
        require(status.storage.tokenFormat == TOKEN_FORMAT)
        require(status.storage.tokenHexLength == TOKEN_HEX_LENGTH)
        require(!status.dynamicPairingEnabled || status.storage.provisioningTokenPending != null)
        require(!status.paired || !status.dynamicPairingEnabled || status.storage.tokenVersion != null)
    }

    private fun JSONObject.optionalLong(key: String, positive: Boolean): Long? =
        if (has(key)) {
            DeviceRuntimeJson.longValue(this, key).also { value ->
                require(if (positive) value > 0L else value >= 0L)
            }
        } else {
            null
        }

    private fun JSONObject.optionalBoolean(key: String): Boolean? =
        if (has(key)) DeviceRuntimeJson.booleanValue(this, key) else null

    private const val STATUS_LABEL = "security.status.get.data"
    private const val FIELD_DYNAMIC_PAIRING_ENABLED = "dynamicPairingEnabled"
    private const val FIELD_PAIRED = "paired"
    private const val FIELD_TOKEN_VERSION = "tokenVersion"
    private const val FIELD_PAIRED_AT_MS = "pairedAtMs"
    private const val FIELD_LAST_ROTATED_AT_MS = "lastRotatedAtMs"
    private const val FIELD_PROVISIONING_TOKEN_PENDING = "provisioningTokenPending"
    private const val TRANSPORT_WEBSOCKET = "websocket"
    private const val REPLAY_PROTECTION = "session_nonce_and_monotonic_sequence"
    private const val OWNERSHIP_TRANSPORT = "ble_qr"
    private const val TOKEN_TRANSPORT = "ble_runtime_endpoint"
    private const val PAIR_COMMAND = "security.pair"
    private const val PAIR_COMMAND_AUTH = "authenticated"
    private const val PAIR_COMMAND_PURPOSE = "ownership_status_only"
    private const val TOKEN_STORAGE_BACKEND = "NVS"
    private const val TOKEN_STORAGE_FORMAT = "sha256_hash"
    private const val TOKEN_FORMAT = "64_hex"
    private const val TOKEN_HEX_LENGTH = 64

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
}
