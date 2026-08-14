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

    fun networkStatus(): AqlWsOutgoingMessage.Command {
        return command(
            module = AqlWsContract.MODULE_NETWORK,
            action = AqlWsContract.ACTION_NETWORK_STATUS_GET
        )
    }

    fun timeStatus(): AqlWsOutgoingMessage.Command {
        return command(
            module = AqlWsContract.MODULE_TIME,
            action = AqlWsContract.ACTION_TIME_STATUS_GET
        )
    }

    fun firmwareStatus(): AqlWsOutgoingMessage.Command {
        return command(
            module = AqlWsContract.MODULE_FIRMWARE,
            action = AqlWsContract.ACTION_FIRMWARE_STATUS_GET
        )
    }

    fun lightStatus(): AqlWsOutgoingMessage.Command {
        return command(
            module = AqlWsContract.MODULE_LIGHT,
            action = AqlWsContract.ACTION_LIGHT_STATUS_GET
        )
    }

    fun coolingStatus(): AqlWsOutgoingMessage.Command {
        return command(
            module = AqlWsContract.MODULE_COOLING,
            action = AqlWsContract.ACTION_COOLING_STATUS_GET
        )
    }

    fun timerStatus(): AqlWsOutgoingMessage.Command {
        return command(
            module = AqlWsContract.MODULE_TIMER,
            action = AqlWsContract.ACTION_TIMER_STATUS_GET
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
