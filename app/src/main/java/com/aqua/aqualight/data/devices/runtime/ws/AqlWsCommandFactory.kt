package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import org.json.JSONObject

object AqlWsCommandFactory {

    fun securityStatus(): AqlWsOutgoingMessage.Command {
        return command(
            module = AqlWsContract.MODULE_SECURITY,
            action = AqlWsContract.ACTION_SECURITY_STATUS_GET
        )
    }

    fun securityPair(
        deviceUid: String,
        rotateToken: Boolean = false,
        currentToken: String = ""
    ): AqlWsOutgoingMessage.Command {
        val data = JSONObject()
            .put("deviceUid", deviceUid)
            .put("rotateToken", rotateToken)

        if (currentToken.isNotBlank()) {
            data.put("currentToken", currentToken)
        }

        return command(
            module = AqlWsContract.MODULE_SECURITY,
            action = AqlWsContract.ACTION_SECURITY_PAIR,
            data = data
        )
    }

    fun deviceIdentity(): AqlWsOutgoingMessage.Command {
        return command(
            module = AqlWsContract.MODULE_DEVICE,
            action = AqlWsContract.ACTION_DEVICE_IDENTITY_GET
        )
    }

    fun deviceStatus(): AqlWsOutgoingMessage.Command {
        return command(
            module = AqlWsContract.MODULE_DEVICE,
            action = AqlWsContract.ACTION_DEVICE_STATUS_GET
        )
    }

    fun deviceCapabilities(): AqlWsOutgoingMessage.Command {
        return command(
            module = AqlWsContract.MODULE_DEVICE,
            action = AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET
        )
    }

    fun command(
        module: String,
        action: String,
        data: JSONObject = JSONObject()
    ): AqlWsOutgoingMessage.Command {
        return AqlWsOutgoingMessage.Command(
            module = module,
            action = action,
            data = data
        )
    }
}
