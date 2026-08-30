package com.aqua.aqualight.ui.tabs.devices.detail.cooling

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
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceCoolingRootViewModel(
    private val operations: DeviceRootOperations,
    private val controlSurfacePreparationOperations: DeviceControlSurfacePreparationOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingRootUiState())
    val uiState: StateFlow<DeviceCoolingRootUiState> = _uiState.asStateFlow()
    private val surfaceUnavailableEventChannel = Channel<DeviceMenuUnavailableReason>(
        capacity = Channel.BUFFERED
    )
    val surfaceUnavailableEvents: Flow<DeviceMenuUnavailableReason> =
        surfaceUnavailableEventChannel.receiveAsFlow()

    private var boundDeviceUid: String = ""
    private var latestRootSnapshot: DeviceRootSnapshot? = null
    private var surfacePreparationPending: Boolean = false
    private var observeJob: Job? = null
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
            family = OwnerDeviceFamily.COOLING
        )
        boundDeviceUid = deviceUid
        latestRootSnapshot = operations.current(deviceUid)
        observeJob?.cancel()
        surfacePreparationJob?.cancel()

        operations.connect(deviceUid)
        val authoritativeAtBind = currentAuthoritativeSurface(deviceUid)
        val preparedSurfaceStillCurrent = preparedHandoff && authoritativeAtBind != null
        latestRootSnapshot = authoritativeAtBind ?: latestRootSnapshot
        surfacePreparationPending = !preparedSurfaceStillCurrent
        renderBoundState()

        observeJob = viewModelScope.launch {
            operations.observe(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid) return@collect
                latestRootSnapshot = snapshot
                renderBoundState()
            }
        }
        if (!preparedSurfaceStillCurrent) {
            prepareRestoredSurface(deviceUid)
        }
    }

    private fun clearBinding() {
        observeJob?.cancel()
        surfacePreparationJob?.cancel()
        boundDeviceUid = ""
        latestRootSnapshot = null
        surfacePreparationPending = false
        _uiState.value = DeviceCoolingRootUiState()
    }

    private fun prepareRestoredSurface(deviceUid: String) {
        if (deviceUid.isBlank() || surfacePreparationJob?.isActive == true) return
        surfacePreparationPending = true
        renderBoundState()
        surfacePreparationJob = viewModelScope.launch {
            val result = runCatching {
                controlSurfacePreparationOperations.prepare(
                    DeviceControlSurfacePreparationRequest(
                        deviceUid = deviceUid,
                        family = OwnerDeviceFamily.COOLING
                    )
                )
            }.getOrElse {
                DeviceControlSurfacePreparationResult.Unavailable(
                    DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
                )
            }
            if (boundDeviceUid != deviceUid) return@launch

            when (result) {
                DeviceControlSurfacePreparationResult.Ready -> {
                    controlSurfacePreparationOperations.consumeFreshPreparation(
                        deviceUid = deviceUid,
                        family = OwnerDeviceFamily.COOLING
                    )
                    val preparedSnapshot = currentAuthoritativeSurface(deviceUid)
                    if (preparedSnapshot == null) {
                        finishUnavailablePreparation(
                            DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
                        )
                    } else {
                        latestRootSnapshot = preparedSnapshot
                        surfacePreparationPending = false
                        renderBoundState()
                    }
                }
                is DeviceControlSurfacePreparationResult.Unavailable -> {
                    finishUnavailablePreparation(result.reason)
                }
            }
        }
    }

    private suspend fun finishUnavailablePreparation(reason: DeviceMenuUnavailableReason) {
        surfacePreparationPending = false
        renderBoundState()
        surfaceUnavailableEventChannel.send(reason)
    }

    private fun currentAuthoritativeSurface(deviceUid: String): DeviceRootSnapshot? =
        operations.current(deviceUid)?.takeIf { snapshot ->
            snapshot.deviceUid == deviceUid && snapshot.isAuthoritativeCoolingSurface()
        }

    private fun renderBoundState() {
        val snapshot = latestRootSnapshot
        if (snapshot == null) {
            _uiState.value = DeviceCoolingRootUiState(
                deviceUid = boundDeviceUid,
                showBlockingPreparation = surfacePreparationPending
            )
            return
        }

        val contentEnabled =
            !surfacePreparationPending && snapshot.isAuthoritativeCoolingSurface()
        _uiState.value = DeviceCoolingRootUiState(
            title = snapshot.title,
            deviceUid = snapshot.deviceUid,
            connectionVisualState = if (contentEnabled) {
                DeviceConnectionVisualState.ONLINE
            } else {
                DeviceConnectionVisualState.OFFLINE
            },
            contentEnabled = contentEnabled,
            showBlockingPreparation = surfacePreparationPending
        )
    }
}

data class DeviceCoolingRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE,
    val contentEnabled: Boolean = false,
    val showBlockingPreparation: Boolean = false
)

internal fun DeviceRootSnapshot.isAuthoritativeCoolingSurface(): Boolean =
    availability == OwnerDeviceAvailability.REACHABLE &&
        catalogState == DeviceRootCatalogState.VALID &&
        family == OwnerDeviceFamily.COOLING &&
        fanOutputCount in MIN_COOLING_FAN_COUNT..MAX_COOLING_FAN_COUNT &&
        temperatureSensorCount == COOLING_TEMPERATURE_SENSOR_COUNT

private const val MIN_COOLING_FAN_COUNT = 1
private const val MAX_COOLING_FAN_COUNT = 3
private const val COOLING_TEMPERATURE_SENSOR_COUNT = 1
