package com.aqua.aqualight.data.devices.runtime.ws

import org.json.JSONObject

@Suppress("TooManyFunctions")
class AqlWsCommandClient(
    private val wsClient: AqlWsTransport
) {
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

    /**
     * Sends an authenticated, read-only network status request and returns its correlation id.
     *
     * Callers that need proof of current device liveness must wait for the matching successful
     * response instead of treating a queued WebSocket write as proof that the device is online.
     */
    fun requestNetworkStatus(): String? {
        val message = AqlWsCommandFactory.networkStatus()
        return if (wsClient.send(message)) {
            message.id
        } else {
            null
        }
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
