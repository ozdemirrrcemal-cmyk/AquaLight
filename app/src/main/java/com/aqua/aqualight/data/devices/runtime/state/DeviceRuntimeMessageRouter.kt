package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareCommandParsers
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaSnapshot
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

sealed interface DeviceRuntimeEventRoute {
    data class Refresh(
        val target: DeviceRuntimeStateTarget,
        val sourceMessageId: String
    ) : DeviceRuntimeEventRoute

    data class Ota(
        val snapshot: DeviceFirmwareOtaSnapshot,
        val sourceMessageId: String
    ) : DeviceRuntimeEventRoute

    data class ProtocolFault(val reason: String) : DeviceRuntimeEventRoute
}

/** Routes only the exact active firmware event union to a typed reducer or refresh target. */
class DeviceRuntimeMessageRouter(
    private val reducer: DeviceRuntimeStateReducer
) {
    fun route(event: AqlWsIncomingMessage.Event): DeviceRuntimeEventRoute {
        if (!AqlWsContract.isActiveEvent(event.module, event.action)) {
            return DeviceRuntimeEventRoute.ProtocolFault(
                "Firmware event route is outside the active Android contract."
            )
        }

        return if (
            event.module == AqlWsContract.MODULE_FIRMWARE &&
            (
                event.action == AqlWsContract.Event.OTA_PROGRESS ||
                    event.action == AqlWsContract.Event.OTA_COMPLETED
                )
        ) {
            runCatching {
                DeviceRuntimeEventRoute.Ota(
                    snapshot = DeviceFirmwareCommandParsers.parseOtaEvent(event.data),
                    sourceMessageId = event.id
                )
            }.getOrElse {
                DeviceRuntimeEventRoute.ProtocolFault(
                    "Firmware OTA event does not match its exact typed contract."
                )
            }
        } else {
            routeStatusChanged(event)
        }
    }

    private fun routeStatusChanged(
        event: AqlWsIncomingMessage.Event
    ): DeviceRuntimeEventRoute = runCatching {
        require(event.action == AqlWsContract.Event.STATUS_CHANGED)
        val data = event.data
        data.requireExactKeys(STATUS_CHANGED_KEYS)

        val commandId = data.requiredText("commandId")
        val module = data.requiredText("module")
        val action = data.requiredText("action")
        data.requiredText("sessionId")
        require(data.requiredLong("publishedAtMs") >= 0L)
        data.requiredObject("result")

        require(module == event.module)
        require(AqlWsContract.isAuthenticatedCommand(module, action))
        val target = requireNotNull(reducer.refreshTarget(module, action)) {
            "Status event command has no canonical refresh target."
        }
        DeviceRuntimeEventRoute.Refresh(target, commandId)
    }.getOrElse {
        DeviceRuntimeEventRoute.ProtocolFault(
            "Firmware status-changed event does not match its exact wrapper contract."
        )
    }
}

private fun JSONObject.requireExactKeys(expected: Set<String>) {
    require(keys().asSequence().toSet() == expected)
}

private fun JSONObject.requiredText(name: String): String {
    val value = opt(name) as? String ?: error("$name must be a string.")
    require(value.isNotEmpty())
    require(value == value.trim())
    require(value.none(Char::isISOControl))
    return value
}

private fun JSONObject.requiredLong(name: String): Long {
    val number = opt(name) as? Number ?: error("$name must be an integer.")
    val value = number.toLong()
    require(number.toDouble().isFinite() && number.toDouble() == value.toDouble())
    return value
}

private fun JSONObject.requiredObject(name: String): JSONObject =
    opt(name) as? JSONObject ?: error("$name must be an object.")

private val STATUS_CHANGED_KEYS = setOf(
    "commandId", "module", "action", "sessionId", "publishedAtMs", "result"
)
