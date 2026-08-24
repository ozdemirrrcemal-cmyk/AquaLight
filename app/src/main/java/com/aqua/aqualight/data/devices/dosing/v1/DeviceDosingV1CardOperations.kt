package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardSummaryPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardUnavailableReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** Narrow data-layer port used only to reuse the owner-scoped central runtime session. */
internal interface DeviceDosingCardRuntimePort {
    fun currentDeviceFamily(deviceUid: DeviceUid): DeviceFamily?
    fun currentConnectionState(deviceUid: DeviceUid): AqlWsConnectionState?
    fun connectRuntime(deviceUid: DeviceUid): Result<Unit>
}

internal class RepositoryDeviceDosingCardRuntimePort(
    private val devicesRepository: DevicesRepository
) : DeviceDosingCardRuntimePort {
    override fun currentDeviceFamily(deviceUid: DeviceUid): DeviceFamily? =
        devicesRepository.currentDevice(deviceUid)?.product?.family

    override fun currentConnectionState(deviceUid: DeviceUid): AqlWsConnectionState? =
        devicesRepository.currentRuntimeConnectionState(deviceUid)

    override fun connectRuntime(deviceUid: DeviceUid): Result<Unit> =
        devicesRepository.connectRuntime(deviceUid)
}

/**
 * Owner-scoped read adapter for the tank-detail Dosing card.
 *
 * Runtime preparation stays inside the production Dosing/data boundary. The adapter reuses the
 * central runtime session and the authoritative [DeviceDosingChannelOperations] projection; it
 * owns no transport, socket, protocol session or mutable Dosing state.
 */
internal class DeviceDosingV1CardOperations(
    private val runtimePort: DeviceDosingCardRuntimePort,
    private val channelOperations: DeviceDosingChannelOperations,
    private val connectionDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DeviceDosingCardOperations {

    override fun observe(deviceUid: String): Flow<DeviceDosingCardState> {
        val normalized = deviceUid.trim()
        if (normalized.isBlank()) {
            return flowOf(
                DeviceDosingCardState.Unavailable(
                    DeviceDosingCardUnavailableReason.INVALID_DEVICE_UID
                )
            )
        }

        val uid = DeviceUid(normalized)
        return flow {
            emit(DeviceDosingCardState.Preparing)
            ensureCentralRuntimeSession(uid)?.let { reason ->
                emit(DeviceDosingCardState.Unavailable(reason))
            }
            channelOperations.observeAll(normalized).collect { snapshots ->
                DeviceDosingCardSummaryPolicy.build(
                    deviceUid = normalized,
                    snapshots = snapshots
                )?.let { summary ->
                    emit(DeviceDosingCardState.Ready(summary))
                }
            }
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(
                DeviceDosingCardState.Unavailable(
                    DeviceDosingCardUnavailableReason.OBSERVATION_FAILED
                )
            )
        }.distinctUntilChanged()
    }

    private suspend fun ensureCentralRuntimeSession(
        deviceUid: DeviceUid
    ): DeviceDosingCardUnavailableReason? = withContext(connectionDispatcher) {
        when (runtimePort.currentDeviceFamily(deviceUid)) {
            null -> DeviceDosingCardUnavailableReason.DEVICE_NOT_REGISTERED
            DeviceFamily.DOSING -> prepareDosingRuntime(deviceUid)
            else -> DeviceDosingCardUnavailableReason.DEVICE_FAMILY_MISMATCH
        }
    }

    private suspend fun prepareDosingRuntime(
        deviceUid: DeviceUid
    ): DeviceDosingCardUnavailableReason? {
        val reusedAuthenticatedSession = runtimePort.currentConnectionState(deviceUid)
            .isAuthenticatedFor(deviceUid)
        if (runtimePort.connectRuntime(deviceUid).isFailure) {
            return DeviceDosingCardUnavailableReason.RUNTIME_CONNECTION_FAILED
        }
        val sessionStillAuthenticated = runtimePort.currentConnectionState(deviceUid)
            .isAuthenticatedFor(deviceUid)
        if (
            reusedAuthenticatedSession &&
            sessionStillAuthenticated &&
            !channelOperations.refreshAll(deviceUid.value)
        ) {
            return DeviceDosingCardUnavailableReason.AUTHORITATIVE_REFRESH_FAILED
        }
        return null
    }
}

private fun AqlWsConnectionState?.isAuthenticatedFor(deviceUid: DeviceUid): Boolean =
    this is AqlWsConnectionState.Authenticated && this.deviceUid == deviceUid
