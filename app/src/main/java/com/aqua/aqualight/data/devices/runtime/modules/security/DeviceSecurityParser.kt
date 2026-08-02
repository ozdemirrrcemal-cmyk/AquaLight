package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeJson
import org.json.JSONObject

internal object DeviceSecurityParser {
    fun parseStatus(data: JSONObject, expectedDeviceUid: DeviceUid): DeviceSecurityStatus =
        DeviceSecurityStatusParser.parseCommandStatus(data, expectedDeviceUid)

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
        require(DeviceRuntimeJson.stringValue(data, "runtimeTransport") == TRANSPORT_WEBSOCKET)
        val command = DeviceRuntimeJson.stringValue(data, "command")
        require(command == "${AqlWsContract.MODULE_SECURITY}.$expectedAction")
        val status = DeviceSecurityStatusParser.parseEmbeddedStatus(
            DeviceRuntimeJson.objectValue(data, "status"),
            expectedDeviceUid
        )
        require(!status.paired)
        return DeviceSecurityRevocationResult(
            operation = operation,
            command = command,
            message = DeviceRuntimeJson.stringValue(data, "message"),
            status = status
        )
    }

    private fun validatePair(result: DeviceSecurityPairResult) {
        require(result.paired)
        require(!result.tokenReturned)
        require(result.runtimeTransport == TRANSPORT_WEBSOCKET)
        require(result.authMessageType == AqlWsContract.TYPE_AUTH)
        require(result.authScheme == AqlWsContract.AUTH_SCHEME)
        require(!result.credentialSerialized)
        require(result.credentialRotationTransport == TOKEN_TRANSPORT)
        require(!result.credentialSerializedOnWebSocket)
    }

    private const val PAIR_LABEL = "security.pair.data"
    private const val REVOCATION_LABEL = "security.revoke.data"
    private const val TRANSPORT_WEBSOCKET = "websocket"
    private const val TOKEN_TRANSPORT = "ble_runtime_endpoint"

    private val PAIR_KEYS = setOf(
        "operation", "paired", "tokenReturned", "credentialRotationTransport",
        "credentialSerializedOnWebSocket", "runtimeTransport", "authMessageType", "authScheme",
        "credentialSerialized"
    )
    private val REVOCATION_KEYS = setOf(
        "operation", "paired", "credentialReturned", "runtimeTransport",
        "command", "message", "status"
    )
}
