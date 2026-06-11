package com.aqua.aqualight.ui.tabs.devices.detail.light.presets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.presets.LightPresetDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightDeviceCommandManager
import com.aqua.aqualight.data.devices.light.runtime.LightRuntimeRepository
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.DeviceLightPresetsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

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

    val events =
        eventsChannel.receiveAsFlow()

    private var deviceId: Long = 0L
    private var isOperationInProgress = false

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
                    DeviceLightPresetsEvent.ShowError(
                        "Device information is missing"
                    )
                )
                return@launch
            }

            if (!beginOperation()) {
                return@launch
            }

            var successMessage: String? = null
            var errorMessage: String? = null
            var shouldNavigateToManual = false

            try {
                val result = lightRuntimeRepository.applyManualScene(
                    deviceId = deviceId,
                    sceneName = preset.title,
                    red = preset.red,
                    green = preset.green,
                    blue = preset.blue,
                    white = preset.white
                )

                if (result.isSuccess) {
                    successMessage = "${preset.title} applied"
                    shouldNavigateToManual = true
                } else {
                    errorMessage =
                        result.message ?: "Preset could not be applied"
                }
            } catch (error: Exception) {
                errorMessage =
                    error.message ?: "Preset could not be applied"
            } finally {
                finishOperation()
            }

            successMessage?.let { message ->
                eventsChannel.send(
                    DeviceLightPresetsEvent.ShowMessage(message)
                )
            }

            errorMessage?.let { message ->
                eventsChannel.send(
                    DeviceLightPresetsEvent.ShowError(message)
                )
            }

            if (shouldNavigateToManual) {
                eventsChannel.send(
                    DeviceLightPresetsEvent.NavigateToManualControl
                )
            }
        }
    }

    fun deletePreset(
        preset: LightPresetItem
    ) {
        if (!preset.isCustom) {
            return
        }

        viewModelScope.launch {
            if (!beginOperation()) {
                return@launch
            }

            var successMessage: String? = null
            var errorMessage: String? = null

            try {
                lightPresetDataStoreManager.deletePreset(preset.id)
                successMessage = "${preset.title} deleted"
            } catch (error: Exception) {
                errorMessage =
                    error.message ?: "Preset could not be deleted"
            } finally {
                finishOperation()
            }

            successMessage?.let { message ->
                eventsChannel.send(
                    DeviceLightPresetsEvent.ShowMessage(message)
                )
            }

            errorMessage?.let { message ->
                eventsChannel.send(
                    DeviceLightPresetsEvent.ShowError(message)
                )
            }
        }
    }

    private suspend fun beginOperation(): Boolean {
        if (isOperationInProgress) {
            return false
        }

        isOperationInProgress = true

        eventsChannel.send(
            DeviceLightPresetsEvent.SetLoading(true)
        )

        return true
    }

    private suspend fun finishOperation() {
        if (!isOperationInProgress) {
            return
        }

        eventsChannel.send(
            DeviceLightPresetsEvent.SetLoading(false)
        )

        isOperationInProgress = false
    }
}