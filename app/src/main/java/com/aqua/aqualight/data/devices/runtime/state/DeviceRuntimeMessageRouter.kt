package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionParser
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerStatusParser
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

enum class DeviceRuntimeRefreshTarget {
    DEVICE,
    SECURITY,
    NETWORK,
    TIME,
    FIRMWARE,
    OTA,
    LIGHT,
    LIGHT_TEMPERATURE_PROTECTION,
    COOLING,
    TIMER,
    DOSING
}

data class DeviceRuntimeRouteResult(
    val state: DeviceRuntimeState,
    val refreshTargets: Set<DeviceRuntimeRefreshTarget> = emptySet()
)

object DeviceRuntimeMessageRouter {

    private const val MAX_WIRE_RECORDS = 64

    fun reduce(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage,
        nowMillis: Long
    ): DeviceRuntimeRouteResult {
        val withRecord = previous.record(message, nowMillis)
        return when (message) {
            is AqlWsIncomingMessage.Response -> reduceResponse(withRecord, message, nowMillis)
            is AqlWsIncomingMessage.Event -> reduceEvent(withRecord, message, nowMillis)
            is AqlWsIncomingMessage.Error -> reduceError(withRecord, message, nowMillis)
        }
    }

    private fun reduceResponse(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult {
        if (!message.ok) {
            return DeviceRuntimeRouteResult(previous.fail(message.toFault(nowMillis)))
        }

        val source = DeviceRuntimeValueSource.RESPONSE
        return when (message.module to message.action) {
            AqlWsContract.MODULE_DEVICE to AqlWsContract.ACTION_DEVICE_STATUS_GET ->
                routeParsed(
                    previous = previous,
                    parsed = DeviceRuntimeCoreStatusParser.parseDeviceStatus(message.data),
                    message = message,
                    nowMillis = nowMillis,
                    apply = { state, value -> state.copy(device = value) },
                    current = previous.device,
                    source = source,
                    failureTarget = DeviceRuntimeRefreshTarget.DEVICE
                )

            AqlWsContract.MODULE_SECURITY to AqlWsContract.ACTION_SECURITY_STATUS_GET ->
                routeParsed(
                    previous,
                    DeviceRuntimeCoreStatusParser.parseSecurityStatus(message.data),
                    message,
                    nowMillis,
                    { state, value -> state.copy(security = value) },
                    previous.security,
                    source,
                    DeviceRuntimeRefreshTarget.SECURITY
                )

            AqlWsContract.MODULE_NETWORK to AqlWsContract.ACTION_NETWORK_STATUS_GET ->
                routeParsed(
                    previous,
                    DeviceRuntimeCoreStatusParser.parseNetworkStatus(message.data),
                    message,
                    nowMillis,
                    { state, value -> state.copy(network = value) },
                    previous.network,
                    source,
                    DeviceRuntimeRefreshTarget.NETWORK
                )

            AqlWsContract.MODULE_TIME to AqlWsContract.ACTION_TIME_STATUS_GET ->
                routeParsed(
                    previous,
                    runCatching { DeviceTimeStatusParser.parse(message.data) },
                    message,
                    nowMillis,
                    { state, value -> state.copy(time = value) },
                    previous.time,
                    source,
                    DeviceRuntimeRefreshTarget.TIME
                )

            AqlWsContract.MODULE_FIRMWARE to AqlWsContract.ACTION_FIRMWARE_STATUS_GET ->
                routeParsed(
                    previous,
                    runCatching { DeviceFirmwareStatusParser.parseFirmwareStatus(message.data) },
                    message,
                    nowMillis,
                    { state, value -> state.copy(firmware = value) },
                    previous.firmware,
                    source,
                    DeviceRuntimeRefreshTarget.FIRMWARE
                )

            AqlWsContract.MODULE_FIRMWARE to AqlWsContract.ACTION_FIRMWARE_OTA_STATUS ->
                routeParsed(
                    previous,
                    DeviceFirmwareStatusParser.parseOtaStatusResponseExact(message.data),
                    message,
                    nowMillis,
                    { state, value -> state.copy(ota = value) },
                    previous.ota,
                    source,
                    DeviceRuntimeRefreshTarget.OTA
                )

            AqlWsContract.MODULE_FIRMWARE to AqlWsContract.ACTION_FIRMWARE_OTA_START -> {
                val parsed = DeviceFirmwareStatusParser.parseOtaStartAcceptedExact(message.data)
                routeParsed(
                    previous,
                    parsed.map { it.ota },
                    message,
                    nowMillis,
                    { state, value -> state.copy(ota = value) },
                    previous.ota,
                    source,
                    DeviceRuntimeRefreshTarget.OTA
                )
            }

            AqlWsContract.MODULE_FIRMWARE to AqlWsContract.ACTION_FIRMWARE_OTA_CLEAR -> {
                val parsed = DeviceFirmwareStatusParser.parseOtaClearResultExact(message.data)
                routeParsed(
                    previous,
                    parsed.map { it.ota },
                    message,
                    nowMillis,
                    { state, value -> state.copy(ota = value) },
                    previous.ota,
                    source,
                    DeviceRuntimeRefreshTarget.OTA
                )
            }

            AqlWsContract.MODULE_LIGHT to AqlWsContract.ACTION_LIGHT_STATUS_GET ->
                routeParsed(
                    previous,
                    runCatching { DeviceLightStatusParser.parse(message.data) },
                    message,
                    nowMillis,
                    { state, value -> state.copy(light = value) },
                    previous.light,
                    source,
                    DeviceRuntimeRefreshTarget.LIGHT
                )

            AqlWsContract.MODULE_LIGHT to
                AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET ->
                routeParsed(
                    previous,
                    DeviceLightTemperatureProtectionParser.parseStatus(message.data),
                    message,
                    nowMillis,
                    { state, value -> state.copy(lightTemperatureProtection = value) },
                    previous.lightTemperatureProtection,
                    source,
                    DeviceRuntimeRefreshTarget.LIGHT_TEMPERATURE_PROTECTION
                )

            AqlWsContract.MODULE_LIGHT to AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_SET -> {
                val parsed = DeviceLightTemperatureProtectionParser.parseSetResult(message.data)
                routeParsed(
                    previous,
                    parsed.map { it.status },
                    message,
                    nowMillis,
                    { state, value -> state.copy(lightTemperatureProtection = value) },
                    previous.lightTemperatureProtection,
                    source,
                    DeviceRuntimeRefreshTarget.LIGHT_TEMPERATURE_PROTECTION
                )
            }

            AqlWsContract.MODULE_COOLING to AqlWsContract.ACTION_COOLING_STATUS_GET ->
                routeParsed(
                    previous,
                    runCatching { DeviceCoolingStatusParser.parse(message.data) },
                    message,
                    nowMillis,
                    { state, value -> state.copy(cooling = value) },
                    previous.cooling,
                    source,
                    DeviceRuntimeRefreshTarget.COOLING
                )

            AqlWsContract.MODULE_TIMER to AqlWsContract.ACTION_TIMER_STATUS_GET ->
                routeParsed(
                    previous,
                    runCatching { DeviceTimerStatusParser.parse(message.data) },
                    message,
                    nowMillis,
                    { state, value -> state.copy(timer = value) },
                    previous.timer,
                    source,
                    DeviceRuntimeRefreshTarget.TIMER
                )

            AqlWsContract.MODULE_DOSING to AqlWsContract.ACTION_DOSING_STATUS_GET ->
                routeParsed(
                    previous,
                    runCatching { DeviceDosingStatusParser.parse(message.data) },
                    message,
                    nowMillis,
                    { state, value -> state.copy(dosing = value) },
                    previous.dosing,
                    source,
                    DeviceRuntimeRefreshTarget.DOSING
                )

            AqlWsContract.MODULE_DEVICE to AqlWsContract.ACTION_DEVICE_NAME_SET ->
                reduceDeviceNameSet(previous, message, nowMillis)

            else -> DeviceRuntimeRouteResult(
                state = previous.copy(lastFault = null),
                refreshTargets = mutationRefreshTargets(message.module, message.action)
            )
        }
    }

    private fun reduceDeviceNameSet(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult {
        val statusJson = message.data.optJSONObject("status")
            ?: return DeviceRuntimeRouteResult(
                previous,
                setOf(DeviceRuntimeRefreshTarget.DEVICE)
            )
        val parsed = DeviceRuntimeCoreStatusParser.parseNameStatus(statusJson)
        val current = previous.device.value
        if (current == null) {
            return DeviceRuntimeRouteResult(
                previous,
                setOf(DeviceRuntimeRefreshTarget.DEVICE)
            )
        }
        return parsed.fold(
            onSuccess = { name ->
                DeviceRuntimeRouteResult(
                    previous.copy(
                        device = previous.device.ready(
                            current.copy(device = name),
                            DeviceRuntimeValueSource.RESPONSE,
                            message.id,
                            nowMillis
                        ),
                        lastFault = null
                    )
                )
            },
            onFailure = { error ->
                val fault = parseFault(message, error, nowMillis)
                DeviceRuntimeRouteResult(
                    previous.copy(
                        device = previous.device.failed(fault),
                        lastFault = fault
                    ),
                    setOf(DeviceRuntimeRefreshTarget.DEVICE)
                )
            }
        )
    }

    private fun reduceEvent(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Event,
        nowMillis: Long
    ): DeviceRuntimeRouteResult {
        if (
            message.action == AqlWsContract.Event.FIRMWARE_OTA_PROGRESS ||
            message.action == AqlWsContract.Event.FIRMWARE_OTA_COMPLETED
        ) {
            return routeParsed(
                previous,
                DeviceFirmwareStatusParser.parseOtaProgressEventExact(message.data),
                message,
                nowMillis,
                { state, value -> state.copy(ota = value) },
                previous.ota,
                DeviceRuntimeValueSource.EVENT,
                DeviceRuntimeRefreshTarget.OTA
            )
        }

        val commandResult = message.data.optJSONObject("result")
        if (
            message.module == AqlWsContract.MODULE_LIGHT &&
            message.action == AqlWsContract.Event.LIGHT_STATUS_CHANGED &&
            commandResult?.has("status") == true
        ) {
            val parsed = DeviceLightTemperatureProtectionParser.parseStatus(
                commandResult.getJSONObject("status")
            )
            if (parsed.isSuccess) {
                return routeParsed(
                    previous,
                    parsed,
                    message,
                    nowMillis,
                    { state, value -> state.copy(lightTemperatureProtection = value) },
                    previous.lightTemperatureProtection,
                    DeviceRuntimeValueSource.EVENT,
                    DeviceRuntimeRefreshTarget.LIGHT_TEMPERATURE_PROTECTION
                )
            }
        }

        return DeviceRuntimeRouteResult(
            state = previous.copy(lastFault = null),
            refreshTargets = eventRefreshTargets(message.action)
        )
    }

    private fun reduceError(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Error,
        nowMillis: Long
    ): DeviceRuntimeRouteResult {
        val fault = message.toFault(nowMillis)
        return DeviceRuntimeRouteResult(
            state = previous.fail(fault),
            refreshTargets = emptySet()
        )
    }

    private fun mutationRefreshTargets(module: String, action: String): Set<DeviceRuntimeRefreshTarget> {
        if (action == AqlWsContract.ACTION_STATUS_GET) return emptySet()
        return when (module) {
            AqlWsContract.MODULE_DEVICE -> setOf(DeviceRuntimeRefreshTarget.DEVICE)
            AqlWsContract.MODULE_SECURITY -> setOf(DeviceRuntimeRefreshTarget.SECURITY)
            AqlWsContract.MODULE_NETWORK -> setOf(DeviceRuntimeRefreshTarget.NETWORK)
            AqlWsContract.MODULE_TIME -> setOf(DeviceRuntimeRefreshTarget.TIME)
            AqlWsContract.MODULE_FIRMWARE -> setOf(
                DeviceRuntimeRefreshTarget.FIRMWARE,
                DeviceRuntimeRefreshTarget.OTA
            )
            AqlWsContract.MODULE_LIGHT -> setOf(DeviceRuntimeRefreshTarget.LIGHT)
            AqlWsContract.MODULE_COOLING -> setOf(DeviceRuntimeRefreshTarget.COOLING)
            AqlWsContract.MODULE_TIMER -> setOf(DeviceRuntimeRefreshTarget.TIMER)
            AqlWsContract.MODULE_DOSING -> setOf(DeviceRuntimeRefreshTarget.DOSING)
            else -> emptySet()
        }
    }

    private fun eventRefreshTargets(action: String): Set<DeviceRuntimeRefreshTarget> = when (action) {
        AqlWsContract.Event.DEVICE_STATUS_CHANGED -> setOf(DeviceRuntimeRefreshTarget.DEVICE)
        AqlWsContract.Event.NETWORK_STATE_CHANGED -> setOf(DeviceRuntimeRefreshTarget.NETWORK)
        AqlWsContract.Event.LIGHT_STATUS_CHANGED -> setOf(
            DeviceRuntimeRefreshTarget.LIGHT,
            DeviceRuntimeRefreshTarget.LIGHT_TEMPERATURE_PROTECTION
        )
        AqlWsContract.Event.COOLING_STATUS_CHANGED -> setOf(DeviceRuntimeRefreshTarget.COOLING)
        AqlWsContract.Event.TIMER_STATUS_CHANGED -> setOf(DeviceRuntimeRefreshTarget.TIMER)
        AqlWsContract.Event.DOSING_STATUS_CHANGED -> setOf(DeviceRuntimeRefreshTarget.DOSING)
        AqlWsContract.Event.TIME_STATUS_CHANGED -> setOf(DeviceRuntimeRefreshTarget.TIME)
        AqlWsContract.Event.FIRMWARE_OTA_PROGRESS,
        AqlWsContract.Event.FIRMWARE_OTA_COMPLETED -> setOf(DeviceRuntimeRefreshTarget.OTA)
        else -> emptySet()
    }

    private fun <T> routeParsed(
        previous: DeviceRuntimeState,
        parsed: Result<T>,
        message: AqlWsIncomingMessage,
        nowMillis: Long,
        apply: (DeviceRuntimeState, DeviceRuntimeValue<T>) -> DeviceRuntimeState,
        current: DeviceRuntimeValue<T>,
        source: DeviceRuntimeValueSource,
        failureTarget: DeviceRuntimeRefreshTarget
    ): DeviceRuntimeRouteResult = parsed.fold(
        onSuccess = { value ->
            DeviceRuntimeRouteResult(
                apply(
                    previous.copy(lastFault = null),
                    current.ready(value, source, message.id, nowMillis)
                )
            )
        },
        onFailure = { error ->
            val fault = parseFault(message, error, nowMillis)
            DeviceRuntimeRouteResult(
                state = apply(previous.copy(lastFault = fault), current.failed(fault)),
                refreshTargets = setOf(failureTarget)
            )
        }
    )

    private fun parseFault(
        message: AqlWsIncomingMessage,
        error: Throwable,
        nowMillis: Long
    ): DeviceRuntimeFault = DeviceRuntimeFault(
        code = "invalid_runtime_payload",
        message = error.message.orEmpty().ifBlank { "Firmware payload validation failed." },
        module = message.module,
        action = message.action,
        messageId = message.id,
        occurredAtMillis = nowMillis
    )

    private fun AqlWsIncomingMessage.Error.toFault(nowMillis: Long): DeviceRuntimeFault =
        DeviceRuntimeFault(
            code = code.ifBlank { "firmware_error_$statusCode" },
            message = message,
            field = field,
            module = module,
            action = action,
            messageId = id,
            occurredAtMillis = nowMillis
        )

    private fun AqlWsIncomingMessage.Response.toFault(nowMillis: Long): DeviceRuntimeFault =
        DeviceRuntimeFault(
            code = "firmware_response_not_ok",
            message = "Firmware returned a non-success response.",
            module = module,
            action = action,
            messageId = id,
            occurredAtMillis = nowMillis
        )

    private fun DeviceRuntimeState.fail(fault: DeviceRuntimeFault): DeviceRuntimeState =
        copy(lastFault = fault)

    private fun DeviceRuntimeState.record(
        message: AqlWsIncomingMessage,
        nowMillis: Long
    ): DeviceRuntimeState {
        val key = "${message.type}:${message.module}:${message.action}"
        val next = LinkedHashMap(lastPayloads)
        next[key] = DeviceRuntimeWireRecord(
            type = message.type,
            module = message.module,
            action = message.action,
            messageId = message.id,
            dataJson = JSONObject(message.data.toString()).toString(),
            receivedAtMillis = nowMillis
        )
        while (next.size > MAX_WIRE_RECORDS) {
            val oldest = next.keys.firstOrNull() ?: break
            next.remove(oldest)
        }
        return copy(lastPayloads = next, lastMessageAtMillis = nowMillis)
    }
}
