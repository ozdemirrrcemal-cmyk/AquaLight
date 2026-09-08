package com.aqua.aqualight.ui.tabs.devices.detail.timer.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlFailure
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.DeviceTimerDisplayNameUpdate
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerChannelUiState
import com.aqua.aqualight.ui.tabs.devices.detail.timer.toUiState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceTimerChannelViewModel(
    private val operations: DeviceTimerControlOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceTimerChannelDetailUiState())
    val uiState: StateFlow<DeviceTimerChannelDetailUiState> = _uiState.asStateFlow()
    private val eventChannel = Channel<DeviceTimerChannelEvent>(Channel.BUFFERED)
    val events: Flow<DeviceTimerChannelEvent> = eventChannel.receiveAsFlow()

    private var observeJob: Job? = null
    private var mutationJob: Job? = null

    fun bind(deviceUidText: String, slotIdText: String) {
        val deviceUid = deviceUidText.trim()
        val slotId = slotIdText.trim()
        if (deviceUid.isBlank() || slotId.isBlank()) {
            _uiState.value = DeviceTimerChannelDetailUiState(
                loadState = DeviceTimerChannelLoadState.FAILED,
                failure = DeviceTimerControlFailure.Unsupported
            )
            return
        }
        if (_uiState.value.deviceUid == deviceUid && _uiState.value.slotId == slotId) return

        observeJob?.cancel()
        mutationJob?.cancel()
        _uiState.value = DeviceTimerChannelDetailUiState(
            deviceUid = deviceUid,
            slotId = slotId,
            loadState = DeviceTimerChannelLoadState.LOADING
        )
        acceptResult(deviceUid, slotId, operations.currentControl(deviceUid), preserveLoading = true)
        observeJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            operations.observeControl(deviceUid).collect { result ->
                acceptResult(deviceUid, slotId, result, preserveLoading = false)
            }
        }
        viewModelScope.launch {
            val result = runCatching { operations.refreshChannel(deviceUid, slotId) }
                .getOrElse { DeviceTimerControlResult.Failed(DeviceTimerControlFailure.Unavailable) }
            acceptResult(deviceUid, slotId, result, preserveLoading = false)
        }
    }

    fun setPersistentRegime(regime: DeviceTimerChannelRegime) {
        val state = _uiState.value
        val channel = state.channel
        val writeReady = state.channelStateWriteEnabled && !state.mutationPending
        val changeRequired = channel?.regime != regime || channel?.temporaryOverrideActive == true
        if (channel != null && writeReady && changeRequired) {
            mutate { operations.setRegime(state.deviceUid, state.slotId, regime) }
        }
    }

    fun toggleManualPower() {
        val channel = _uiState.value.channel ?: return
        val nextRegime = when (channel.operatingState) {
            DeviceTimerOperatingState.ON -> DeviceTimerChannelRegime.OFF
            DeviceTimerOperatingState.OFF -> DeviceTimerChannelRegime.ON
        }
        setPersistentRegime(nextRegime)
    }

    fun setWorkMode(workMode: DeviceTimerWorkMode) {
        val channel = _uiState.value.channel ?: return
        val regime = when (workMode) {
            DeviceTimerWorkMode.PROGRAM -> DeviceTimerChannelRegime.AUTO
            DeviceTimerWorkMode.MANUAL -> channel.regime.takeUnless {
                it == DeviceTimerChannelRegime.AUTO
            } ?: when (channel.operatingState) {
                DeviceTimerOperatingState.ON -> DeviceTimerChannelRegime.ON
                DeviceTimerOperatingState.OFF -> DeviceTimerChannelRegime.OFF
            }
        }
        setPersistentRegime(regime)
    }

    fun startTemporaryOverride(regime: DeviceTimerChannelRegime, durationMinutes: Int) {
        val state = _uiState.value
        val regimeValid = regime != DeviceTimerChannelRegime.AUTO
        val durationValid = durationMinutes in MIN_TEMPORARY_MINUTES..MAX_TEMPORARY_MINUTES
        val writeReady = state.temporaryOverrideWriteEnabled && !state.mutationPending
        if (regimeValid && durationValid && writeReady) {
            mutate {
                operations.setTemporaryOverride(
                    deviceUid = state.deviceUid,
                    slotId = state.slotId,
                    regime = regime,
                    durationMillis = durationMinutes * MILLIS_PER_MINUTE
                )
            }
        }
    }

    /** Firmware cancels a temporary override when the persistent regime is sent again. */
    fun resumePersistentMode() {
        val channel = _uiState.value.channel ?: return
        if (!channel.temporaryOverrideActive) return
        setPersistentRegime(channel.regime)
    }

    fun updateDisplayName(value: String?) {
        val state = _uiState.value
        val channel = state.channel
        val writeReady = state.displayNameWriteEnabled && !state.mutationPending
        if (channel == null || !writeReady) return
        when (val resolution = value.resolveTimerDisplayNameUpdate(channel.effectiveName)) {
            TimerDisplayNameResolution.Invalid ->
                eventChannel.trySend(DeviceTimerChannelEvent.InvalidDisplayName)
            TimerDisplayNameResolution.Unchanged -> Unit
            is TimerDisplayNameResolution.Apply -> mutate {
                operations.setDisplayName(state.deviceUid, state.slotId, resolution.update)
            }
        }
    }

    private fun mutate(block: suspend () -> DeviceTimerControlResult) {
        val state = _uiState.value
        if (state.deviceUid.isBlank() || state.slotId.isBlank() || state.mutationPending) return
        mutationJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _uiState.value = _uiState.value.copy(mutationPending = true, failure = null)
            val result = runCatching { block() }
                .getOrElse { DeviceTimerControlResult.Failed(DeviceTimerControlFailure.Unavailable) }
            acceptResult(state.deviceUid, state.slotId, result, preserveLoading = false)
            _uiState.value = _uiState.value.copy(mutationPending = false)
            if (result is DeviceTimerControlResult.Failed) {
                eventChannel.send(DeviceTimerChannelEvent.Failed(result.failure))
            }
        }.also { job -> job.start() }
    }

    private fun acceptResult(
        deviceUid: String,
        slotId: String,
        result: DeviceTimerControlResult,
        preserveLoading: Boolean
    ) {
        val state = _uiState.value
        if (state.deviceUid != deviceUid || state.slotId != slotId) return
        when (result) {
            is DeviceTimerControlResult.Available -> {
                val nextState = result.toChannelDetailState(state, slotId, preserveLoading)
                if (nextState == null) {
                    acceptFailure(DeviceTimerControlFailure.InvalidData)
                } else {
                    _uiState.value = nextState
                }
            }
            is DeviceTimerControlResult.Failed -> acceptFailure(result.failure)
        }
    }

    private fun acceptFailure(failure: DeviceTimerControlFailure) {
        _uiState.value = _uiState.value.copy(
            loadState = if (_uiState.value.channel == null) DeviceTimerChannelLoadState.FAILED
            else _uiState.value.loadState,
            failure = failure
        )
    }
}

