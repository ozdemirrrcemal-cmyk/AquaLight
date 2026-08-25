package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardSummary
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transform
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
 *
 * Presentation is snapshot-stable: once the central owner has published a complete validated
 * snapshot, runtime preparation, reconnect and refresh failures never replace it with transient
 * preparing/unavailable content. The next complete central snapshot atomically replaces it.
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
        return combine(
            observeCentralPresentation(normalized),
            observeRuntimePreparation(uid)
        ) { presentation, preparation ->
            when (presentation) {
                is CentralCardPresentation.Available ->
                    DeviceDosingCardState.Ready(presentation.summary)
                CentralCardPresentation.Missing -> preparation
                CentralCardPresentation.Failed -> DeviceDosingCardState.Unavailable(
                    DeviceDosingCardUnavailableReason.OBSERVATION_FAILED
                )
            }
        }.distinctUntilChanged()
    }

    private fun observeCentralPresentation(
        deviceUid: String
    ): Flow<CentralCardPresentation> {
        var retainedSummary: DeviceDosingCardSummary? = null
        return channelOperations.observeAll(deviceUid)
            .transform { snapshots ->
                val summary = DeviceDosingCardSummaryPolicy.build(
                    deviceUid = deviceUid,
                    snapshots = snapshots
                )
                if (summary != null) {
                    retainedSummary = summary
                    emit(CentralCardPresentation.Available(summary))
                } else if (retainedSummary == null) {
                    emit(CentralCardPresentation.Missing)
                }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                val retained = retainedSummary
                emit(
                    if (retained != null) {
                        CentralCardPresentation.Available(retained)
                    } else {
                        CentralCardPresentation.Failed
                    }
                )
            }
    }

    private fun observeRuntimePreparation(
        deviceUid: DeviceUid
    ): Flow<DeviceDosingCardState> = flow {
        emit(DeviceDosingCardState.Preparing)
        ensureCentralRuntimeSession(deviceUid)?.let { reason ->
            emit(DeviceDosingCardState.Unavailable(reason))
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(
            DeviceDosingCardState.Unavailable(
                DeviceDosingCardUnavailableReason.RUNTIME_CONNECTION_FAILED
            )
        )
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
        return if (runtimePort.connectRuntime(deviceUid).isFailure) {
            DeviceDosingCardUnavailableReason.RUNTIME_CONNECTION_FAILED
        } else {
            val sessionStillAuthenticated = runtimePort.currentConnectionState(deviceUid)
                .isAuthenticatedFor(deviceUid)
            if (
                reusedAuthenticatedSession &&
                sessionStillAuthenticated &&
                !channelOperations.refreshAll(deviceUid.value)
            ) {
                DeviceDosingCardUnavailableReason.AUTHORITATIVE_REFRESH_FAILED
            } else {
                null
            }
        }
    }
}

private sealed interface CentralCardPresentation {
    data object Missing : CentralCardPresentation
    data class Available(val summary: DeviceDosingCardSummary) : CentralCardPresentation
    data object Failed : CentralCardPresentation
}

private fun AqlWsConnectionState?.isAuthenticatedFor(deviceUid: DeviceUid): Boolean =
    this is AqlWsConnectionState.Authenticated && this.deviceUid == deviceUid