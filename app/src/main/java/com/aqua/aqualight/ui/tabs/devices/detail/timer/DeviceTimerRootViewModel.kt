package com.aqua.aqualight.ui.tabs.devices.detail.timer

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
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerNextTransitionType
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.application.devices.timer.DeviceTimerOutputHealth
import com.aqua.aqualight.application.devices.timer.DeviceTimerRuntimeReason
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleSnapshot
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

class DeviceTimerRootViewModel(
    private val operations: DeviceRootOperations,
    private val timerControlOperations: DeviceTimerControlOperations,
    private val controlSurfacePreparationOperations: DeviceControlSurfacePreparationOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceTimerRootUiState())
    val uiState: StateFlow<DeviceTimerRootUiState> = _uiState.asStateFlow()
    private val surfaceUnavailableEventChannel = Channel<DeviceMenuUnavailableReason>(
        capacity = Channel.BUFFERED
    )
    val surfaceUnavailableEvents: Flow<DeviceMenuUnavailableReason> =
        surfaceUnavailableEventChannel.receiveAsFlow()

    private var boundDeviceUid = ""
    private var latestRootSnapshot: DeviceRootSnapshot? = null
    private var lastControlPresentation: DeviceTimerControlUiState? = null
    private var controlAvailable = false
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
            family = OwnerDeviceFamily.TIMER
        )

        cancelJobs()
        boundDeviceUid = deviceUid
        latestRootSnapshot = operations.current(deviceUid)
        val initialControl = timerControlOperations.currentControl(deviceUid)
        acceptControlResult(initialControl)
        val preparedSurfaceStillCurrent =
            preparedHandoff && initialControl is DeviceTimerControlResult.Available
        surfacePreparationPending = !preparedSurfaceStillCurrent
        renderBoundState()

        operations.connect(deviceUid)
        rootObserveJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            operations.observe(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid) return@collect
                latestRootSnapshot = snapshot
                renderBoundState()
            }
        }
        controlObserveJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            timerControlOperations.observeControl(deviceUid).collect { result ->
                if (boundDeviceUid != deviceUid) return@collect
                acceptControlResult(result)
                renderBoundState()
            }
        }
        if (surfacePreparationPending) {
            prepareRestoredSurface(deviceUid)
        }
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
                        family = OwnerDeviceFamily.TIMER
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
            family = OwnerDeviceFamily.TIMER
        )
        latestRootSnapshot = operations.current(deviceUid)
        when (val current = timerControlOperations.currentControl(deviceUid)) {
            is DeviceTimerControlResult.Available -> {
                acceptControlResult(current)
                surfacePreparationPending = false
                renderBoundState()
            }
            is DeviceTimerControlResult.Failed -> finishUnavailablePreparation(
                DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
            )
        }
    }

    private suspend fun finishUnavailablePreparation(reason: DeviceMenuUnavailableReason) {
        surfacePreparationPending = false
        controlAvailable = false
        renderBoundState()
        surfaceUnavailableEventChannel.send(reason)
    }

    private fun acceptControlResult(result: DeviceTimerControlResult) {
        controlAvailable = result is DeviceTimerControlResult.Available
        if (result is DeviceTimerControlResult.Available) {
            lastControlPresentation = result.snapshot.toUiState()
        }
    }

    private fun renderBoundState() {
        val root = latestRootSnapshot
        val rootAvailable = root.isTimerControlRootAvailable(boundDeviceUid)
        _uiState.value = DeviceTimerRootUiState(
            title = root?.title.orEmpty(),
            deviceUid = boundDeviceUid,
            connectionVisualState = if (rootAvailable && controlAvailable) {
                DeviceConnectionVisualState.ONLINE
            } else {
                DeviceConnectionVisualState.OFFLINE
            },
            contentEnabled = rootAvailable && controlAvailable && !surfacePreparationPending,
            showBlockingPreparation = surfacePreparationPending,
            control = lastControlPresentation
        )
    }

    private fun clearBinding() {
        cancelJobs()
        boundDeviceUid = ""
        latestRootSnapshot = null
        lastControlPresentation = null
        controlAvailable = false
        surfacePreparationPending = false
        _uiState.value = DeviceTimerRootUiState()
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

data class DeviceTimerRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE,
    val contentEnabled: Boolean = false,
    val showBlockingPreparation: Boolean = false,
    val control: DeviceTimerControlUiState? = null
)

