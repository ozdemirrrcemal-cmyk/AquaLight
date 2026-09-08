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
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlFailure
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
    private var lastControlFailure: DeviceTimerControlFailure? = null
    private var controlAvailable = false
    private var surfacePreparationPending = false
    private var rootObserveJob: Job? = null
    private var controlObserveJob: Job? = null
    private var surfacePreparationJob: Job? = null
    private val channelMutationJobs = mutableMapOf<String, Job>()
    private val pendingChannelSlotIds = mutableSetOf<String>()

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

    fun selectRegime(
        slotId: String,
        regime: DeviceTimerChannelRegime,
        temporaryDurationMillis: Long? = null
    ) {
        val deviceUid = lastControlPresentation.regimeMutationDeviceUid(
            boundDeviceUid = boundDeviceUid,
            contentEnabled = _uiState.value.contentEnabled,
            slotId = slotId,
            regime = regime,
            temporaryDurationMillis = temporaryDurationMillis,
            pendingChannelSlotIds = pendingChannelSlotIds
        ) ?: return

        pendingChannelSlotIds += slotId
        renderBoundState()
        val mutationJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val result = runCatching {
                if (temporaryDurationMillis == null) {
                    timerControlOperations.setRegime(deviceUid, slotId, regime)
                } else {
                    timerControlOperations.setTemporaryOverride(
                        deviceUid = deviceUid,
                        slotId = slotId,
                        regime = regime,
                        durationMillis = temporaryDurationMillis
                    )
                }
            }.getOrElse {
                DeviceTimerControlResult.Failed(DeviceTimerControlFailure.Unavailable)
            }
            if (boundDeviceUid == deviceUid) {
                acceptMutationResult(result)
                pendingChannelSlotIds -= slotId
                channelMutationJobs.remove(slotId)
                renderBoundState()
            }
        }
        channelMutationJobs[slotId] = mutationJob
        mutationJob.start()
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
        when (result) {
            is DeviceTimerControlResult.Available -> {
                lastControlPresentation = result.snapshot.toUiState()
                lastControlFailure = null
            }
            is DeviceTimerControlResult.Failed -> lastControlFailure = result.failure
        }
    }

    private fun acceptMutationResult(result: DeviceTimerControlResult) {
        when (result) {
            is DeviceTimerControlResult.Available -> acceptControlResult(result)
            is DeviceTimerControlResult.Failed -> {
                lastControlFailure = result.failure
                if (result.failure.closesControlSurface()) controlAvailable = false
            }
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
            control = lastControlPresentation,
            controlFailure = lastControlFailure,
            pendingChannelSlotIds = pendingChannelSlotIds.toSet()
        )
    }

    private fun clearBinding() {
        cancelJobs()
        boundDeviceUid = ""
        latestRootSnapshot = null
        lastControlPresentation = null
        lastControlFailure = null
        controlAvailable = false
        surfacePreparationPending = false
        pendingChannelSlotIds.clear()
        _uiState.value = DeviceTimerRootUiState()
    }

    private fun cancelJobs() {
        rootObserveJob?.cancel()
        controlObserveJob?.cancel()
        surfacePreparationJob?.cancel()
        channelMutationJobs.values.forEach { job -> job.cancel() }
        rootObserveJob = null
        controlObserveJob = null
        surfacePreparationJob = null
        channelMutationJobs.clear()
        pendingChannelSlotIds.clear()
    }
}

data class DeviceTimerRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE,
    val contentEnabled: Boolean = false,
    val showBlockingPreparation: Boolean = false,
    val control: DeviceTimerControlUiState? = null,
    val controlFailure: DeviceTimerControlFailure? = null,
    val pendingChannelSlotIds: Set<String> = emptySet()
)

