package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.root

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
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelSnapshot
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlFailure
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlSnapshot
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerNextTransitionType
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerOperatingState
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerOutputHealth
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerRuntimeReason
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.common.devicepresence.toDeviceConnectionVisualState
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
    private val controlSurfacePreparationOperations: DeviceControlSurfacePreparationOperations,
    private val currentEpochMillis: () -> Long = System::currentTimeMillis
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

    fun togglePower(slotId: String) {
        val channel = lastControlPresentation?.channels
            ?.firstOrNull { candidate -> candidate.slotId == slotId }
            ?: return
        val mutation = channel.powerMutation(currentEpochMillis())
        val deviceUid = lastControlPresentation.powerMutationDeviceUid(
            DeviceTimerPowerRequest(
                boundDeviceUid = boundDeviceUid,
                contentEnabled = _uiState.value.contentEnabled,
                slotId = slotId,
                mutation = mutation,
                pendingChannelSlotIds = pendingChannelSlotIds
            )
        ) ?: return

        pendingChannelSlotIds += slotId
        renderBoundState()
        val mutationJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val result = runCatching {
                timerControlOperations.executePowerMutation(deviceUid, slotId, mutation)
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
            connectionVisualState = root.toDeviceConnectionVisualState(),
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

data class DeviceTimerControlUiState(
    val authority: DeviceTimerControlAuthorityUiState,
    val permissions: DeviceTimerControlPermissionsUiState,
    val summary: DeviceTimerControlSummaryUiState,
    val channels: List<DeviceTimerChannelUiState>
) {
    val revision: Long get() = authority.revision
    val lockLoop: Boolean get() = authority.lockLoop
    val uptimeMillis: Long get() = authority.uptimeMillis
    val maxSchedulesPerChannel: Int get() = authority.maxSchedulesPerChannel
    val readOnly: Boolean get() = permissions.readOnly
    val channelStateWriteEnabled: Boolean get() = permissions.channelStateWriteEnabled
    val scheduleReadEnabled: Boolean get() = permissions.scheduleReadEnabled
    val scheduleWriteEnabled: Boolean get() = permissions.scheduleWriteEnabled
    val spansMidnightSupported: Boolean get() = permissions.spansMidnightSupported
    val temporaryOverrideWriteEnabled: Boolean get() = permissions.temporaryOverrideWriteEnabled
    val displayNameWriteEnabled: Boolean get() = permissions.displayNameWriteEnabled
    val activeChannelCount: Int get() = summary.activeChannelCount
    val statusNotices: Set<DeviceTimerStatusNotice> get() = summary.statusNotices
}

data class DeviceTimerControlAuthorityUiState(
    val revision: Long,
    val lockLoop: Boolean,
    val uptimeMillis: Long,
    val maxSchedulesPerChannel: Int
)

data class DeviceTimerControlPermissionsUiState(
    val readOnly: Boolean,
    val channelStateWriteEnabled: Boolean,
    val scheduleReadEnabled: Boolean,
    val scheduleWriteEnabled: Boolean,
    val spansMidnightSupported: Boolean,
    val temporaryOverrideWriteEnabled: Boolean,
    val displayNameWriteEnabled: Boolean
)

data class DeviceTimerControlSummaryUiState(
    val activeChannelCount: Int,
    val statusNotices: Set<DeviceTimerStatusNotice>
)

data class DeviceTimerChannelUiState(
    val identity: DeviceTimerChannelIdentityUiState,
    val state: DeviceTimerChannelStateUiState,
    val transition: DeviceTimerChannelTransitionUiState,
    val runtime: DeviceTimerChannelRuntimeUiState,
    val schedules: List<DeviceTimerScheduleUiState>?
) {
    val slotId: String get() = identity.slotId
    val channelNumber: Int get() = identity.channelNumber
    val defaultName: String get() = identity.defaultName
    val displayName: String get() = identity.displayName
    val displayNameEditable: Boolean get() = identity.displayNameEditable
    val regime: DeviceTimerChannelRegime get() = state.regime
    val operatingState: DeviceTimerOperatingState get() = state.operatingState
    val scheduleCount: Int get() = state.scheduleCount
    val outputHealth: DeviceTimerOutputHealth get() = state.outputHealth
    val activeScheduleSlotId: Int? get() = transition.activeScheduleSlotId
    val activeScheduleName: String? get() = transition.activeScheduleName
    val nextTransitionType: DeviceTimerNextTransitionType get() = transition.nextTransitionType
    val nextTransitionAtEpochMillis: Long? get() = transition.nextTransitionAtEpochMillis
    val runtimeReason: DeviceTimerRuntimeReason get() = runtime.reason
    val clockReady: Boolean get() = runtime.clockReady
    val temporaryOverrideActive: Boolean get() = runtime.temporaryOverrideActive
    val temporaryOverrideRemainingMillis: Long get() = runtime.temporaryOverrideRemainingMillis
    val physicalFeedbackAvailable: Boolean get() = runtime.physicalFeedbackAvailable
}

data class DeviceTimerChannelIdentityUiState(
    val slotId: String,
    val channelNumber: Int,
    val defaultName: String,
    val displayName: String,
    val displayNameEditable: Boolean
)

data class DeviceTimerChannelStateUiState(
    val regime: DeviceTimerChannelRegime,
    val operatingState: DeviceTimerOperatingState,
    val scheduleCount: Int,
    val outputHealth: DeviceTimerOutputHealth
)

data class DeviceTimerChannelTransitionUiState(
    val activeScheduleSlotId: Int?,
    val activeScheduleName: String?,
    val nextTransitionType: DeviceTimerNextTransitionType,
    val nextTransitionAtEpochMillis: Long?
)

data class DeviceTimerChannelRuntimeUiState(
    val reason: DeviceTimerRuntimeReason,
    val clockReady: Boolean,
    val temporaryOverrideActive: Boolean,
    val temporaryOverrideRemainingMillis: Long,
    val physicalFeedbackAvailable: Boolean
)

/** Screen-level commercial notices derived from authoritative Timer runtime state. */
enum class DeviceTimerStatusNotice {
    CLOCK_UNAVAILABLE,
    RUNTIME_LOCKED
}

data class DeviceTimerScheduleUiState(
    val identity: DeviceTimerScheduleIdentityUiState,
    val window: DeviceTimerScheduleWindowUiState
) {
    val index: Int get() = identity.index
    val slotId: Int get() = identity.slotId
    val enabled: Boolean get() = identity.enabled
    val name: String get() = identity.name
    val weekdays: List<Boolean> get() = window.weekdays
    val startTimeMillis: Long get() = window.startTimeMillis
    val endTimeMillis: Long get() = window.endTimeMillis
    val spansMidnight: Boolean get() = window.spansMidnight
}

data class DeviceTimerScheduleIdentityUiState(
    val index: Int,
    val slotId: Int,
    val enabled: Boolean,
    val name: String
)

data class DeviceTimerScheduleWindowUiState(
    val weekdays: List<Boolean>,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val spansMidnight: Boolean
)

private fun DeviceRootSnapshot?.isTimerControlRootAvailable(deviceUid: String): Boolean {
    val root = this ?: return false
    val identityMatches = root.deviceUid == deviceUid && root.family == OwnerDeviceFamily.TIMER
    val authorityReady = root.availability == OwnerDeviceAvailability.REACHABLE &&
        root.catalogState == DeviceRootCatalogState.VALID
    return identityMatches && authorityReady
}

internal fun DeviceTimerControlSnapshot.toUiState(): DeviceTimerControlUiState {
    val channelPresentation = channels.map(DeviceTimerChannelSnapshot::toUiState)
    val notices = buildSet {
        if (channels.any { channel -> !channel.clockReady }) {
            add(DeviceTimerStatusNotice.CLOCK_UNAVAILABLE)
        }
        if (lockLoop) add(DeviceTimerStatusNotice.RUNTIME_LOCKED)
    }
    return DeviceTimerControlUiState(
        authority = DeviceTimerControlAuthorityUiState(
            revision = revision,
            lockLoop = lockLoop,
            uptimeMillis = uptimeMillis,
            maxSchedulesPerChannel = maxSchedulesPerChannel
        ),
        permissions = DeviceTimerControlPermissionsUiState(
            readOnly = capabilities.readOnly,
            channelStateWriteEnabled = !capabilities.readOnly &&
                capabilities.supportsChannelState,
            scheduleReadEnabled = capabilities.supportsSchedules,
            scheduleWriteEnabled = !capabilities.readOnly &&
                capabilities.supportsConfigApply &&
                capabilities.supportsSchedules,
            spansMidnightSupported = capabilities.supportsSpansMidnight,
            temporaryOverrideWriteEnabled = !capabilities.readOnly &&
                capabilities.supportsTemporaryOverride,
            displayNameWriteEnabled = !capabilities.readOnly &&
                capabilities.supportsChannelDisplayName
        ),
        summary = DeviceTimerControlSummaryUiState(
            activeChannelCount = channels.count { channel ->
                channel.operatingState == DeviceTimerOperatingState.ON
            },
            statusNotices = notices
        ),
        channels = channelPresentation
    )
}

internal fun DeviceTimerChannelSnapshot.toUiState() = DeviceTimerChannelUiState(
    identity = DeviceTimerChannelIdentityUiState(
        slotId = slotId,
        channelNumber = channelNumber,
        defaultName = defaultName,
        displayName = displayName,
        displayNameEditable = displayNameEditable
    ),
    state = DeviceTimerChannelStateUiState(
        regime = regime,
        operatingState = operatingState,
        scheduleCount = scheduleCount,
        outputHealth = outputHealth
    ),
    transition = DeviceTimerChannelTransitionUiState(
        activeScheduleSlotId = activeScheduleSlotId,
        activeScheduleName = activeScheduleName,
        nextTransitionType = nextTransitionType,
        nextTransitionAtEpochMillis = nextTransitionAtEpochMillis
    ),
    runtime = DeviceTimerChannelRuntimeUiState(
        reason = runtimeReason,
        clockReady = clockReady,
        temporaryOverrideActive = temporaryOverrideActive,
        temporaryOverrideRemainingMillis = temporaryOverrideRemainingMillis,
        physicalFeedbackAvailable = physicalFeedbackAvailable
    ),
    schedules = schedules?.map(DeviceTimerScheduleSnapshot::toUiState)
)

private fun DeviceTimerScheduleSnapshot.toUiState() = DeviceTimerScheduleUiState(
    identity = DeviceTimerScheduleIdentityUiState(
        index = index,
        slotId = slotId,
        enabled = enabled,
        name = name
    ),
    window = DeviceTimerScheduleWindowUiState(
        weekdays = weekdays,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        spansMidnight = spansMidnight
    )
)

private fun DeviceTimerControlUiState?.powerMutationDeviceUid(
    request: DeviceTimerPowerRequest
): String? {
    val control = this
    val channel = control?.channels?.firstOrNull { it.slotId == request.slotId }
    val mutationWriteEnabled = when (request.mutation) {
        is DeviceTimerPowerMutation.Persistent -> control?.channelStateWriteEnabled == true
        is DeviceTimerPowerMutation.ProgramPreservingOverride ->
            control?.temporaryOverrideWriteEnabled == true
    }
    val interactionReady = channel != null &&
        request.contentEnabled &&
        mutationWriteEnabled &&
        control?.lockLoop == false
    val changeRequired = when (request.mutation) {
        is DeviceTimerPowerMutation.Persistent ->
            channel?.regime != request.mutation.regime || channel?.temporaryOverrideActive == true
        is DeviceTimerPowerMutation.ProgramPreservingOverride -> channel != null
    }
    val requestReady = request.boundDeviceUid.isNotBlank() &&
        request.slotId !in request.pendingChannelSlotIds
    return request.boundDeviceUid.takeIf { requestReady && interactionReady && changeRequired }
}

private data class DeviceTimerPowerRequest(
    val boundDeviceUid: String,
    val contentEnabled: Boolean,
    val slotId: String,
    val mutation: DeviceTimerPowerMutation,
    val pendingChannelSlotIds: Set<String>
)

private fun DeviceTimerControlFailure.closesControlSurface(): Boolean = when (this) {
    DeviceTimerControlFailure.Unavailable,
    DeviceTimerControlFailure.NotConnected,
    DeviceTimerControlFailure.Unsupported -> true
    DeviceTimerControlFailure.InvalidData,
    is DeviceTimerControlFailure.Rejected -> false
}