data class DeviceTimerChannelDetailUiState(
    val deviceUid: String = "",
    val slotId: String = "",
    val loadState: DeviceTimerChannelLoadState = DeviceTimerChannelLoadState.IDLE,
    val channel: DeviceTimerChannelUiState? = null,
    val runtimeLocked: Boolean = false,
    val scheduleReadEnabled: Boolean = false,
    val channelStateWriteEnabled: Boolean = false,
    val temporaryOverrideWriteEnabled: Boolean = false,
    val displayNameWriteEnabled: Boolean = false,
    val mutationPending: Boolean = false,
    val failure: DeviceTimerControlFailure? = null
)

enum class DeviceTimerChannelLoadState { IDLE, LOADING, CONTENT, FAILED }

enum class DeviceTimerWorkMode { MANUAL, PROGRAM }

sealed interface DeviceTimerChannelEvent {
    data class Failed(val failure: DeviceTimerControlFailure) : DeviceTimerChannelEvent
    data object InvalidDisplayName : DeviceTimerChannelEvent
}

private val DeviceTimerChannelUiState.effectiveName: String
    get() = displayName.ifBlank { defaultName }

private sealed interface TimerDisplayNameResolution {
    data object Invalid : TimerDisplayNameResolution
    data object Unchanged : TimerDisplayNameResolution
    data class Apply(val update: DeviceTimerDisplayNameUpdate) : TimerDisplayNameResolution
}

private fun String?.resolveTimerDisplayNameUpdate(
    effectiveName: String
): TimerDisplayNameResolution = when {
    this == null -> TimerDisplayNameResolution.Apply(
        DeviceTimerDisplayNameUpdate.ResetToDefault
    )
    !trim().isValidTimerDisplayName() -> TimerDisplayNameResolution.Invalid
    trim() == effectiveName -> TimerDisplayNameResolution.Unchanged
    else -> TimerDisplayNameResolution.Apply(DeviceTimerDisplayNameUpdate.Value(trim()))
}

private fun DeviceTimerControlResult.Available.toChannelDetailState(
    previous: DeviceTimerChannelDetailUiState,
    slotId: String,
    preserveLoading: Boolean
): DeviceTimerChannelDetailUiState? = snapshot.channels.singleOrNull { it.slotId == slotId }
    ?.let { channel ->
        val capabilities = snapshot.capabilities
        previous.copy(
            loadState = if (preserveLoading) previous.loadState
            else DeviceTimerChannelLoadState.CONTENT,
            channel = channel.toUiState(),
            runtimeLocked = snapshot.lockLoop,
            scheduleReadEnabled = capabilities.supportsSchedules,
            channelStateWriteEnabled = !capabilities.readOnly &&
                capabilities.supportsChannelState && !snapshot.lockLoop,
            temporaryOverrideWriteEnabled = !capabilities.readOnly &&
                capabilities.supportsTemporaryOverride && !snapshot.lockLoop,
            displayNameWriteEnabled = !capabilities.readOnly &&
                capabilities.supportsConfigApply &&
                capabilities.supportsChannelDisplayName &&
                channel.displayNameEditable && !snapshot.lockLoop,
            failure = null
        )
    }

private fun String.isValidTimerDisplayName(): Boolean =
    isNotBlank() &&
        none { character -> character.isISOControl() } &&
        toByteArray(Charsets.UTF_8).size <= MAX_DISPLAY_NAME_UTF8_BYTES

private const val MIN_TEMPORARY_MINUTES = 1
private const val MAX_TEMPORARY_MINUTES = 1_440
private const val MILLIS_PER_MINUTE = 60_000L
private const val MAX_DISPLAY_NAME_UTF8_BYTES = 48
