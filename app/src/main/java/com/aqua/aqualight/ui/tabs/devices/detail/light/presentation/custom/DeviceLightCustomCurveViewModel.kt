@file:Suppress("TooManyFunctions")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomMutationResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomOperations
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomReadResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomSnapshot
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import java.util.concurrent.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

internal class DeviceLightCustomCurveViewModel(
    private val customOperations: DeviceLightCustomOperations,
    private val libraryOperations: DeviceLightLibraryOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightCustomCurveUiState())
    val uiState: StateFlow<DeviceLightCustomCurveUiState> = _uiState.asStateFlow()
    val currentState: DeviceLightCustomCurveUiState get() = _uiState.value

    private val _effects = MutableSharedFlow<DeviceLightCustomCurveEffect>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: SharedFlow<DeviceLightCustomCurveEffect> = _effects.asSharedFlow()

    private var boundDeviceUid = ""
    private var persistedDraft: DeviceLightCustomDraft? = null
    private var restoredDraft: DeviceLightCustomDraft? = null
    private var restoreDirty = false

    fun bind(
        deviceUidText: String,
        restoredDraft: DeviceLightCustomDraft? = null,
        restoredDirty: Boolean = false
    ) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Custom light destination deviceUid must not be blank." }
        if (boundDeviceUid == deviceUid) return
        boundDeviceUid = deviceUid
        this.restoredDraft = restoredDraft
        restoreDirty = restoredDraft != null && restoredDirty
        _uiState.value = DeviceLightCustomCurveUiState(
            deviceUid = deviceUid,
            initialLoading = true
        )
        refreshFromDevice()
    }

    fun refreshIfClean() {
        if (!_uiState.value.hasUnsavedChanges) refreshFromDevice()
    }

    fun refreshFromDevice() {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch {
            _uiState.update { state -> state.copy(initialLoading = true, readFailed = false) }
            when (val result = customOperations.read(deviceUid)) {
                is DeviceLightCustomReadResult.Available -> applySnapshot(result.snapshot)
                is DeviceLightCustomReadResult.Failed -> {
                    _uiState.update { state ->
                        state.copy(
                            connectionVisualState = result.failure.connectionState(),
                            initialLoading = false,
                            contentEnabled = false,
                            readFailed = true
                        )
                    }
                    emit(DeviceLightCustomCurveEffect.ShowError(result.failure.messageRes()))
                }
            }
        }
    }

    fun selectEveryDay() = mutateDraft { draft -> draft.copy(weekdaysMask = EVERY_DAY_MASK) }

    fun toggleWeekday(dayIndex: Int) {
        require(dayIndex in FIRST_WEEKDAY_INDEX..LAST_WEEKDAY_INDEX)
        mutateDraft { draft ->
            val bit = 1 shl dayIndex
            val changed = draft.weekdaysMask xor bit
            draft.copy(weekdaysMask = changed.takeIf { mask -> mask != 0 } ?: draft.weekdaysMask)
        }
    }

    fun requestAddPoint() {
        val state = _uiState.value
        if (!state.contentEnabled || state.operationInProgress) return
        if (state.draft.points.size >= state.maxPoints) return emitPointLimit(state.maxPoints)
        emit(DeviceLightCustomCurveEffect.OpenTimePicker(null))
    }

    fun requestEditSelectedTime() {
        if (_uiState.value.operationInProgress) return
        _uiState.value.selectedTimeMs?.let { time ->
            emit(DeviceLightCustomCurveEffect.OpenTimePicker(time))
        }
    }

    fun selectOrAddGraphTime(timeMs: Long) {
        val state = _uiState.value
        if (!state.contentEnabled || state.operationInProgress) return
        val aligned = timeMs.alignedTime()
        val existing = state.draft.points.singleOrNull { point -> point.timeMs == aligned }
        if (existing != null) {
            _uiState.update { state -> state.copy(selectedTimeMs = existing.timeMs) }
        } else {
            if (state.draft.points.size >= state.maxPoints) {
                emitPointLimit(state.maxPoints)
                return
            }
            addOrMovePoint(originalTimeMs = null, targetTimeMs = aligned)
        }
    }

    @Suppress("ReturnCount")
    fun addOrMovePoint(originalTimeMs: Long?, targetTimeMs: Long) {
        val state = _uiState.value
        if (!state.contentEnabled || state.operationInProgress) return
        val aligned = targetTimeMs.alignedTime()
        if (state.draft.points.any { point ->
                point.timeMs == aligned && point.timeMs != originalTimeMs
            }
        ) return
        val changed = if (originalTimeMs == null) {
            if (state.draft.points.size >= state.maxPoints) {
                emitPointLimit(state.maxPoints)
                return
            }
            state.draft.points + DeviceLightCustomPointUiState(
                timeMs = aligned,
                channels = interpolatedChannels(state.draft.points, state.channels, aligned)
            )
        } else {
            state.draft.points.map { point ->
                if (point.timeMs == originalTimeMs) point.copy(timeMs = aligned) else point
            }
        }.sortedBy { point -> point.timeMs }
        setDraft(state.draft.copy(points = changed), selectedTimeMs = aligned)
    }

    @Suppress("ReturnCount")
    fun duplicateSelectedPoint() {
        val state = _uiState.value
        if (state.operationInProgress) return
        val selected = state.selectedPoint ?: return
        if (state.draft.points.size >= state.maxPoints) {
            emitPointLimit(state.maxPoints)
            return
        }
        val occupied = state.draft.points.mapTo(mutableSetOf()) { point -> point.timeMs }
        val target = generateSequence(selected.timeMs + state.timeStepMs) { value ->
            value + state.timeStepMs
        }.takeWhile { value -> value < MILLIS_PER_DAY }.firstOrNull { value -> value !in occupied }
            ?: generateSequence(selected.timeMs - state.timeStepMs) { value ->
                value - state.timeStepMs
            }.takeWhile { value -> value >= 0L }.firstOrNull { value -> value !in occupied }
            ?: return
        setDraft(
            state.draft.copy(points = (state.draft.points + selected.copy(timeMs = target)).sortedBy {
                point -> point.timeMs
            }),
            selectedTimeMs = target
        )
    }

    fun deleteSelectedPoint() {
        val state = _uiState.value
        if (state.operationInProgress) return
        val selected = state.selectedPoint ?: return
        val points = state.draft.points.filterNot { point -> point.timeMs == selected.timeMs }
        val nextSelection = points.minByOrNull { point -> kotlin.math.abs(point.timeMs - selected.timeMs) }
            ?.timeMs
        setDraft(state.draft.copy(points = points), selectedTimeMs = nextSelection)
    }

    @Suppress("ReturnCount")
    fun updateSelectedChannel(channel: DeviceLightCustomChannelId, percent: Int) {
        val state = _uiState.value
        if (state.operationInProgress) return
        val selected = state.selectedPoint ?: return
        if (channel !in selected.channels) return
        val points = state.draft.points.map { point ->
            if (point.timeMs == selected.timeMs) {
                point.copy(
                    channels = point.channels + (
                        channel to percent.coerceIn(
                            MIN_LIGHT_CHANNEL_PERCENT,
                            MAX_LIGHT_CHANNEL_PERCENT
                        )
                    )
                )
            } else {
                point
            }
        }
        setDraft(state.draft.copy(points = points), state.selectedTimeMs)
    }

    fun stepSelectedChannel(channel: DeviceLightCustomChannelId, delta: Int) {
        val value = _uiState.value.selectedPoint?.channels?.get(channel) ?: return
        updateSelectedChannel(channel, value + delta)
    }

    fun updatePreviewTime(timeMs: Long) {
        _uiState.update { state -> state.copy(previewTimeMs = timeMs.alignedTime()) }
    }

    fun preview() {
        val state = _uiState.value
        if (!state.contentEnabled || state.operationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true) }
            when (val result = customOperations.preview(boundDeviceUid, state.previewTimeMs)) {
                DeviceLightCustomMutationResult.Success -> Unit
                is DeviceLightCustomMutationResult.Failed -> emit(
                    DeviceLightCustomCurveEffect.ShowError(result.failure.messageRes())
                )
            }
            _uiState.update { it.copy(operationInProgress = false) }
        }
    }

    fun clearPreview() {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch { customOperations.clearPreview(deviceUid) }
    }

    fun requestSaveAs() {
        if (!_uiState.value.canSaveAs) return
        viewModelScope.launch {
            val names = runCatching { libraryOperations.usedNames(DeviceLightLibraryKind.CUSTOM) }
                .getOrDefault(emptyList())
            emit(DeviceLightCustomCurveEffect.OpenSaveAs(names))
        }
    }

    fun saveAs(name: String) {
        val state = _uiState.value
        if (!state.canSaveAs) return
        viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true) }
            val points = state.draft.points.map { point ->
                DeviceLightLibraryCustomPoint(
                    timeMs = point.timeMs,
                    scene = DeviceLightLibraryScene(
                        point.channels.mapKeys { (channel, _) -> channel.toLibraryChannel() }
                    )
                )
            }
            when (
                val result = libraryOperations.saveCustom(
                    deviceUid = boundDeviceUid,
                    name = name,
                    weekdaysMask = state.draft.weekdaysMask,
                    points = points
                )
            ) {
                is DeviceLightLibraryMutationResult.Success -> {
                    persistedDraft = state.draft
                    _uiState.update { current ->
                        current.copy(operationInProgress = false, hasUnsavedChanges = false)
                    }
                    emit(DeviceLightCustomCurveEffect.ShowSuccess(R.string.device_light_library_saved_success))
                }
                is DeviceLightLibraryMutationResult.Failed -> {
                    _uiState.update { it.copy(operationInProgress = false) }
                    emit(DeviceLightCustomCurveEffect.ShowError(result.failure.messageRes()))
                }
            }
        }
    }

    fun resetToDevice() {
        clearPreview()
        restoredDraft = null
        restoreDirty = false
        refreshFromDevice()
    }

    fun openLibrary() = emit(DeviceLightCustomCurveEffect.OpenLibrary)

    private fun applySnapshot(snapshot: DeviceLightCustomSnapshot) {
        val channels = snapshot.channels.map(DeviceLightCustomChannel::toUiChannel)
        val deviceDraft = DeviceLightCustomDraft(
            weekdaysMask = snapshot.weekdaysMask,
            points = snapshot.points.map { point ->
                DeviceLightCustomPointUiState(
                    timeMs = point.timeMs,
                    channels = point.scene.channels.mapKeys { (channel, _) -> channel.toUiChannel() }
                )
            }
        )
        persistedDraft = deviceDraft
        val restored = restoredDraft?.takeIf { draft ->
            draft.points.all { point -> point.channels.keys == channels.toSet() } &&
                draft.points.size <= snapshot.maxPoints
        }
        val draft = if (restoreDirty && restored != null) restored else deviceDraft
        restoredDraft = null
        _uiState.value = DeviceLightCustomCurveUiState(
            deviceUid = snapshot.deviceUid,
            connectionVisualState = DeviceConnectionVisualState.ONLINE,
            channels = channels,
            draft = draft,
            selectedTimeMs = draft.points.firstOrNull()?.timeMs,
            previewTimeMs = snapshot.currentTimeMs?.alignedTime() ?: _uiState.value.previewTimeMs,
            maxPoints = snapshot.maxPoints,
            timeStepMs = snapshot.timeStepMs,
            contentEnabled = true,
            initialLoading = false,
            hasUnsavedChanges = restoreDirty && restored != null
        )
        restoreDirty = false
    }

    private fun mutateDraft(change: (DeviceLightCustomDraft) -> DeviceLightCustomDraft) {
        val state = _uiState.value
        if (!state.contentEnabled || state.operationInProgress) return
        setDraft(change(state.draft), state.selectedTimeMs)
    }

    private fun setDraft(draft: DeviceLightCustomDraft, selectedTimeMs: Long?) {
        _uiState.update { state ->
            state.copy(
                draft = draft,
                selectedTimeMs = selectedTimeMs,
                hasUnsavedChanges = draft != persistedDraft
            )
        }
    }

    private fun interpolatedChannels(
        points: List<DeviceLightCustomPointUiState>,
        availableChannels: List<DeviceLightCustomChannelId>,
        timeMs: Long
    ): Map<DeviceLightCustomChannelId, Int> {
        val channels = points.firstOrNull()?.channels?.keys
            ?: availableChannels
        val before = points.lastOrNull { point -> point.timeMs < timeMs }
        val after = points.firstOrNull { point -> point.timeMs > timeMs }
        return channels.associateWith { channel ->
            when {
                before == null && after == null -> 0
                before == null -> after?.channels?.get(channel) ?: 0
                after == null -> before.channels[channel] ?: 0
                else -> {
                    val fraction = (timeMs - before.timeMs).toDouble() /
                        (after.timeMs - before.timeMs).toDouble()
                    val start = before.channels[channel] ?: 0
                    val end = after.channels[channel] ?: 0
                    (start + (end - start) * fraction).roundToLong().toInt()
                }
            }
        }
    }

    private fun emit(effect: DeviceLightCustomCurveEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private fun emitPointLimit(maxPoints: Int) {
        emit(DeviceLightCustomCurveEffect.ShowPointLimit(maxPoints))
    }
}

