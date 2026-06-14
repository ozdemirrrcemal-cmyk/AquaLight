package com.aqua.aqualight.ui.tabs.devices.detail.light.manual

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DATA_LAYER_NOT_CONNECTED
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightScene
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Temporary UI shell for manual light control.
 *
 * Manual controls expose a UI-only unavailable state until the Light command
 * contract is designed and connected intentionally.
 */
class DeviceLightManualViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        unavailableState()
    )
    val uiState: StateFlow<ManualLightUiState> =
        _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ManualLightEvent>()
    val events: SharedFlow<ManualLightEvent> =
        _events.asSharedFlow()

    private var deviceId: Long = 0L

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId
        _uiState.value = unavailableState(
            reason = if (deviceId <= 0L) {
                LIGHT_DEVICE_INFORMATION_MISSING
            } else {
                LIGHT_DATA_LAYER_NOT_CONNECTED
            }
        )
    }

    fun setPowerOn(
        enabled: Boolean
    ) {
        emitUnavailable()
    }

    fun applyScene(
        scene: ManualLightScene
    ) {
        emitUnavailable()
    }

    fun resumeAuto() {
        emitUnavailable()
    }

    fun saveAs() {
        emitUnavailable()
    }

    fun savePreset(
        name: String
    ) {
        emitUnavailable()
    }

    fun previewRed(
        value: Int
    ) {
        emitUnavailable()
    }

    fun previewGreen(
        value: Int
    ) {
        emitUnavailable()
    }

    fun previewBlue(
        value: Int
    ) {
        emitUnavailable()
    }

    fun previewWhite(
        value: Int
    ) {
        emitUnavailable()
    }

    fun beginSliderInteraction() = Unit

    fun endSliderInteraction() = Unit

    private fun unavailableState(
        reason: String = LIGHT_DATA_LAYER_NOT_CONNECTED
    ): ManualLightUiState {
        return ManualLightUiState(
            isManualMode = false,
            isManualScene = false,
            isPowerOn = false,
            masterOutputPercent = 0,
            red = 0,
            green = 0,
            blue = 0,
            white = 0,
            savedPresets = emptyList(),
            isDeviceOnline = false,
            controlsEnabled = false,
            connectionStatusText = reason
        )
    }

    private fun emitUnavailable() {
        viewModelScope.launch {
            _events.emit(
                ManualLightEvent.ShowError(
                    LIGHT_DATA_LAYER_NOT_CONNECTED
                )
            )
        }
    }
}
