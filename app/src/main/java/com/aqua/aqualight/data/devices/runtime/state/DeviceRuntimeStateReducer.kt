package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModuleStatus
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatus
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaClearTypedResult
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
                metadata = ready(metadata, null),
                device = ready(moduleStatus, null),
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
        store.reduce(deviceUid, generation, target::markLoading)
    }

    /** Returns the canonical status refresh required after a successful mutation, if any. */
    fun commandCompleted(outcome: DeviceRuntimeCommandOutcome<*>): DeviceRuntimeStateTarget<*>? =
        when (outcome) {
            is DeviceRuntimeCommandOutcome.Success<*> -> reduceSuccess(outcome)

            // These outcomes do not prove a new module value and must not overwrite READY/STALE data.
            is DeviceRuntimeCommandOutcome.NotConnected,
            is DeviceRuntimeCommandOutcome.NotAuthenticated,
            is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
            is DeviceRuntimeCommandOutcome.Cancelled -> null

            is DeviceRuntimeCommandOutcome.SendFailed -> {
                reduceFault(
                    deviceUid = outcome.deviceUid,
                    generation = outcome.generation,
                    module = outcome.module,
                    action = outcome.action,
                    messageId = outcome.messageId,
                    reason = "Runtime command could not be sent."
                )
                null
            }
            is DeviceRuntimeCommandOutcome.Timeout -> {
                reduceFault(
                    deviceUid = outcome.deviceUid,
                    generation = outcome.generation,
                    module = outcome.module,
                    action = outcome.action,
                    messageId = outcome.messageId,
                    reason = "Runtime command timed out after ${outcome.timeoutMillis} ms."
                )
                null
            }
            is DeviceRuntimeCommandOutcome.FirmwareError -> {
                reduceFault(
                    deviceUid = outcome.deviceUid,
                    generation = outcome.generation,
                    module = outcome.module,
                    action = outcome.action,
                    messageId = outcome.messageId,
                    reason = outcome.message.ifBlank { outcome.code }
                )
                null
            }
            is DeviceRuntimeCommandOutcome.ProtocolError -> {
                reduceFault(
                    deviceUid = outcome.deviceUid,
                    generation = outcome.generation,
                    module = outcome.module,
                    action = outcome.action,
                    messageId = outcome.messageId,
                    reason = outcome.reason,
                    protocolFault = true
                )
                null
            }
            is DeviceRuntimeCommandOutcome.LocalStateError -> {
                reduceFault(
                    deviceUid = outcome.deviceUid,
                    generation = outcome.generation,
                    module = outcome.module,
                    action = outcome.action,
                    messageId = outcome.messageId,
                    reason = outcome.reason
                )
                null
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

    fun refreshTarget(module: String, action: String): DeviceRuntimeStateTarget<*>? =
        when (module) {
            AqlWsContract.MODULE_DEVICE -> DeviceRuntimeStateTarget.Metadata
            AqlWsContract.MODULE_SECURITY -> when (action) {
                AqlWsContract.ACTION_SECURITY_UNPAIR,
                AqlWsContract.ACTION_SECURITY_RESET -> null
                else -> DeviceRuntimeStateTarget.Security
            }
            AqlWsContract.MODULE_NETWORK -> DeviceRuntimeStateTarget.Network
            AqlWsContract.MODULE_TIME -> DeviceRuntimeStateTarget.Time
            AqlWsContract.MODULE_LIGHT -> if (
                action == AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET ||
                action == AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_SET
            ) {
                DeviceRuntimeStateTarget.LightTemperatureProtection
            } else {
                DeviceRuntimeStateTarget.Light
            }
            AqlWsContract.MODULE_TIMER -> DeviceRuntimeStateTarget.Timer
            AqlWsContract.MODULE_DOSING -> DeviceRuntimeStateTarget.Dosing
            AqlWsContract.MODULE_COOLING -> DeviceRuntimeStateTarget.Cooling
            AqlWsContract.MODULE_FIRMWARE -> if (
                action == AqlWsContract.ACTION_FIRMWARE_STATUS_GET
            ) {
                DeviceRuntimeStateTarget.Firmware
            } else {
                DeviceRuntimeStateTarget.Ota
            }
            else -> null
        }

    private fun reduceSuccess(
        outcome: DeviceRuntimeCommandOutcome.Success<*>
    ): DeviceRuntimeStateTarget<*>? {
        val reduced = when (outcome.module) {
            AqlWsContract.MODULE_SECURITY -> reduceSecurity(outcome)
            AqlWsContract.MODULE_NETWORK -> reduceNetwork(outcome)
            AqlWsContract.MODULE_TIME -> reduceTime(outcome)
            AqlWsContract.MODULE_LIGHT -> reduceLight(outcome)
            AqlWsContract.MODULE_TIMER -> reduceTimer(outcome)
            AqlWsContract.MODULE_DOSING -> reduceDosing(outcome)
            AqlWsContract.MODULE_COOLING -> reduceCooling(outcome)
            AqlWsContract.MODULE_FIRMWARE -> reduceFirmware(outcome)
            else -> false
        }
        return if (reduced) null else refreshTarget(outcome.module, outcome.action)
    }

    private fun reduceSecurity(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        if (outcome.action != AqlWsContract.ACTION_SECURITY_STATUS_GET) return false
        val value = outcome.value as? DeviceSecurityStatusResponse ?: return false
        return store.reduce(outcome.deviceUid, outcome.generation) { state ->
            state.copy(security = ready(value, outcome.messageId))
        }
    }

    private fun reduceNetwork(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        if (outcome.action != AqlWsContract.ACTION_NETWORK_STATUS_GET) return false
        val value = outcome.value as? DeviceNetworkStatus ?: return false
        return store.reduce(outcome.deviceUid, outcome.generation) { state ->
            state.copy(network = ready(value, outcome.messageId))
        }
    }

    private fun reduceTime(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        if (outcome.action != AqlWsContract.ACTION_TIME_STATUS_GET) return false
        val value = outcome.value as? DeviceTimeStatus ?: return false
        return store.reduce(outcome.deviceUid, outcome.generation) { state ->
            state.copy(time = ready(value, outcome.messageId))
        }
    }

    private fun reduceLight(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean =
        when (outcome.action) {
            AqlWsContract.ACTION_LIGHT_STATUS_GET -> {
                val value = outcome.value as? DeviceLightStatus ?: return false
                store.reduce(outcome.deviceUid, outcome.generation) { state ->
                    state.copy(light = ready(value, outcome.messageId))
                }
            }
            AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET -> {
                val value = outcome.value as? DeviceLightTemperatureProtectionStatus
                    ?: return false
                store.reduce(outcome.deviceUid, outcome.generation) { state ->
                    state.copy(
                        lightTemperatureProtection = ready(value, outcome.messageId)
                    )
                }
            }
            else -> false
        }

    private fun reduceTimer(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        if (outcome.action != AqlWsContract.ACTION_TIMER_STATUS_GET) return false
        val value = outcome.value as? DeviceTimerStatus ?: return false
        return store.reduce(outcome.deviceUid, outcome.generation) { state ->
            state.copy(timer = ready(value, outcome.messageId))
        }
    }

    private fun reduceDosing(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        if (outcome.action != AqlWsContract.ACTION_DOSING_STATUS_GET) return false
        val value = outcome.value as? DeviceDosingStatus ?: return false
        return store.reduce(outcome.deviceUid, outcome.generation) { state ->
            state.copy(dosing = ready(value, outcome.messageId))
        }
    }

    private fun reduceCooling(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean {
        if (outcome.action != AqlWsContract.ACTION_COOLING_STATUS_GET) return false
        val value = outcome.value as? DeviceCoolingStatus ?: return false
        return store.reduce(outcome.deviceUid, outcome.generation) { state ->
            state.copy(cooling = ready(value, outcome.messageId))
        }
    }

    private fun reduceFirmware(outcome: DeviceRuntimeCommandOutcome.Success<*>): Boolean =
        when (outcome.action) {
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
                val value = outcome.value as? DeviceFirmwareOtaClearTypedResult ?: return false
                store.reduce(outcome.deviceUid, outcome.generation) { state ->
                    state.copy(ota = ready(value.ota, outcome.messageId))
                }
            }
            else -> false
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
        val fault = DeviceRuntimeModuleFault(
            module = module,
            action = action,
            messageId = messageId,
            reason = reason
        )
        store.reduce(deviceUid, generation) { state ->
            val updated = target.markError(state, fault)
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