internal sealed interface DeviceLightCustomCurveEffect {
    data class OpenTimePicker(val originalTimeMs: Long?) : DeviceLightCustomCurveEffect
    data class OpenSaveAs(val usedCustomNames: List<String>) : DeviceLightCustomCurveEffect
    data object OpenLibrary : DeviceLightCustomCurveEffect
    data class ShowSuccess(@StringRes val messageRes: Int) : DeviceLightCustomCurveEffect
    data class ShowError(@StringRes val messageRes: Int) : DeviceLightCustomCurveEffect
    data class ShowPointLimit(val maxPoints: Int) : DeviceLightCustomCurveEffect
}

private fun Long.alignedTime(): Long = coerceIn(0L, MILLIS_PER_DAY - MILLIS_PER_MINUTE)
    .div(MILLIS_PER_MINUTE)
    .times(MILLIS_PER_MINUTE)

private fun DeviceLightCustomChannelId.toLibraryChannel(): DeviceLightLibraryChannel = when (this) {
    DeviceLightCustomChannelId.RED -> DeviceLightLibraryChannel.RED
    DeviceLightCustomChannelId.GREEN -> DeviceLightLibraryChannel.GREEN
    DeviceLightCustomChannelId.BLUE -> DeviceLightLibraryChannel.BLUE
    DeviceLightCustomChannelId.WHITE -> DeviceLightLibraryChannel.WHITE
}

private fun DeviceLightCustomFailure.connectionState(): DeviceConnectionVisualState? = when (this) {
    DeviceLightCustomFailure.NOT_CONNECTED -> DeviceConnectionVisualState.OFFLINE
    else -> null
}

@StringRes
private fun DeviceLightCustomFailure.messageRes(): Int = when (this) {
    DeviceLightCustomFailure.NOT_CONNECTED -> R.string.device_light_library_load_not_connected_error
    else -> R.string.device_light_custom_operation_error
}

@StringRes
private fun DeviceLightLibraryFailure.messageRes(): Int = when (this) {
    DeviceLightLibraryFailure.DUPLICATE_NAME -> R.string.device_light_library_name_duplicate_error
    DeviceLightLibraryFailure.INVALID_NAME -> R.string.device_light_library_name_invalid_error
    DeviceLightLibraryFailure.NOT_CONNECTED -> R.string.device_light_library_load_not_connected_error
    else -> R.string.device_light_library_operation_error
}

private const val FIRST_WEEKDAY_INDEX = 0
private const val LAST_WEEKDAY_INDEX = 6