@Suppress("LongParameterList")
data class DeviceTimerControlUiState(
    val revision: Long,
    val lockLoop: Boolean,
    val uptimeMillis: Long,
    val maxSchedulesPerChannel: Int,
    val readOnly: Boolean,
    val channelStateWriteEnabled: Boolean,
    val scheduleWriteEnabled: Boolean,
    val spansMidnightSupported: Boolean,
    val temporaryOverrideWriteEnabled: Boolean,
    val displayNameWriteEnabled: Boolean,
    val channels: List<DeviceTimerChannelUiState>
)

@Suppress("LongParameterList")
data class DeviceTimerChannelUiState(
    val slotId: String,
    val channelNumber: Int,
    val defaultName: String,
    val displayName: String,
    val regime: DeviceTimerChannelRegime,
    val operatingState: DeviceTimerOperatingState,
    val scheduleCount: Int,
    val activeScheduleSlotId: Int?,
    val activeScheduleName: String?,
    val nextTransitionType: DeviceTimerNextTransitionType,
    val nextTransitionAtEpochMillis: Long?,
    val runtimeReason: DeviceTimerRuntimeReason,
    val clockReady: Boolean,
    val temporaryOverrideActive: Boolean,
    val temporaryOverrideRemainingMillis: Long,
    val outputHealth: DeviceTimerOutputHealth,
    val physicalFeedbackAvailable: Boolean,
    val displayNameEditable: Boolean,
    val schedules: List<DeviceTimerScheduleUiState>?
)

@Suppress("LongParameterList")
data class DeviceTimerScheduleUiState(
    val index: Int,
    val slotId: Int,
    val enabled: Boolean,
    val name: String,
    val weekdays: List<Boolean>,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val spansMidnight: Boolean
)

@Suppress("ComplexCondition")
private fun DeviceRootSnapshot?.isTimerControlRootAvailable(deviceUid: String): Boolean =
    this != null &&
        this.deviceUid == deviceUid &&
        availability == OwnerDeviceAvailability.REACHABLE &&
        catalogState == DeviceRootCatalogState.VALID &&
        family == OwnerDeviceFamily.TIMER

private fun DeviceTimerControlSnapshot.toUiState() = DeviceTimerControlUiState(
    revision = revision,
    lockLoop = lockLoop,
    uptimeMillis = uptimeMillis,
    maxSchedulesPerChannel = maxSchedulesPerChannel,
    readOnly = capabilities.readOnly,
    channelStateWriteEnabled = !capabilities.readOnly && capabilities.supportsChannelState,
    scheduleWriteEnabled = !capabilities.readOnly &&
        capabilities.supportsConfigApply &&
        capabilities.supportsSchedules,
    spansMidnightSupported = capabilities.supportsSpansMidnight,
    temporaryOverrideWriteEnabled = !capabilities.readOnly &&
        capabilities.supportsTemporaryOverride,
    displayNameWriteEnabled = !capabilities.readOnly &&
        capabilities.supportsChannelDisplayName,
    channels = channels.map(DeviceTimerChannelSnapshot::toUiState)
)

private fun DeviceTimerChannelSnapshot.toUiState() = DeviceTimerChannelUiState(
    slotId = slotId,
    channelNumber = channelNumber,
    defaultName = defaultName,
    displayName = displayName,
    regime = regime,
    operatingState = operatingState,
    scheduleCount = scheduleCount,
    activeScheduleSlotId = activeScheduleSlotId,
    activeScheduleName = activeScheduleName,
    nextTransitionType = nextTransitionType,
    nextTransitionAtEpochMillis = nextTransitionAtEpochMillis,
    runtimeReason = runtimeReason,
    clockReady = clockReady,
    temporaryOverrideActive = temporaryOverrideActive,
    temporaryOverrideRemainingMillis = temporaryOverrideRemainingMillis,
    outputHealth = outputHealth,
    physicalFeedbackAvailable = physicalFeedbackAvailable,
    displayNameEditable = displayNameEditable,
    schedules = schedules?.map(DeviceTimerScheduleSnapshot::toUiState)
)

private fun DeviceTimerScheduleSnapshot.toUiState() = DeviceTimerScheduleUiState(
    index = index,
    slotId = slotId,
    enabled = enabled,
    name = name,
    weekdays = weekdays,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    spansMidnight = spansMidnight
)
