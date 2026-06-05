package com.aqua.aqualight.ui.tabs.devices.detail.light.presets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.presets.LightPresetDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.LightRuntimeRepository
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.DeviceLightPresetsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightDeviceCommandManager

class DeviceLightPresetsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val lightPresetDataStoreManager =
        LightPresetDataStoreManager(application.applicationContext)

    private val lightRuntimeRepository =
    LightRuntimeRepository(
        commandManager = Esp32LightDeviceCommandManager(
            context = application.applicationContext
        )
    )

    val presetsFlow =
        lightPresetDataStoreManager.presetsFlow

    private val eventsChannel =
        Channel<DeviceLightPresetsEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()

    private var deviceId: Long = 0L

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId
    }

    fun currentManualRuntime() =
        lightRuntimeRepository.currentManualRuntime(deviceId)

    fun applyPresetToDevice(
        preset: LightPresetItem
    ) {
        viewModelScope.launch {
            if (deviceId <= 0L) {
                eventsChannel.send(
                    DeviceLightPresetsEvent.ShowError("Device information is missing")
                )
                return@launch
            }

            val result = lightRuntimeRepository.applyManualScene(
                deviceId = deviceId,
                sceneName = preset.title,
                red = preset.red,
                green = preset.green,
                blue = preset.blue,
                white = preset.white
            )

            if (result.isSuccess) {
                eventsChannel.send(
                    DeviceLightPresetsEvent.ShowMessage("${preset.title} applied")
                )
                eventsChannel.send(
                    DeviceLightPresetsEvent.NavigateToManualControl
                )
            } else {
                eventsChannel.send(
                    DeviceLightPresetsEvent.ShowError(
                        result.message ?: "Preset could not be applied"
                    )
                )
            }
        }
    }

    fun deletePreset(
        preset: LightPresetItem
    ) {
        if (!preset.isCustom) return

        viewModelScope.launch {
            runCatching {
                lightPresetDataStoreManager.deletePreset(preset.id)
            }.onSuccess {
                eventsChannel.send(
                    DeviceLightPresetsEvent.ShowMessage("${preset.title} deleted")
                )
            }.onFailure {
                eventsChannel.send(
                    DeviceLightPresetsEvent.ShowError("Preset could not be deleted")
                )
            }
        }
    }
}