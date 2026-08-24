package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardSummary
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardSummaryPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Owner-scoped read adapter for the tank-detail Dosing card.
 *
 * Runtime preparation stays inside the production Dosing/data boundary. The adapter reuses the
 * central [DevicesRepository] session and the authoritative [DeviceDosingChannelOperations]
 * projection; it owns no transport, socket, protocol session or mutable Dosing state.
 */
internal class DeviceDosingV1CardOperations(
    private val devicesRepository: DevicesRepository,
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
                ensureCentralRuntimeSession(DeviceUid(normalized))
            }
    }

    private suspend fun ensureCentralRuntimeSession(deviceUid: DeviceUid) {
        withContext(connectionDispatcher) {
            val device = devicesRepository.currentDevice(deviceUid)
            if (device?.product?.family != DeviceFamily.DOSING) return@withContext

            val reusedAuthenticatedSession =
                devicesRepository.currentRuntimeConnectionState(deviceUid)
                    .isAuthenticatedFor(deviceUid)
            val connectionReady = devicesRepository.connectRuntime(deviceUid).isSuccess
            val sessionStillAuthenticated =
                devicesRepository.currentRuntimeConnectionState(deviceUid)
                    .isAuthenticatedFor(deviceUid)

            if (connectionReady && reusedAuthenticatedSession && sessionStillAuthenticated) {
                // The runtime may have authenticated before this owner-scoped Dosing adapter began
                // collecting lifecycle events. Reusing that session emits no new Authenticated edge,
                // so hydrate the card through the same central device-wide refresh used by runtime
                // bootstrap. Fresh connections remain owned by the production lifecycle collector.
                channelOperations.refreshAll(deviceUid.value)
            }
        }
    }
}

private fun AqlWsConnectionState?.isAuthenticatedFor(deviceUid: DeviceUid): Boolean =
    this is AqlWsConnectionState.Authenticated && this.deviceUid == deviceUid
