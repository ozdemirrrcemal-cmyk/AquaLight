package com.aqua.aqualight.ui.tabs.devices.detail.light

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.DeviceLightControlSnapshot
import com.aqua.aqualight.application.devices.light.matchesLightControlSurface
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceLightRootViewModel(
    private val rootOperations: DeviceRootOperations,
    private val lightControlOperations: DeviceLightControlOperations,
    private val controlSurfacePreparationOperations: DeviceControlSurfacePreparationOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightRootUiState())
    val uiState: StateFlow<DeviceLightRootUiState> = _uiState.asStateFlow()
    private val surfaceUnavailableEventChannel = Channel<DeviceMenuUnavailableReason>(
        capacity = Channel.BUFFERED
    )
    val surfaceUnavailableEvents: Flow<DeviceMenuUnavailableReason> =
        surfaceUnavailableEventChannel.receiveAsFlow()

    private var boundDeviceUid = ""
    private var latestRootSnapshot: DeviceRootSnapshot? = null
    private var currentControlSnapshot: DeviceLightControlSnapshot? = null
    private var surfacePreparationPending = false
    private var rootObserveJob: Job? = null
    private var controlObserveJob: Job? = null
    private var surfacePreparationJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid) {
            renderBoundState()
            return
        }

        val preparedHandoff = controlSurfacePreparationOperations.consumeFreshPreparation(
            deviceUid = deviceUid,
            family = OwnerDeviceFamily.LIGHT
        )
        cancelJobs()
        boundDeviceUid = deviceUid
        latestRootSnapshot = rootOperations.current(deviceUid)
        acceptControlResult(lightControlOperations.currentControl(deviceUid))
        val preparedSurfaceStillCurrent = preparedHandoff &&
            currentControlSnapshot.matchesLightControlSurface(
                deviceUid,
                latestRootSnapshot
            )
        surfacePreparationPending = !preparedSurfaceStillCurrent
        renderBoundState()

        rootOperations.connect(deviceUid)
        rootObserveJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            rootOperations.observe(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid) return@collect
                latestRootSnapshot = snapshot
                renderBoundState()
            }
        }
        controlObserveJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            lightControlOperations.observeControl(deviceUid).collect { result ->
                if (boundDeviceUid != deviceUid) return@collect
                acceptControlResult(result)
                renderBoundState()
            }
        }
        if (surfacePreparationPending) prepareRestoredSurface(deviceUid)
    }

    private fun prepareRestoredSurface(deviceUid: String) {
        if (surfacePreparationJob?.isActive == true) return
        surfacePreparationPending = true
        renderBoundState()
        surfacePreparationJob = viewModelScope.launch {
            val result = runCatching {
                controlSurfacePreparationOperations.prepare(
                    DeviceControlSurfacePreparationRequest(
                        deviceUid = deviceUid,
                        family = OwnerDeviceFamily.LIGHT
                    )
                )
            }.getOrElse {
                DeviceControlSurfacePreparationResult.Unavailable(
                    DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
                )
            }
            if (boundDeviceUid != deviceUid) return@launch

            when (result) {
                DeviceControlSurfacePreparationResult.Ready -> finishReadyPreparation(deviceUid)
                is DeviceControlSurfacePreparationResult.Unavailable ->
                    finishUnavailablePreparation(result.reason)
            }
        }
    }

    private suspend fun finishReadyPreparation(deviceUid: String) {
        controlSurfacePreparationOperations.consumeFreshPreparation(
            deviceUid = deviceUid,
            family = OwnerDeviceFamily.LIGHT
        )
        latestRootSnapshot = rootOperations.current(deviceUid)
        val current = lightControlOperations.currentControl(deviceUid)
        acceptControlResult(current)
        when (current) {
            is DeviceLightControlResult.Failed -> finishUnavailablePreparation(
                DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
            )
            is DeviceLightControlResult.Available -> {
                if (current.snapshot.matchesLightControlSurface(deviceUid, latestRootSnapshot)) {
                    surfacePreparationPending = false
                    renderBoundState()
                } else {
                    finishUnavailablePreparation(
                        DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH
                    )
                }
            }
        }
    }

    private suspend fun finishUnavailablePreparation(reason: DeviceMenuUnavailableReason) {
        surfacePreparationPending = false
        currentControlSnapshot = null
        renderBoundState()
        surfaceUnavailableEventChannel.send(reason)
    }

    private fun acceptControlResult(result: DeviceLightControlResult) {
        currentControlSnapshot = when (result) {
            is DeviceLightControlResult.Available -> result.snapshot
            is DeviceLightControlResult.Failed -> null
        }
    }

    private fun renderBoundState() {
        val root = latestRootSnapshot
        val controlAvailable = currentControlSnapshot.matchesLightControlSurface(
            boundDeviceUid,
            root
        )
        val rootAvailable = root.isLightControlRootAvailable(boundDeviceUid)
        val surfaceAvailable = rootAvailable && controlAvailable
        _uiState.value = DeviceLightRootUiState(
            title = root?.title.orEmpty(),
            deviceUid = boundDeviceUid,
            connectionVisualState = if (surfaceAvailable) {
                DeviceConnectionVisualState.ONLINE
            } else {
                DeviceConnectionVisualState.OFFLINE
            },
            contentEnabled = surfaceAvailable && !surfacePreparationPending,
            showBlockingPreparation = surfacePreparationPending
        )
    }

    private fun clearBinding() {
        cancelJobs()
        boundDeviceUid = ""
        latestRootSnapshot = null
        currentControlSnapshot = null
        surfacePreparationPending = false
        _uiState.value = DeviceLightRootUiState()
    }

    private fun cancelJobs() {
        rootObserveJob?.cancel()
        controlObserveJob?.cancel()
        surfacePreparationJob?.cancel()
        rootObserveJob = null
        controlObserveJob = null
        surfacePreparationJob = null
    }
}

data class DeviceLightRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE,
    val contentEnabled: Boolean = false,
    val showBlockingPreparation: Boolean = false
)

private fun DeviceRootSnapshot?.isLightControlRootAvailable(deviceUid: String): Boolean = when {
    this == null -> false
    this.deviceUid != deviceUid -> false
    availability != OwnerDeviceAvailability.REACHABLE -> false
    catalogState != DeviceRootCatalogState.VALID -> false
    else -> family == OwnerDeviceFamily.LIGHT
}
