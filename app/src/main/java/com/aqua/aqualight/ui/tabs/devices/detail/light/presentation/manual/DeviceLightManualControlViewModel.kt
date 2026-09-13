package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightManualControlViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightManualControlUiState())
    internal val uiState: StateFlow<DeviceLightManualControlUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DeviceLightManualControlEffect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    internal val effects: SharedFlow<DeviceLightManualControlEffect> = _effects.asSharedFlow()

    private var boundDeviceUid = ""

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Manual light destination deviceUid must not be blank." }
        if (boundDeviceUid == deviceUid) return
        boundDeviceUid = deviceUid
        _uiState.value = deviceLightManualPreviewState(deviceUid)
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
        emitEffect(DeviceLightManualControlEffect.OpenLibrary)
    }

    private fun emitEffect(effect: DeviceLightManualControlEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}

internal sealed interface DeviceLightManualControlEffect {
    data object OpenLibrary : DeviceLightManualControlEffect

    data class ShowError(
        @StringRes val messageRes: Int
    ) : DeviceLightManualControlEffect
}
