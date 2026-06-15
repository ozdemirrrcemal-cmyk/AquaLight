package com.aqua.aqualight.ui.tabs.devices.detail.light.presets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.light.presets.LightPresetDataStoreManager
import com.aqua.aqualight.data.devices.runtime.light.LightLocalOverrideStore
import com.aqua.aqualight.data.devices.runtime.light.LightLocalOverrideType
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeDeviceAccessor
import com.aqua.aqualight.data.devices.light.presets.model.SavedLightPreset
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.DeviceLightPresetsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Data-connected Presets & Scenes view model.
 *
 * Built-in presets stay in the UI catalog. User presets are stored locally in
 * Proto DataStore. Applying either built-in or custom preset sends a scene
 * output through the shared Light runtime gateway. Legacy firmware receives the
 * scene as a normal manual RGBW command; newer firmware can replace the API
 * adapter without changing this screen.
 */
class DeviceLightPresetsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val presetStore = LightPresetDataStoreManager.create(
        context = appContext
    )

    private val runtimeAccessor = LightRuntimeDeviceAccessor(
        context = appContext
    )

    private val _presetsFlow = MutableStateFlow<List<SavedLightPreset>>(
        emptyList()
    )
    val presetsFlow: StateFlow<List<SavedLightPreset>> =
        _presetsFlow.asStateFlow()

    private val _events = MutableSharedFlow<DeviceLightPresetsEvent>()
    val events: SharedFlow<DeviceLightPresetsEvent> =
        _events.asSharedFlow()

    private var deviceId: Long = 0L
    private var presetCollectorJob: Job? = null

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId
        presetCollectorJob?.cancel()

        if (deviceId <= 0L) {
            _presetsFlow.value = emptyList()
            return
        }

        presetCollectorJob = viewModelScope.launch {
            presetStore.presetsForDeviceFlow(deviceId).collect { presets ->
                _presetsFlow.value = presets
            }
        }
    }

    fun currentManualRuntime(): LightManualRuntimeSnapshot {
        val override = LightLocalOverrideStore.current(
            deviceId = deviceId
        ) ?: return LightManualRuntimeSnapshot(
            deviceId = deviceId
        )

        if (override.type != LightLocalOverrideType.SCENE) {
            return LightManualRuntimeSnapshot(
                deviceId = deviceId
            )
        }

        val channels = override.channels.normalized()
        return LightManualRuntimeSnapshot(
            deviceId = deviceId,
            isManualScene = true,
            activeSceneName = override.sceneName,
            activeSceneSource = override.sceneSource,
            red = channels.red,
            green = channels.green,
            blue = channels.blue,
            white = channels.white
        )
    }

    fun applyPresetToDevice(
        preset: LightPresetItem
    ) {
        if (deviceId <= 0L) return

        viewModelScope.launch {
            _events.emit(
                DeviceLightPresetsEvent.SetLoading(true)
            )

            val result = runtimeAccessor.setSceneOutput(
                deviceId = deviceId,
                channelValues = preset.toLightChannelValues(),
                sceneName = preset.title,
                sceneSource = preset.sceneSourceKey()
            )

            when (result) {
                is ApiResult.Success -> {
                    _events.emit(
                        DeviceLightPresetsEvent.ShowMessage(
                            "Preset applied"
                        )
                    )
                }

                is ApiResult.Error -> {
                    _events.emit(
                        DeviceLightPresetsEvent.ShowError(
                            result.error.message
                        )
                    )
                }
            }

            _events.emit(
                DeviceLightPresetsEvent.SetLoading(false)
            )
        }
    }

    fun deletePreset(
        preset: LightPresetItem
    ) {
        if (deviceId <= 0L || !preset.isCustom) return

        viewModelScope.launch {
            runCatching {
                presetStore.deletePreset(
                    presetId = preset.id,
                    deviceId = deviceId
                )
            }.onSuccess { deleted ->
                _events.emit(
                    if (deleted) {
                        DeviceLightPresetsEvent.ShowMessage(
                            "Preset deleted"
                        )
                    } else {
                        DeviceLightPresetsEvent.ShowError(
                            "Preset could not be found"
                        )
                    }
                )
            }.onFailure { exception ->
                _events.emit(
                    DeviceLightPresetsEvent.ShowError(
                        exception.message ?: "Preset could not be deleted"
                    )
                )
            }
        }
    }

    private fun LightPresetItem.toLightChannelValues(): LightChannelValues {
        return LightChannelValues(
            red = red.coerceIn(0, 100),
            green = green.coerceIn(0, 100),
            blue = blue.coerceIn(0, 100),
            white = white.coerceIn(0, 100)
        ).normalized()
    }

    private fun LightPresetItem.sceneSourceKey(): String {
        return if (isCustom) {
            "custom:$id"
        } else {
            id
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
