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

    fun deviceIdentity(): Boolean {
        return wsClient.send(AqlWsCommandFactory.deviceIdentity())
    }

    fun deviceStatus(): Boolean {
        return wsClient.send(AqlWsCommandFactory.deviceStatus())
    }

    fun deviceCapabilities(): Boolean {
        return wsClient.send(AqlWsCommandFactory.deviceCapabilities())
    }

    fun networkStatus(): Boolean {
        return wsClient.send(AqlWsCommandFactory.networkStatus())
    }

    fun timeStatus(): Boolean {
        return wsClient.send(AqlWsCommandFactory.timeStatus())
    }

    fun firmwareStatus(): Boolean {
        return wsClient.send(AqlWsCommandFactory.firmwareStatus())
    }

    fun lightStatus(): Boolean {
        return wsClient.send(AqlWsCommandFactory.lightStatus())
    }

    fun coolingStatus(): Boolean {
        return wsClient.send(AqlWsCommandFactory.coolingStatus())
    }

    fun timerStatus(): Boolean {
        return wsClient.send(AqlWsCommandFactory.timerStatus())
    }

    fun dosingStatus(): Boolean {
        return wsClient.send(AqlWsCommandFactory.dosingStatus())
    }

    fun command(
        module: String,
        action: String,
        data: JSONObject = JSONObject()
    ): String? {
        val message = AqlWsCommandFactory.command(
            module = module,
            action = action,
            data = data
        )

        return if (wsClient.send(message)) {
            message.id
        } else {
            null
        }
    }
}
