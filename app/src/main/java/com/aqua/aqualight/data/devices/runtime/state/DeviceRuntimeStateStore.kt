package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DeviceRuntimeStateStore(
    private val wallClockMillis: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private val _states = MutableStateFlow<Map<DeviceUid, DeviceRuntimeState>>(emptyMap())

    val states: StateFlow<Map<DeviceUid, DeviceRuntimeState>> = _states.asStateFlow()

    fun observe(deviceUid: DeviceUid): Flow<DeviceRuntimeState> = states
        .map { values -> values[deviceUid] ?: DeviceRuntimeState(deviceUid) }
        .distinctUntilChanged()

    fun current(deviceUid: DeviceUid): DeviceRuntimeState =
        states.value[deviceUid] ?: DeviceRuntimeState(deviceUid)

    fun markAuthenticated(deviceUid: DeviceUid) {
        update(deviceUid, DeviceRuntimeState::markAuthenticated)
    }

    fun markDisconnected(deviceUid: DeviceUid) {
        update(deviceUid, DeviceRuntimeState::markDisconnected)
    }

    fun beginBootstrap(
        deviceUid: DeviceUid,
        metadataGeneration: Long,
        targets: Set<DeviceRuntimeRefreshTarget>
    ) {
        require(metadataGeneration > 0L) { "Validated metadata generation must be positive." }
        update(deviceUid) { state -> state.beginBootstrap(metadataGeneration, targets) }
    }

    fun applyMessage(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage
    ): Set<DeviceRuntimeRefreshTarget> {
        var targets = emptySet<DeviceRuntimeRefreshTarget>()
        update(deviceUid) { previous ->
            val result = DeviceRuntimeMessageRouter.reduce(
                previous = previous,
                message = message,
                nowMillis = wallClockMillis()
            )
            targets = result.refreshTargets
            result.state
        }
        return targets
    }

    fun applyCommandFault(
        deviceUid: DeviceUid,
        code: String,
        message: String,
        module: String,
        action: String,
        messageId: String = ""
    ) {
        val now = wallClockMillis()
        update(deviceUid) { previous ->
            previous.copy(
                lastFault = DeviceRuntimeFault(
                    code = code,
                    message = message,
                    module = module,
                    action = action,
                    messageId = messageId,
                    occurredAtMillis = now
                )
            )
        }
    }

    fun applyTransportFault(
        deviceUid: DeviceUid,
        code: String,
        message: String
    ) {
        val now = wallClockMillis()
        update(deviceUid) { previous ->
            previous.copy(
                authenticated = false,
                lastFault = DeviceRuntimeFault(
                    code = code,
                    message = message,
                    occurredAtMillis = now
                )
            ).markDisconnected()
        }
    }

    fun retire(deviceUid: DeviceUid) {
        synchronized(lock) {
            if (deviceUid !in _states.value) return
            _states.value = _states.value - deviceUid
        }
    }

    fun clear() {
        synchronized(lock) {
            _states.value = emptyMap()
        }
    }

    private fun update(
        deviceUid: DeviceUid,
        transform: (DeviceRuntimeState) -> DeviceRuntimeState
    ) {
        synchronized(lock) {
            val current = _states.value
            val previous = current[deviceUid] ?: DeviceRuntimeState(deviceUid)
            _states.value = current + (deviceUid to transform(previous))
        }
    }
}
