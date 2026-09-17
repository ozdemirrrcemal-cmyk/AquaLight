package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualFailure
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMutationResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualOperations
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualReadResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualScene
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.toCommercialLightError
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightManualControlViewModel(
    private val manualOperations: DeviceLightManualOperations,
    private val libraryOperations: DeviceLightLibraryOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightManualControlUiState())
    internal val uiState: StateFlow<DeviceLightManualControlUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DeviceLightManualControlEffect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    internal val effects: SharedFlow<DeviceLightManualControlEffect> = _effects.asSharedFlow()

    internal val libraryActions = DeviceLightManualLibraryActions(
        operations = libraryOperations,
        scope = viewModelScope,
        currentDeviceUid = { boundDeviceUid },
        currentState = { _uiState.value },
        emitEffect = ::emitEffect
    )

    private var boundDeviceUid = ""
    private var bindingVersion = 0L
    private var draftVersion = 0L
    private var acknowledgedDraftVersion = 0L
    private var latestSnapshot: DeviceLightManualSnapshot? = null
    private var observationJobs: List<Job> = emptyList()
    private var mutationTail: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Manual light destination deviceUid must not be blank." }
        if (boundDeviceUid == deviceUid) return
        observationJobs.forEach { job -> job.cancel() }
        mutationTail?.cancel()
        mutationTail = null
        boundDeviceUid = deviceUid
        bindingVersion += 1L
        draftVersion = 0L
        acknowledgedDraftVersion = 0L
        latestSnapshot = null
        _uiState.value = deviceLightManualInitialState(deviceUid)
        observationJobs = listOf(
            viewModelScope.launch {
                manualOperations.observe(deviceUid).collect(::applyManualResult)
            },
            libraryActions.bind(deviceUid)
        )
    }

    internal fun updateChannel(channelId: DeviceLightManualChannelId, percent: Int) {
        val state = _uiState.value
        val requestedPercent = percent.coerceIn(PERCENT_RANGE)
        val current = state.channels.singleOrNull { channel -> channel.id == channelId }
        if (state.controlsEnabled && current != null && current.percent != requestedPercent) {
            draftVersion += 1L
            _uiState.value = state.copy(
                channels = state.channels.map { channel ->
                    if (channel.id == channelId) {
                        channel.copy(percent = requestedPercent)
                    } else {
                        channel
                    }
                },
                selectedPreset = null
            )
        }
    }

    internal fun commitScene() {
        val state = _uiState.value
        if (!state.controlsEnabled || state.channels.isEmpty()) return
        enqueueMutation(
            version = draftVersion,
            command = DeviceLightManualCommand.SetScene(state.toApplicationScene())
        )
    }

    internal fun stepChannel(channelId: DeviceLightManualChannelId, delta: Int) {
        val channel = _uiState.value.channels.singleOrNull { it.id == channelId } ?: return
        val previousVersion = draftVersion
        updateChannel(channelId, channel.percent + delta)
        if (draftVersion != previousVersion) commitScene()
    }

    internal fun applyPreset(presetId: DeviceLightManualPresetId) {
        val state = _uiState.value
        if (!state.controlsEnabled) return
        val preset = state.presets.singleOrNull { it.id == presetId } ?: return
        draftVersion += 1L
        _uiState.value = state.copy(
            channels = state.channels.map { channel ->
                channel.copy(percent = preset.scene.getValue(channel.id))
            },
            selectedPreset = presetId
        )
        commitScene()
    }

    internal fun turnOff() {
        val state = _uiState.value
        if (!state.controlsEnabled) return
        draftVersion += 1L
        _uiState.value = state.copy(
            channels = state.channels.map { channel -> channel.copy(percent = PERCENT_RANGE.first) },
            selectedPreset = null
        )
        enqueueMutation(draftVersion, DeviceLightManualCommand.TurnOff)
    }

    private fun applyManualResult(result: DeviceLightManualReadResult) {
        when (result) {
            is DeviceLightManualReadResult.Available -> {
                latestSnapshot = result.snapshot
                _uiState.update { state ->
                    result.snapshot.mergeInto(
                        state = state,
                        replaceScene = draftVersion == acknowledgedDraftVersion
                    )
                }
            }
            is DeviceLightManualReadResult.Failed -> _uiState.update { state ->
                state.copy(
                    connectionVisualState = result.failure.connectionState(),
                    contentEnabled = false,
                    protection = null,
                    initialLoading = false
                )
            }
        }
    }

    private fun enqueueMutation(version: Long, command: DeviceLightManualCommand) {
        val deviceUid = boundDeviceUid
        val expectedBindingVersion = bindingVersion
        val precedingMutation = mutationTail
        mutationTail = viewModelScope.launch {
            precedingMutation?.join()
            val result = when (command) {
                is DeviceLightManualCommand.SetScene ->
                    manualOperations.setScene(deviceUid, command.scene)
                DeviceLightManualCommand.TurnOff -> manualOperations.turnOff(deviceUid)
            }
            if (bindingVersion == expectedBindingVersion && boundDeviceUid == deviceUid) {
                applyMutationResult(version, result)
            }
        }
    }

    private fun applyMutationResult(
        version: Long,
        result: DeviceLightManualMutationResult
    ) {
        when (result) {
            is DeviceLightManualMutationResult.Success -> {
                latestSnapshot = result.snapshot
                acknowledgedDraftVersion = maxOf(acknowledgedDraftVersion, version)
                _uiState.update { state ->
                    result.snapshot.mergeInto(state, replaceScene = draftVersion == version)
                }
            }
            is DeviceLightManualMutationResult.Failed -> {
                if (draftVersion == version) {
                    acknowledgedDraftVersion = version
                    latestSnapshot?.let { snapshot ->
                        _uiState.update { state -> snapshot.mergeInto(state, replaceScene = true) }
                    }
                }
                _uiState.update { state ->
                    state.copy(connectionVisualState = result.failure.connectionState())
                }
                emitEffect(
                    DeviceLightManualControlEffect.ShowError(
                        result.failure.toCommercialLightError().messageRes
                    )
                )
            }
        }
    }

    private fun emitEffect(effect: DeviceLightManualControlEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}

internal sealed interface DeviceLightManualControlEffect {
    data object OpenLibrary : DeviceLightManualControlEffect

    data class OpenSaveAs(val usedManualNames: List<String>) :
        DeviceLightManualControlEffect

    data class ShowSuccess(
        @StringRes val messageRes: Int
    ) : DeviceLightManualControlEffect

    data class ShowError(
        @StringRes val messageRes: Int
    ) : DeviceLightManualControlEffect
}

private sealed interface DeviceLightManualCommand {
    data class SetScene(val scene: DeviceLightManualScene) : DeviceLightManualCommand
    data object TurnOff : DeviceLightManualCommand
}

private fun DeviceLightManualControlUiState.toApplicationScene() = DeviceLightManualScene(
    channels.associate { channel -> channel.id.toApplicationChannel() to channel.percent }
)

private fun DeviceLightManualFailure.connectionState(): DeviceConnectionVisualState = when (this) {
    DeviceLightManualFailure.NOT_CONNECTED -> DeviceConnectionVisualState.OFFLINE
    DeviceLightManualFailure.UNAVAILABLE,
    DeviceLightManualFailure.UNSUPPORTED,
    DeviceLightManualFailure.REJECTED,
    DeviceLightManualFailure.INVALID_DATA -> DeviceConnectionVisualState.WARNING
}