@Suppress("LongParameterList")
data class DeviceTimerControlUiState(
    val revision: Long,
    val lockLoop: Boolean,
    val uptimeMillis: Long,
    val maxSchedulesPerChannel: Int,
    val readOnly: Boolean,
    val channelStateWriteEnabled: Boolean,
    val scheduleReadEnabled: Boolean,
    val scheduleWriteEnabled: Boolean,
    val spansMidnightSupported: Boolean,
    val temporaryOverrideWriteEnabled: Boolean,
    val displayNameWriteEnabled: Boolean,
    val activeChannelCount: Int,
    val statusNotices: Set<DeviceTimerStatusNotice>,
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

/** Screen-level commercial notices derived from authoritative Timer runtime state. */
enum class DeviceTimerStatusNotice {
    CLOCK_UNAVAILABLE,
    RUNTIME_LOCKED
}

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

internal fun DeviceTimerControlSnapshot.toUiState(): DeviceTimerControlUiState {
    val channelPresentation = channels.map(DeviceTimerChannelSnapshot::toUiState)
    val notices = buildSet {
        if (channels.any { channel -> !channel.clockReady }) {
            add(DeviceTimerStatusNotice.CLOCK_UNAVAILABLE)
        }
        if (lockLoop) add(DeviceTimerStatusNotice.RUNTIME_LOCKED)
    }
    return DeviceTimerControlUiState(
        revision = revision,
        lockLoop = lockLoop,
        uptimeMillis = uptimeMillis,
        maxSchedulesPerChannel = maxSchedulesPerChannel,
        readOnly = capabilities.readOnly,
        channelStateWriteEnabled = !capabilities.readOnly && capabilities.supportsChannelState,
        scheduleReadEnabled = capabilities.supportsSchedules,
        scheduleWriteEnabled = !capabilities.readOnly &&
            capabilities.supportsConfigApply &&
            capabilities.supportsSchedules,
        spansMidnightSupported = capabilities.supportsSpansMidnight,
        temporaryOverrideWriteEnabled = !capabilities.readOnly &&
            capabilities.supportsTemporaryOverride,
        displayNameWriteEnabled = !capabilities.readOnly &&
            capabilities.supportsChannelDisplayName,
        activeChannelCount = channels.count { channel ->
            channel.operatingState == DeviceTimerOperatingState.ON
        },
        statusNotices = notices,
        channels = channelPresentation
    )
}

internal fun DeviceTimerChannelSnapshot.toUiState() = DeviceTimerChannelUiState(
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

@Suppress("ComplexCondition", "LongParameterList")
private fun DeviceTimerControlUiState?.regimeMutationDeviceUid(
    boundDeviceUid: String,
    contentEnabled: Boolean,
    slotId: String,
    regime: DeviceTimerChannelRegime,
    temporaryDurationMillis: Long?,
    pendingChannelSlotIds: Set<String>
): String? {
    val channel = this?.channels?.firstOrNull { candidate -> candidate.slotId == slotId }
    val persistentMutation = temporaryDurationMillis == null
    val capabilityReady = if (persistentMutation) {
        this?.channelStateWriteEnabled == true
    } else {
        this?.temporaryOverrideWriteEnabled == true
    }
    val interactionReady = this != null &&
        contentEnabled &&
        capabilityReady &&
        !lockLoop
    return boundDeviceUid.takeIf {
        it.isNotBlank() &&
            interactionReady &&
            (!persistentMutation || channel?.regime != regime) &&
            (persistentMutation || regime != DeviceTimerChannelRegime.AUTO) &&
            (persistentMutation || temporaryDurationMillis?.let {
                duration -> duration in 1L..MAX_TEMPORARY_OVERRIDE_MILLIS
            } == true) &&
            slotId !in pendingChannelSlotIds
    }
}

private const val MAX_TEMPORARY_OVERRIDE_MILLIS = 86_400_000L

private fun DeviceTimerControlFailure.closesControlSurface(): Boolean = when (this) {
    DeviceTimerControlFailure.Unavailable,
    DeviceTimerControlFailure.NotConnected,
    DeviceTimerControlFailure.Unsupported -> true
    DeviceTimerControlFailure.InvalidData,
    is DeviceTimerControlFailure.Rejected -> false
}
