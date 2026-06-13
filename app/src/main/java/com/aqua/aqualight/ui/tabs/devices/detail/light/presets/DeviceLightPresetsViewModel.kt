package com.aqua.aqualight.ui.tabs.devices.detail.light.presets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightViewModel.Companion.LIGHT_DATA_LAYER_NOT_CONNECTED
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.presets.model.SavedLightPreset
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.DeviceLightPresetsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Temporary UI shell for presets.
 *
 * The previous preset persistence and device apply command are not connected.
 * Custom presets are intentionally empty until the new Light data contract is defined.
 */
class DeviceLightPresetsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _presetsFlow = MutableStateFlow<List<SavedLightPreset>>(
        emptyList()
    )
    val presetsFlow: StateFlow<List<SavedLightPreset>> =
        _presetsFlow.asStateFlow()

    private val _events = MutableSharedFlow<DeviceLightPresetsEvent>()
    val events: SharedFlow<DeviceLightPresetsEvent> =
        _events.asSharedFlow()

    private var deviceId: Long = 0L

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId
        _presetsFlow.value = emptyList()
    }

    fun currentManualRuntime(): LightManualRuntimeSnapshot {
        return LightManualRuntimeSnapshot(
            deviceId = deviceId
        )
    }

    fun applyPresetToDevice(
        preset: LightPresetItem
    ) {
        emitUnavailable()
    }

    fun deletePreset(
        preset: LightPresetItem
    ) {
        emitUnavailable()
    }

    private fun emitUnavailable() {
        viewModelScope.launch {
            _events.emit(
                DeviceLightPresetsEvent.ShowError(
                    LIGHT_DATA_LAYER_NOT_CONNECTED
                )
            )
        }
    }
}

data class LightManualRuntimeSnapshot(
    val deviceId: Long,
    val isManualScene: Boolean = false,
    val activeSceneName: String? = null,
    val activeSceneSource: String? = null,
    val red: Int = 0,
    val green: Int = 0,
    val blue: Int = 0,
    val white: Int = 0
)
