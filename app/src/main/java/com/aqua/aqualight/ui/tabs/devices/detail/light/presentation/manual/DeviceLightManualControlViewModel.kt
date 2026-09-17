package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryTarget
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualPreviewSpec
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
    private val libraryOperations: DeviceLightLibraryOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightManualControlUiState())
    internal val uiState: StateFlow<DeviceLightManualControlUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DeviceLightManualControlEffect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    internal val effects: SharedFlow<DeviceLightManualControlEffect> = _effects.asSharedFlow()

    private var boundDeviceUid = ""
    private var manualNames: List<String> = emptyList()

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Manual light destination deviceUid must not be blank." }
        if (boundDeviceUid == deviceUid) return
        boundDeviceUid = deviceUid
        _uiState.value = deviceLightManualPreviewState(deviceUid)
        viewModelScope.launch {
            libraryOperations.observeLibrary(deviceUid).collect { result ->
                if (result is DeviceLightLibraryResult.Available) {
                    applyTarget(result.snapshot.target)
                    manualNames = result.snapshot.entries
                        .filter { entry -> entry.kind == DeviceLightLibraryKind.MANUAL }
                        .map { entry -> entry.name }
                }
            }
        }
    }

    internal fun updateChannel(channelId: DeviceLightManualChannelId, percent: Int) {
        _uiState.update { state ->
            if (!state.contentEnabled) return@update state
            state.copy(
                channels = state.channels.map { channel ->
                    if (channel.id == channelId) {
                        channel.copy(percent = percent.coerceIn(PERCENT_RANGE))
                    } else {
                        channel
                    }
                },
                selectedPreset = null
            )
        }
    }

    internal fun stepChannel(channelId: DeviceLightManualChannelId, delta: Int) {
        val channel = _uiState.value.channels.singleOrNull { it.id == channelId } ?: return
        updateChannel(channelId, channel.percent + delta)
    }

    internal fun applyPreset(presetId: DeviceLightManualPresetId) {
        _uiState.update { state ->
            if (!state.contentEnabled) return@update state
            val preset = state.presets.singleOrNull { it.id == presetId } ?: return@update state
            state.copy(
                channels = state.channels.map { channel ->
                    channel.copy(percent = preset.scene[channel.id] ?: channel.percent)
                },
                selectedPreset = presetId
            )
        }
    }

    internal fun turnOff() {
        _uiState.update { state ->
            if (!state.contentEnabled) return@update state
            state.copy(
                channels = state.channels.map { channel -> channel.copy(percent = 0) },
                selectedPreset = null
            )
        }
    }

    internal fun requestLoad() {
        emitEffect(DeviceLightManualControlEffect.OpenLibrary)
    }

    internal fun requestSaveAs() {
        viewModelScope.launch {
            val usedNames = runCatching {
                libraryOperations.usedNames(DeviceLightLibraryKind.MANUAL)
            }.getOrDefault(manualNames)
            emitEffect(DeviceLightManualControlEffect.OpenSaveAs(usedNames))
        }
    }

    internal fun saveAs(name: String) {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        val scene = DeviceLightLibraryScene(
            _uiState.value.channels.associate { channel ->
                channel.id.toLibraryChannel() to channel.percent
            }
        )
        viewModelScope.launch {
            when (val result = libraryOperations.saveManual(deviceUid, name, scene)) {
                is DeviceLightLibraryMutationResult.Success -> emitEffect(
                    DeviceLightManualControlEffect.ShowSuccess(
                        R.string.device_light_library_saved_success
                    )
                )
                is DeviceLightLibraryMutationResult.Failed -> emitEffect(
                    DeviceLightManualControlEffect.ShowError(result.failure.messageRes())
                )
            }
        }
    }

    private fun applyTarget(target: DeviceLightLibraryTarget) {
        val supportedIds = target.channels.mapTo(mutableSetOf()) { channel ->
            channel.toManualChannelId()
        }
        _uiState.update { state ->
            val supportsWhite = DeviceLightManualChannelId.WHITE in supportedIds
            state.copy(
                channels = state.channels.filter { channel -> channel.id in supportedIds },
                power = if (supportsWhite) {
                    state.power?.copy(
                        watts = target.estimatedPowerWatts
                            ?: AquaLightManualPreviewSpec.estimatedPowerWatts
                    )
                } else {
                    null
                }
            )
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

private fun DeviceLightManualChannelId.toLibraryChannel(): DeviceLightLibraryChannel = when (this) {
    DeviceLightManualChannelId.RED -> DeviceLightLibraryChannel.RED
    DeviceLightManualChannelId.GREEN -> DeviceLightLibraryChannel.GREEN
    DeviceLightManualChannelId.BLUE -> DeviceLightLibraryChannel.BLUE
    DeviceLightManualChannelId.WHITE -> DeviceLightLibraryChannel.WHITE
}

private fun DeviceLightLibraryChannel.toManualChannelId(): DeviceLightManualChannelId = when (this) {
    DeviceLightLibraryChannel.RED -> DeviceLightManualChannelId.RED
    DeviceLightLibraryChannel.GREEN -> DeviceLightManualChannelId.GREEN
    DeviceLightLibraryChannel.BLUE -> DeviceLightManualChannelId.BLUE
    DeviceLightLibraryChannel.WHITE -> DeviceLightManualChannelId.WHITE
}

@StringRes
private fun DeviceLightLibraryFailure.messageRes(): Int = when (this) {
    DeviceLightLibraryFailure.DUPLICATE_NAME ->
        R.string.device_light_library_name_duplicate_error
    DeviceLightLibraryFailure.INVALID_NAME ->
        R.string.device_light_library_name_invalid_error
    DeviceLightLibraryFailure.NOT_CONNECTED ->
        R.string.device_light_library_load_not_connected_error
    else -> R.string.device_light_library_operation_error
}
