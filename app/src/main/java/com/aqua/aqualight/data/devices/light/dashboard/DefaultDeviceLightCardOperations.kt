package com.aqua.aqualight.data.devices.light.dashboard

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightCardOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightCardState
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightCardUnavailableReason
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlSnapshot
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal interface DeviceLightCardRuntimePort {
    fun currentDeviceFamily(deviceUid: DeviceUid): DeviceFamily?
    fun connectRuntime(deviceUid: DeviceUid): Result<Unit>
}

internal class RepositoryDeviceLightCardRuntimePort(
    private val devicesRepository: DevicesRepository
) : DeviceLightCardRuntimePort {
    override fun currentDeviceFamily(deviceUid: DeviceUid): DeviceFamily? =
        devicesRepository.currentDevice(deviceUid)?.product?.family

    override fun connectRuntime(deviceUid: DeviceUid): Result<Unit> =
        devicesRepository.connectRuntime(deviceUid)
}

/**
 * Stateless adapter over the owner-scoped central Light dashboard boundary.
 *
 * It owns no Light snapshot, scheduler, output calculation or transport session. Preparation only
 * connects the shared runtime and refreshes the same complete status+graph projection used by the
 * Light root screen.
 */
internal class DefaultDeviceLightCardOperations(
    private val runtimePort: DeviceLightCardRuntimePort,
    private val controlOperations: DeviceLightControlOperations,
    private val connectionDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DeviceLightCardOperations {

    constructor(
        devicesRepository: DevicesRepository,
        controlOperations: DeviceLightControlOperations
    ) : this(
        runtimePort = RepositoryDeviceLightCardRuntimePort(devicesRepository),
        controlOperations = controlOperations
    )

    override fun observe(deviceUid: String): Flow<DeviceLightCardState> {
        val normalized = deviceUid.trim()
        return if (normalized.isBlank()) {
            flowOf(
                DeviceLightCardState.Unavailable(
                    DeviceLightCardUnavailableReason.INVALID_DEVICE_UID
                )
            )
        } else {
            val uid = DeviceUid(normalized)
            combine(
                observeCentralPresentation(normalized),
                prepareCentralRuntime(uid)
            ) { presentation, preparation ->
                when (presentation) {
                    is CentralLightCardPresentation.Available ->
                        DeviceLightCardState.Ready(presentation.snapshot)
                    CentralLightCardPresentation.Missing -> preparation
                    CentralLightCardPresentation.Failed -> DeviceLightCardState.Unavailable(
                        DeviceLightCardUnavailableReason.OBSERVATION_FAILED
                    )
                }
            }.distinctUntilChanged()
        }
    }

    private fun observeCentralPresentation(
        deviceUid: String
    ): Flow<CentralLightCardPresentation> = controlOperations.observeControl(deviceUid)
        .map { result ->
            when (result) {
                is DeviceLightControlResult.Available ->
                    CentralLightCardPresentation.Available(result.snapshot)
                is DeviceLightControlResult.Failed -> CentralLightCardPresentation.Missing
            }
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(CentralLightCardPresentation.Failed)
        }

    private fun prepareCentralRuntime(
        deviceUid: DeviceUid
    ): Flow<DeviceLightCardState> = flow {
        emit(DeviceLightCardState.Preparing)
        val failure = withContext(connectionDispatcher) {
            prepareCentralRuntimeSession(deviceUid)
        }
        failure?.let { reason -> emit(DeviceLightCardState.Unavailable(reason)) }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(
            DeviceLightCardState.Unavailable(
                DeviceLightCardUnavailableReason.RUNTIME_CONNECTION_FAILED
            )
        )
    }

    private suspend fun prepareCentralRuntimeSession(
        deviceUid: DeviceUid
    ): DeviceLightCardUnavailableReason? = when (runtimePort.currentDeviceFamily(deviceUid)) {
        null -> DeviceLightCardUnavailableReason.DEVICE_NOT_REGISTERED
        DeviceFamily.LIGHT -> prepareLightRuntime(deviceUid)
        else -> DeviceLightCardUnavailableReason.DEVICE_FAMILY_MISMATCH
    }

    private suspend fun prepareLightRuntime(
        deviceUid: DeviceUid
    ): DeviceLightCardUnavailableReason? =
        if (runtimePort.connectRuntime(deviceUid).isFailure) {
            DeviceLightCardUnavailableReason.RUNTIME_CONNECTION_FAILED
        } else {
            when (controlOperations.refreshControl(deviceUid.value)) {
                is DeviceLightControlResult.Available -> null
                is DeviceLightControlResult.Failed ->
                    DeviceLightCardUnavailableReason.AUTHORITATIVE_REFRESH_FAILED
            }
        }
}

private sealed interface CentralLightCardPresentation {
    data object Missing : CentralLightCardPresentation
    data object Failed : CentralLightCardPresentation
    data class Available(val snapshot: DeviceLightControlSnapshot) : CentralLightCardPresentation
}
