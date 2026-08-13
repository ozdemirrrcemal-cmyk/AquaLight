package com.aqua.aqualight.data.devices.runtime.modules.dosing.state

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.isNewerDosingSample
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingGlobalStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingMutationResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingStatusChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceDosingRuntimeState(
    val globalStatus: DeviceDosingGlobalStatus? = null,
    val channels: Map<String, DeviceDosingChannelStatus> = emptyMap(),
    val lastMutation: DeviceDosingMutationResult? = null,
    val lastStatusChange: DeviceDosingStatusChange? = null,
    val requiresStatusRefresh: Boolean = false
) {
    fun channel(channelKey: String): DeviceDosingChannelStatus? = channels[channelKey]
}

/** Device-isolated canonical Dosing state reduced from global/channel status and mutations. */
internal class DeviceDosingRuntimeStateStore {
    private val lock = Any()
    private val _states = MutableStateFlow<Map<DeviceUid, DeviceDosingRuntimeState>>(emptyMap())
    val states: StateFlow<Map<DeviceUid, DeviceDosingRuntimeState>> = _states.asStateFlow()

    fun recordGlobalStatus(deviceUid: DeviceUid, status: DeviceDosingGlobalStatus): Boolean =
        synchronized(lock) {
            val current = _states.value[deviceUid] ?: DeviceDosingRuntimeState()
            val currentUptime = current.globalStatus?.envelope?.uptimeMs
            if (
                currentUptime != null &&
                !isNewerDosingSample(status.envelope.uptimeMs, currentUptime)
            ) {
                return@synchronized false
            }
            val validKeys = status.channels.map { it.channelKey }.toSet()
            val retainedDetails = current.channels.filterKeys { it in validKeys }
            _states.value = _states.value + (
                deviceUid to current.copy(
                    globalStatus = status,
                    channels = retainedDetails,
                    requiresStatusRefresh = false
                )
            )
            true
        }

    fun recordChannelStatus(deviceUid: DeviceUid, status: DeviceDosingChannelStatus): Boolean =
        synchronized(lock) {
            val current = _states.value[deviceUid] ?: DeviceDosingRuntimeState()
            val key = status.channel.channelKey
            val previous = current.channels[key]
            if (
                previous != null &&
                !isNewerDosingSample(status.envelope.uptimeMs, previous.envelope.uptimeMs)
            ) {
                return@synchronized false
            }
            val globalRevision = current.globalStatus?.channels
                ?.singleOrNull { it.channelKey == key }
                ?.revision
            if (globalRevision != null) require(status.channel.revision >= globalRevision)
            _states.value = _states.value + (
                deviceUid to current.copy(
                    channels = current.channels + (key to status),
                    lastMutation = null,
                    requiresStatusRefresh = false
                )
            )
            true
        }

    fun recordMutation(deviceUid: DeviceUid, result: DeviceDosingMutationResult): Boolean =
        synchronized(lock) {
            val current = _states.value[deviceUid] ?: DeviceDosingRuntimeState()
            val key = result.channelKey
            val existing = current.channels[key]
            val updatedChannels = if (existing != null) {
                current.channels + (key to existing.copy(channel = result.channel))
            } else {
                current.channels
            }
            val updatedGlobal = current.globalStatus?.let { global ->
                val summary = global.channels.singleOrNull { it.channelKey == key }
                if (summary == null) {
                    global
                } else {
                    global.copy(
                        channels = global.channels.map { item ->
                            if (item.channelKey != key) item else item.copy(
                                effectiveName = result.channel.effectiveName,
                                revision = result.channel.revision,
                                runtimeEnabled = result.channel.runtimeEnabled,
                                runtimeReason = result.channel.runtimeReason,
                                programEnabled = result.channel.program?.enabled == true,
                                programMode = result.channel.program?.mode,
                                deliveryAccountingCertain = result.channel.deliveryAccountingCertain,
                                usageToday = result.channel.usageToday,
                                reservoir = item.reservoir.copy(
                                    trackingEnabled = result.channel.reservoir.trackingEnabled,
                                    remainingMl = result.channel.reservoir.remainingMl,
                                    accountingCertain = result.channel.reservoir.accountingCertain
                                ),
                                active = result.channel.activeRun.active
                            )
                        }
                    )
                }
            }
            _states.value = _states.value + (
                deviceUid to current.copy(
                    globalStatus = updatedGlobal,
                    channels = updatedChannels,
                    lastMutation = result,
                    requiresStatusRefresh = true
                )
            )
            true
        }

    fun recordStatusChange(deviceUid: DeviceUid, change: DeviceDosingStatusChange): Boolean =
        synchronized(lock) {
            val current = _states.value[deviceUid] ?: DeviceDosingRuntimeState()
            val previous = current.lastStatusChange
            if (
                previous != null &&
                previous.channelKey == change.channelKey &&
                change.change.sequence <= previous.change.sequence
            ) {
                return@synchronized false
            }
            _states.value = _states.value + (
                deviceUid to current.copy(
                    lastStatusChange = change,
                    requiresStatusRefresh = true
                )
            )
            true
        }

    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) {
            if (deviceUid !in _states.value) return
            _states.value = _states.value.toMutableMap().apply { remove(deviceUid) }.toMap()
        }
    }
}
