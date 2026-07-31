package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModuleStatus
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatus
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaClearResult
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaStartAccepted
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionStatus
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkStatus
import com.aqua.aqualight.data.devices.runtime.modules.security.DeviceSecurityStatusResponse
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeStatus
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerStatus

/** Applies typed command/event values to exactly one device and connection generation. */
@Suppress("TooManyFunctions", "CyclomaticComplexMethod", "LongMethod")
class DeviceRuntimeStateReducer(
    private val store: DeviceRuntimeStateStore,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {

    fun publishMetadata(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        metadata: DeviceRuntimeMetadata,
        moduleStatus: DeviceRuntimeModuleStatus
    ): Boolean {
        val support = DeviceRuntimeSupport.from(metadata)
        return store.reduce(deviceUid, generation) { state ->
            state.copy(
                support = support,
                metadata = ready(metadata, sourceMessageId = null),
                device = ready(moduleStatus, sourceMessageId = null),
                security = state.security.availableWhen(support.security),
                network = state.network.availableWhen(support.network),
                time = state.time.availableWhen(support.time),
                light = state.light.availableWhen(support.light),
                lightTemperatureProtection = state.lightTemperatureProtection
                    .availableWhen(support.lightTemperatureProtection),
                timer = state.timer.availableWhen(support.timer),
                dosing = state.dosing.availableWhen(support.dosing),
                cooling = state.cooling.availableWhen(support.cooling),
                firmware = state.firmware.availableWhen(support.firmware),
                ota = state.ota.availableWhen(support.ota),
                protocolFault = null
            )
        }
    }

    fun commandStarted(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        module: String,
        action: String
    ) {
        val target = refreshTarget(module, action) ?: return
        store.reduce(deviceUid, generation) { state ->
            state.updateTarget(target) { current ->
                current.copy(
                    phase = DeviceRuntimeFreshness.LOADING,
                    fault = null
                )
            }
        }
    }

    /** Returns the canonical status refresh required after a successful mutation, if any. */
    fun commandCompleted(outcome: DeviceRuntimeCommandOutcome<*>): DeviceRuntimeStateTarget? {
        return when (outcome) {
            is DeviceRuntimeCommandOutcome.Success<*> -> reduceSuccess(outcome)
            is DeviceRuntimeCommandOutcome.NotConnected -> null
            is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> {
                val generation = store.current(outcome.deviceUid)?.generation ?: return null
                refreshTarget(outcome.module, outcome.action)?.also { target ->
                    store.reduce(outcome.deviceUid, generation) { state ->
                        state.updateTarget(target) { DeviceRuntimeValue.unavailable() }
                    }
                }
                null
            }
            is DeviceRuntimeCommandOutcome.NotAuthenticated -> {
                reduceFault(
                    outcome.deviceUid,
                    outcome.generation,
                    outcome.module,
                    outcome.action,
                    "",
                    "Runtime command requires an authenticated generation."
                )
                null
            }
            is DeviceRuntimeCommandOutcome.SendFailed -> {
                reduceFault(
                    outcome.deviceUid,
                    outcome.generation,
                    outcome.module,
                    outcome.action,
                    outcome.messageId,
                    "Runtime command could not be sent."
                )
                null
            }
            is DeviceRuntimeCommandOutcome.Timeout -> {
                reduceFault(
                    outcome.deviceUid,
                    outcome.generation,
                    outcome.module,
                    outcome.action,
                    outcome.messageId,
                    "Runtime command timed out after ${outcome.timeoutMillis} ms."
                )
                null
            }
            is DeviceRuntimeCommandOutcome.FirmwareError -> {
                reduceFault(
                    outcome.deviceUid,
                    outcome.generation,
                    outcome.module,
                    outcome.action,
                    outcome.messageId,
                    outcome.message.ifBlank { outcome.code }
                )
                null
            }
            is DeviceRuntimeCommandOutcome.ProtocolError -> {
                reduceFault(
                    outcome.deviceUid,
                    outcome.generation,
                    outcome.module,
                    outcome.action,
                    outcome.messageId,
                    outcome.reason,
                    protocolFault = true
                )
                null
            }
            is DeviceRuntimeCommandOutcome.LocalStateError -> {
                reduceFault(
                    outcome.deviceUid,
                    outcome.generation,
                    outcome.module,
                    outcome.action,
                    outcome.messageId,
                    outcome.reason
                )
                null
            }
            is DeviceRuntimeCommandOutcome.Cancelled -> {
                reduceFault(
                    outcome.deviceUid,
                    outcome.generation,
                    outcome.module,
                    outcome.action,
                    outcome.messageId,
                    outcome.reason
                )
                null
            }
        }
    }

    fun reduceOtaEvent(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        sourceMessageId: String,
        snapshot: DeviceFirmwareOtaSnapshot
    ): Boolean = store.reduce(deviceUid, generation) { state ->
        state.copy(ota = ready(snapshot, sourceMessageId))
    }

    fun recordProtocolFault(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        module: String,
        action: String,
        reason: String
    ): Boolean = store.reduce(deviceUid, generation) { state ->
        state.copy(
            protocolFault = DeviceRuntimeProtocolFault(
                module = module,
                action = action,
                reason = reason,
                receivedAtMillis = clockMillis(),
                receivedAtElapsedMillis = elapsedRealtimeMillis()
            )
        )
    }

    fun refreshTarget(module: String, action: String): DeviceRuntimeStateTarget? = when (module) {
        AqlWsContract.MODULE_DEVICE -> DeviceRuntimeStateTarget.METADATA
        AqlWsContract.MODULE_SECURITY -> when (action) {
            AqlWsContract.ACTION_SECURITY_UNPAIR,
            AqlWsContract.ACTION_SECURITY_RESET -> null
            else -> DeviceRuntimeStateTarget.SECURITY
        }
        AqlWsContract.MODULE_NETWORK -> DeviceRuntimeStateTarget.NETWORK
        AqlWsContract.MODULE_TIME -> DeviceRuntimeStateTarget.TIME
        AqlWsContract.MODULE_LIGHT -> if (
            action == AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET ||
            action == AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_SET
        ) {
            DeviceRuntimeStateTarget.LIGHT_TEMPERATURE_PROTECTION
        } else {
            DeviceRuntimeStateTarget.LIGHT
        }
        AqlWsContract.MODULE_TIMER -> DeviceRuntimeStateTarget.TIMER
        AqlWsContract.MODULE_DOSING -> DeviceRuntimeStateTarget.DOSING
        AqlWsContract.MODULE_COOLING -> DeviceRuntimeStateTarget.COOLING
        AqlWsContract.MODULE_FIRMWARE -> if (
            action == AqlWsContract.ACTION_FIRMWARE_STATUS_GET
        ) {
            DeviceRuntimeStateTarget.FIRMWARE
        } else {
            DeviceRuntimeStateTarget.OTA
        }
        else -> null
    }

    private fun reduceSuccess(
        outcome: DeviceRuntimeCommandOutcome.Success<*>
    ): DeviceRuntimeStateTarget? {
        val reduced = when (outcome.module) {
            AqlWsContract.MODULE_SECURITY -> reduceSecuritySuccess(outcome)
            AqlWsContract.MODULE_NETWORK -> reduceNetworkSuccess(outcome)
            AqlWsContract.MODULE_TIME -> reduceTimeSuccess(outcome)
            AqlWsContract.MODULE_LIGHT -> reduceLightSuccess(outcome)
            AqlWsContract.MODULE_TIMER -> reduceTimerSuccess(outcome)
            AqlWsContract.MODULE_DOSING -> reduceDosingSuccess(outcome)
            AqlWsContract.MODULE_COOLING -> reduceCoolingSuccess(outcome)
            AqlWsContract.MODULE_FIRMWARE -> reduceFirmwareSuccess(outcome)
            else -> false
        }
        if (reduced) return null
        return refreshTarget(outcome.module, outcome.action)
    }

    private fun reduceSecuritySuccess(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        if (outcome.action != AqlWsContract.ACTION_SECURITY_STATUS_GET) return false
        val value = outcome.value as? DeviceSecurityStatusResponse ?: return false
        return store.reduce(outcome.deviceUid, outcome.generation) { state ->
            state.copy(security = ready(value, outcome.messageId))
        }
    }

    private fun reduceNetworkSuccess(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        if (outcome.action != AqlWsContract.ACTION_NETWORK_STATUS_GET) return false
        val value = outcome.value as? DeviceNetworkStatus ?: return false
        return store.reduce(outcome.deviceUid, outcome.generation) { state ->
            state.copy(network = ready(value, outcome.messageId))
        }
    }

    private fun reduceTimeSuccess(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        if (outcome.action != AqlWsContract.ACTION_TIME_STATUS_GET) return false
        val value = outcome.value as? DeviceTimeStatus ?: return false
        return store.reduce(outcome.deviceUid, outcome.generation) { state ->
            state.copy(time = ready(value, outcome.messageId))
        }
    }

    private fun reduceLightSuccess(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        return when (outcome.action) {
            AqlWsContract.ACTION_LIGHT_STATUS_GET -> {
                val value = outcome.value as? DeviceLightStatus ?: return false
                store.reduce(outcome.deviceUid, outcome.generation) { state ->
                    state.copy(light = ready(value, outcome.messageId))
                }
            }
            AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET -> {
                val value = outcome.value as? DeviceLightTemperatureProtectionStatus ?: return false
                store.reduce(outcome.deviceUid, outcome.generation) { state ->
                    state.copy(
                        lightTemperatureProtection = ready(value, outcome.messageId)
                    )
                }
            }
            else -> false
        }
    }

    private fun reduceTimerSuccess(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        if (outcome.action != AqlWsContract.ACTION_TIMER_STATUS_GET) return false
        val value = outcome.value as? DeviceTimerStatus ?: return false
        return store.reduce(outcome.deviceUid, outcome.generation) { state ->
            state.copy(timer = ready(value, outcome.messageId))
        }
    }

    private fun reduceDosingSuccess(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        if (outcome.action != AqlWsContract.ACTION_DOSING_STATUS_GET) return false
        val value = outcome.value as? DeviceDosingStatus ?: return false
        return store.reduce(outcome.deviceUid, outcome.generation) { state ->
            state.copy(dosing = ready(value, outcome.messageId))
        }
    }

    private fun reduceCoolingSuccess(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        if (outcome.action != AqlWsContract.ACTION_COOLING_STATUS_GET) return false
        val value = outcome.value as? DeviceCoolingStatus ?: return false
        return store.reduce(outcome.deviceUid, outcome.generation) { state ->
            state.copy(cooling = ready(value, outcome.messageId))
        }
    }

    private fun reduceFirmwareSuccess(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        return when (outcome.action) {
            AqlWsContract.ACTION_FIRMWARE_STATUS_GET -> {
                val value = outcome.value as? DeviceFirmwareStatus ?: return false
                store.reduce(outcome.deviceUid, outcome.generation) { state ->
                    state.copy(
                        firmware = ready(value, outcome.messageId),
                        ota = ready(value.ota, outcome.messageId)
                    )
                }
            }
            AqlWsContract.ACTION_FIRMWARE_OTA_STATUS -> {
                val value = outcome.value as? DeviceFirmwareOtaSnapshot ?: return false
                store.reduce(outcome.deviceUid, outcome.generation) { state ->
                    state.copy(ota = ready(value, outcome.messageId))
                }
            }
            AqlWsContract.ACTION_FIRMWARE_OTA_START -> {
                val value = outcome.value as? DeviceFirmwareOtaStartAccepted ?: return false
                store.reduce(outcome.deviceUid, outcome.generation) { state ->
                    state.copy(ota = ready(value.ota, outcome.messageId))
                }
            }
            AqlWsContract.ACTION_FIRMWARE_OTA_CLEAR -> {
                val value = outcome.value as? DeviceFirmwareOtaClearResult ?: return false
                store.reduce(outcome.deviceUid, outcome.generation) { state ->
                    state.copy(ota = ready(value.ota, outcome.messageId))
                }
            }
            else -> false
        }
    }

    private fun reduceFault(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        module: String,
        action: String,
        messageId: String,
        reason: String,
        protocolFault: Boolean = false
    ) {
        val target = refreshTarget(module, action) ?: return
        store.reduce(deviceUid, generation) { state ->
            state.updateTarget(target) { current ->
                current.copy(
                    phase = DeviceRuntimeFreshness.ERROR,
                    fault = DeviceRuntimeModuleFault(
                        module = module,
                        action = action,
                        messageId = messageId,
                        reason = reason
                    )
                )
            }.let { updated ->
                if (!protocolFault) {
                    updated
                } else {
                    updated.copy(
                        protocolFault = DeviceRuntimeProtocolFault(
                            module = module,
                            action = action,
                            reason = reason,
                            receivedAtMillis = clockMillis(),
                            receivedAtElapsedMillis = elapsedRealtimeMillis()
                        )
                    )
                }
            }
        }
    }

    private fun <T> ready(value: T, sourceMessageId: String?): DeviceRuntimeValue<T> =
        DeviceRuntimeValue(
            phase = DeviceRuntimeFreshness.READY,
            value = value,
            receivedAtMillis = clockMillis(),
            receivedAtElapsedMillis = elapsedRealtimeMillis(),
            sourceMessageId = sourceMessageId,
            fault = null
        )
}

private fun <T> DeviceRuntimeValue<T>.availableWhen(supported: Boolean): DeviceRuntimeValue<T> =
    if (supported) this else DeviceRuntimeValue.unavailable()

@Suppress("UNCHECKED_CAST")
private fun DeviceRuntimeState.updateTarget(
    target: DeviceRuntimeStateTarget,
    transform: (DeviceRuntimeValue<Any?>) -> DeviceRuntimeValue<Any?>
): DeviceRuntimeState = when (target) {
    DeviceRuntimeStateTarget.METADATA -> copy(
        metadata = transform(metadata as DeviceRuntimeValue<Any?>) as
            DeviceRuntimeValue<DeviceRuntimeMetadata>
    )
    DeviceRuntimeStateTarget.SECURITY -> copy(
        security = transform(security as DeviceRuntimeValue<Any?>) as
            DeviceRuntimeValue<DeviceSecurityStatusResponse>
    )
    DeviceRuntimeStateTarget.NETWORK -> copy(
        network = transform(network as DeviceRuntimeValue<Any?>) as
            DeviceRuntimeValue<DeviceNetworkStatus>
    )
    DeviceRuntimeStateTarget.TIME -> copy(
        time = transform(time as DeviceRuntimeValue<Any?>) as DeviceRuntimeValue<DeviceTimeStatus>
    )
    DeviceRuntimeStateTarget.LIGHT -> copy(
        light = transform(light as DeviceRuntimeValue<Any?>) as
            DeviceRuntimeValue<DeviceLightStatus>
    )
    DeviceRuntimeStateTarget.LIGHT_TEMPERATURE_PROTECTION -> copy(
        lightTemperatureProtection = transform(
            lightTemperatureProtection as DeviceRuntimeValue<Any?>
        ) as DeviceRuntimeValue<DeviceLightTemperatureProtectionStatus>
    )
    DeviceRuntimeStateTarget.TIMER -> copy(
        timer = transform(timer as DeviceRuntimeValue<Any?>) as
            DeviceRuntimeValue<DeviceTimerStatus>
    )
    DeviceRuntimeStateTarget.DOSING -> copy(
        dosing = transform(dosing as DeviceRuntimeValue<Any?>) as
            DeviceRuntimeValue<DeviceDosingStatus>
    )
    DeviceRuntimeStateTarget.COOLING -> copy(
        cooling = transform(cooling as DeviceRuntimeValue<Any?>) as
            DeviceRuntimeValue<DeviceCoolingStatus>
    )
    DeviceRuntimeStateTarget.FIRMWARE -> copy(
        firmware = transform(firmware as DeviceRuntimeValue<Any?>) as
            DeviceRuntimeValue<DeviceFirmwareStatus>
    )
    DeviceRuntimeStateTarget.OTA -> copy(
        ota = transform(ota as DeviceRuntimeValue<Any?>) as
            DeviceRuntimeValue<DeviceFirmwareOtaSnapshot>
    )
}
