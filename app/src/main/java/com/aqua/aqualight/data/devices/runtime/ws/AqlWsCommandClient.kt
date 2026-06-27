package com.aqua.aqualight.data.devices.runtime.ws

import org.json.JSONObject

class AqlWsCommandClient(
    private val wsClient: AqlWsClient
) {
    fun authenticate(token: String): String? {
        val normalizedToken = token.trim()
        if (normalizedToken.isBlank()) return null

        val message = AqlWsOutgoingMessage.Auth(token = normalizedToken)
        return if (wsClient.send(message)) {
            message.id
        } else {
            null
        }
    }

    fun ping(): Boolean {
        return wsClient.send(AqlWsOutgoingMessage.Ping())
    }

    fun securityStatus(): Boolean {
        return wsClient.send(AqlWsCommandFactory.securityStatus())
    }

    fun securityPair(
        deviceUid: String,
        rotateToken: Boolean = false,
        currentToken: String = ""
    ): Boolean {
        return wsClient.send(
            AqlWsCommandFactory.securityPair(
                deviceUid = deviceUid,
                rotateToken = rotateToken,
                currentToken = currentToken
            )
        )
    }

    fun deviceIdentity(): Boolean {
        return wsClient.send(AqlWsCommandFactory.deviceIdentity())
    }

    fun deviceStatus(): Boolean {
        return wsClient.send(AqlWsCommandFactory.deviceStatus())
    }

    fun deviceCapabilities(): Boolean {
        return wsClient.send(AqlWsCommandFactory.deviceCapabilities())
    }

    fun command(
        module: String,
        action: String,
        data: JSONObject = JSONObject()
    ): Boolean {
        return wsClient.send(
            AqlWsCommandFactory.command(
                module = module,
                action = action,
                data = data
            )
        )
    }
}
