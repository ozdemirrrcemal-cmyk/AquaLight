package com.aqua.aqualight.data.devices.runtime.ws

import org.json.JSONObject

@Suppress("TooManyFunctions")
class AqlWsCommandClient(
    private val wsClient: AqlWsTransport
) {
    fun securityStatus(): Boolean = wsClient.send(AqlWsCommandFactory.securityStatus())

    fun deviceIdentity(): Boolean = wsClient.send(AqlWsCommandFactory.deviceIdentity())

    fun deviceStatus(): Boolean = wsClient.send(AqlWsCommandFactory.deviceStatus())

    fun deviceCapabilities(): Boolean = wsClient.send(AqlWsCommandFactory.deviceCapabilities())

    fun networkStatus(): Boolean = wsClient.send(AqlWsCommandFactory.networkStatus())

    /**
     * Sends an authenticated, read-only network status request and returns its correlation id.
     * Callers that need proof of current device liveness must wait for the matching successful
     * response instead of treating a queued WebSocket write as proof that the device is online.
     */
    fun requestNetworkStatus(): String? {
        val message = AqlWsCommandFactory.networkStatus()
        return if (wsClient.send(message)) message.id else null
    }

    fun timeStatus(): Boolean = wsClient.send(AqlWsCommandFactory.timeStatus())

    fun firmwareStatus(): Boolean = wsClient.send(AqlWsCommandFactory.firmwareStatus())

    fun lightStatus(): Boolean = wsClient.send(AqlWsCommandFactory.lightStatus())

    fun coolingStatus(): Boolean = wsClient.send(AqlWsCommandFactory.coolingStatus())

    fun timerStatus(): Boolean = wsClient.send(AqlWsCommandFactory.timerStatus())

    fun dosingStatus(): Boolean = wsClient.send(AqlWsCommandFactory.dosingStatus())

    /**
     * Sends a caller-created command. This is the race-free entry point for request correlation:
     * the caller can register [message.id] before any response is able to arrive.
     */
    fun send(message: AqlWsOutgoingMessage.Command): Boolean = wsClient.send(message)

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
        return if (send(message)) message.id else null
    }
}
