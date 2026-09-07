package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardState
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardSummary
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardUnavailableReason
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeState
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transform

/**
 * Owner-scoped read adapter over the single central Cooling runtime owner.
 *
 * It owns no mutable Cooling state and never evaluates fan control or program schedules. Missing
 * program details are hydrated into the central owner, then projected from that same snapshot.
 */
internal class DefaultDeviceCoolingCardOperations(
    private val devicesRepository: DevicesRepository
) : DeviceCoolingCardOperations {

    override fun observe(deviceUid: String): Flow<DeviceCoolingCardState> {
        val uid = deviceUid.trim().takeIf(String::isNotEmpty)?.let(::DeviceUid)
            ?: return flowOf(
                DeviceCoolingCardState.Unavailable(
                    DeviceCoolingCardUnavailableReason.INVALID_DEVICE_UID
                )
            )
        val runtime = devicesRepository.runtimeModules()?.cooling
            ?: return flowOf(
                DeviceCoolingCardState.Unavailable(
                    DeviceCoolingCardUnavailableReason.RUNTIME_CONNECTION_FAILED
                )
            )

        return combine(
            observeCentralPresentation(uid, runtime),
            prepareRuntime(uid)
        ) { presentation, preparation ->
            when (presentation) {
                is CoolingCardPresentation.Available ->
                    DeviceCoolingCardState.Ready(presentation.summary)
                CoolingCardPresentation.Missing -> preparation
                CoolingCardPresentation.Failed -> DeviceCoolingCardState.Unavailable(
                    DeviceCoolingCardUnavailableReason.OBSERVATION_FAILED
                )
            }
        }.distinctUntilChanged()
    }

    private fun observeCentralPresentation(
        deviceUid: DeviceUid,
        runtime: DeviceCoolingRuntimeRepository
    ): Flow<CoolingCardPresentation> {
        var requestedProgramRevision: Long? = null
        return combine(
            devicesRepository.observeDevice(deviceUid),
            runtime.states
        ) { device, states ->
            val state = states[deviceUid]
            when {
                device == null || device.product.family != DeviceFamily.COOLING ->
                    CentralCoolingSnapshot.Missing
                state == null || !state.authoritative -> CentralCoolingSnapshot.Missing
                else -> CentralCoolingSnapshot.Available(state)
            }
        }.transform { snapshot ->
            when (snapshot) {
                CentralCoolingSnapshot.Missing -> {
                    requestedProgramRevision = null
                    emit(CoolingCardPresentation.Missing)
                }
                is CentralCoolingSnapshot.Available -> {
                    val state = snapshot.state
                    val summary = DeviceCoolingCardSnapshotMapper.map(state)
                    emit(
                        summary?.let(CoolingCardPresentation::Available)
                            ?: CoolingCardPresentation.Missing
                    )
                    val revision = state.status?.programRevision
                    if (
                        revision != null &&
                        requestedProgramRevision != revision &&
                        state.requiresProgramHydration()
                    ) {
                        requestedProgramRevision = revision
                        runtime.requestProgram(deviceUid)
                    }
                }
            }
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(CoolingCardPresentation.Failed)
        }
    }

    private fun prepareRuntime(deviceUid: DeviceUid): Flow<DeviceCoolingCardState> = flow {
        emit(DeviceCoolingCardState.Preparing)
        val reason = when (devicesRepository.currentDevice(deviceUid)?.product?.family) {
            null -> DeviceCoolingCardUnavailableReason.DEVICE_NOT_REGISTERED
            DeviceFamily.COOLING -> if (devicesRepository.connectRuntime(deviceUid).isFailure) {
                DeviceCoolingCardUnavailableReason.RUNTIME_CONNECTION_FAILED
            } else {
                null
            }
            else -> DeviceCoolingCardUnavailableReason.DEVICE_FAMILY_MISMATCH
        }
        reason?.let { emit(DeviceCoolingCardState.Unavailable(it)) }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(
            DeviceCoolingCardState.Unavailable(
                DeviceCoolingCardUnavailableReason.RUNTIME_CONNECTION_FAILED
            )
        )
    }
}

private fun DeviceCoolingRuntimeState.requiresProgramHydration(): Boolean {
    val status = status ?: return false
    val mode = telemetry?.controlMode ?: status.config.controlMode
    return mode == com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ControlMode.PROGRAM &&
        programSnapshot?.programRevision != status.programRevision
}

private sealed interface CentralCoolingSnapshot {
    data object Missing : CentralCoolingSnapshot
    data class Available(val state: DeviceCoolingRuntimeState) : CentralCoolingSnapshot
}

private sealed interface CoolingCardPresentation {
    data object Missing : CoolingCardPresentation
    data object Failed : CoolingCardPresentation
    data class Available(val summary: DeviceCoolingCardSummary) : CoolingCardPresentation
}
