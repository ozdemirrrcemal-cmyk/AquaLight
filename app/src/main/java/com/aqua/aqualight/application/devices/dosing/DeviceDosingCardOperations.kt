package com.aqua.aqualight.application.devices.dosing

import com.aqua.aqualight.application.devices.DeviceRootOperations
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/** Read-only application boundary for the tank-detail Dosing device card. */
interface DeviceDosingCardOperations {
    fun observe(deviceUid: String): Flow<DeviceDosingCardSummary?>
}

/**
 * Coordinates card observation with the one shared owner-scoped device runtime.
 *
 * The UI only subscribes to the card read model. This coordinator may request the existing root
 * runtime session before collecting authoritative Dosing state, but it never owns transport,
 * sockets, firmware refresh semantics or mutable Dosing state. Connection failure is intentionally
 * non-terminal for observation so central runtime recovery can publish a later authoritative state.
 */
class DeviceDosingCardUseCase(
    private val rootOperations: DeviceRootOperations,
    private val channelOperations: DeviceDosingChannelOperations,
    private val connectionDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DeviceDosingCardOperations {

    override fun observe(deviceUid: String): Flow<DeviceDosingCardSummary?> {
        val normalized = deviceUid.trim()
        if (normalized.isBlank()) return flowOf(null)

        return channelOperations.observeAll(normalized)
            .map { snapshots ->
                DeviceDosingCardSummaryPolicy.build(
                    deviceUid = normalized,
                    snapshots = snapshots
                )
            }
            .onStart {
                ensureSharedRuntime(normalized)
            }
    }

    private suspend fun ensureSharedRuntime(deviceUid: String) {
        withContext(connectionDispatcher) {
            rootOperations.connect(deviceUid)
        }
    }
}
