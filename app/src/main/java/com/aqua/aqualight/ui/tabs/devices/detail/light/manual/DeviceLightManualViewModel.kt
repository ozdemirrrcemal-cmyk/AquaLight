package com.aqua.aqualight.ui.tabs.devices.detail.light.manual

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.presets.LightPresetDataStoreManager
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightScene
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.SavedLightPreset
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

class DeviceLightManualViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val lightPresetDataStoreManager =
        LightPresetDataStoreManager(application.applicationContext)

    private val _uiState = MutableStateFlow(
        ManualLightUiState()
    )

    val uiState: StateFlow<ManualLightUiState> =
        _uiState.asStateFlow()

    private val eventsChannel =
        Channel<ManualLightEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()

    fun setPowerOn(
        enabled: Boolean
    ) {
        _uiState.update { state ->
            val newState = if (enabled) {
                state.copy(
                    isPowerOn = true,
                    isManualMode = true
                )
            } else {
                state.copy(
                    isPowerOn = false,
                    isManualMode = true,
                    masterOutputPercent = 0,
                    red = 0,
                    green = 0,
                    blue = 0,
                    white = 0
                )
            }

            recalculateOutput(newState)
        }
    }

    fun updateMasterOutput(
        percent: Int
    ) {
        val safePercent = percent.coerceIn(0, 100)

        _uiState.update { state ->
            recalculateOutput(
                state.copy(
                    isPowerOn = safePercent > 0,
                    isManualMode = true,
                    masterOutputPercent = safePercent
                )
            )
        }
    }

    fun updateRed(value: Int) {
        updateChannel(red = value)
    }

    fun updateGreen(value: Int) {
        updateChannel(green = value)
    }

    fun updateBlue(value: Int) {
        updateChannel(blue = value)
    }

    fun updateWhite(value: Int) {
        updateChannel(white = value)
    }

    fun applyScene(
        scene: ManualLightScene
    ) {
        val master = listOf(
            scene.red,
            scene.green,
            scene.blue,
            scene.white
        ).maxOrNull() ?: 0

        _uiState.update { state ->
            recalculateOutput(
                state.copy(
                    isManualMode = true,
                    isPowerOn = master > 0,
                    masterOutputPercent = master,
                    red = scene.red,
                    green = scene.green,
                    blue = scene.blue,
                    white = scene.white
                )
            )
        }
    }

    fun applyOffScene() {
        setPowerOn(false)
    }

    fun resumeAuto() {
        viewModelScope.launch {
            // TODO: Send manualMode = false to ESP32.

            _uiState.update {
                it.copy(
                    isManualMode = false
                )
            }

            eventsChannel.send(
                ManualLightEvent.ShowMessage("Auto schedule resumed")
            )
        }
    }

    fun saveAs() {
        viewModelScope.launch {
            val state = _uiState.value

            val hasAnyOutput = listOf(
                state.red,
                state.green,
                state.blue,
                state.white
            ).any { it > 0 }

            if (!hasAnyOutput) {
                eventsChannel.send(
                    ManualLightEvent.ShowError("Set at least one channel before saving")
                )
                return@launch
            }

            eventsChannel.send(
                ManualLightEvent.ShowSavePresetSheet
            )
        }
    }

    fun savePreset(
        name: String
    ) {
        viewModelScope.launch {
            val cleanName = name.trim()

            if (cleanName.isBlank()) {
                eventsChannel.send(
                    ManualLightEvent.ShowError("Preset name is required")
                )
                return@launch
            }

            val state = _uiState.value

            val hasAnyOutput = listOf(
                state.red,
                state.green,
                state.blue,
                state.white
            ).any { it > 0 }

            if (!hasAnyOutput) {
                eventsChannel.send(
                    ManualLightEvent.ShowError("Set at least one channel before saving")
                )
                return@launch
            }

            val now = System.currentTimeMillis()

            val preset = SavedLightPreset(
                id = "manual_$now",
                name = cleanName,
                red = state.red,
                green = state.green,
                blue = state.blue,
                white = state.white,
                createdAt = now,
                updatedAt = now
            )

            runCatching {
                lightPresetDataStoreManager.savePreset(preset)
            }.onSuccess {
                eventsChannel.send(
                    ManualLightEvent.ShowMessage("Preset saved")
                )
            }.onFailure {
                eventsChannel.send(
                    ManualLightEvent.ShowError("Preset could not be saved")
                )
            }
        }
    }

    private fun updateChannel(
        red: Int? = null,
        green: Int? = null,
        blue: Int? = null,
        white: Int? = null
    ) {
        _uiState.update { state ->
            val newRed = red?.coerceIn(0, 100) ?: state.red
            val newGreen = green?.coerceIn(0, 100) ?: state.green
            val newBlue = blue?.coerceIn(0, 100) ?: state.blue
            val newWhite = white?.coerceIn(0, 100) ?: state.white

            val master = listOf(
                newRed,
                newGreen,
                newBlue,
                newWhite
            ).maxOrNull() ?: 0

            recalculateOutput(
                state.copy(
                    isManualMode = true,
                    isPowerOn = master > 0,
                    masterOutputPercent = master,
                    red = newRed,
                    green = newGreen,
                    blue = newBlue,
                    white = newWhite
                )
            )
        }
    }

    private fun recalculateOutput(
        state: ManualLightUiState
    ): ManualLightUiState {
        val estimatedPower = if (state.isPowerOn) {
            state.redMaxWatts * (state.red / 100.0) +
                state.greenMaxWatts * (state.green / 100.0) +
                state.blueMaxWatts * (state.blue / 100.0) +
                state.whiteMaxWatts * (state.white / 100.0)
        } else {
            0.0
        }

        val maxPower =
            state.redMaxWatts +
                state.greenMaxWatts +
                state.blueMaxWatts +
                state.whiteMaxWatts

        val hasPowerCalibration = maxPower > 0.0

        val fallbackOutputPercent = listOf(
            state.red,
            state.green,
            state.blue,
            state.white
        ).maxOrNull() ?: 0

        val masterOutputPercent = when {
            !state.isPowerOn -> 0

            hasPowerCalibration -> {
                ((estimatedPower / maxPower) * 100.0)
                    .roundToInt()
                    .coerceIn(0, 100)
            }

            else -> fallbackOutputPercent
        }

        val previewColor = calculatePreviewColor(
            red = state.red,
            green = state.green,
            blue = state.blue,
            white = state.white
        )

        return state.copy(
            estimatedPowerWatts = estimatedPower.roundToOneDecimal(),
            hasPowerCalibration = hasPowerCalibration,
            masterOutputPercent = masterOutputPercent,
            previewRed = previewColor.first,
            previewGreen = previewColor.second,
            previewBlue = previewColor.third
        )
    }

    private fun calculatePreviewColor(
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ): Triple<Int, Int, Int> {
        val r = red.coerceIn(0, 100) / 100.0
        val g = green.coerceIn(0, 100) / 100.0
        val b = blue.coerceIn(0, 100) / 100.0
        val w = white.coerceIn(0, 100) / 100.0

        val redColor = Triple(1.00, 0.08, 0.03)
        val greenColor = Triple(0.12, 1.00, 0.20)
        val blueColor = Triple(0.05, 0.28, 1.00)
        val whiteColor = Triple(0.92, 0.96, 1.00)

        val linearRed =
            redColor.first * r +
                greenColor.first * g +
                blueColor.first * b +
                whiteColor.first * w

        val linearGreen =
            redColor.second * r +
                greenColor.second * g +
                blueColor.second * b +
                whiteColor.second * w

        val linearBlue =
            redColor.third * r +
                greenColor.third * g +
                blueColor.third * b +
                whiteColor.third * w

        val max = maxOf(
            linearRed,
            linearGreen,
            linearBlue,
            1.0
        )

        fun gammaCorrect(value: Double): Int {
            val normalized = (value / max).coerceIn(0.0, 1.0)

            return (255.0 * normalized.pow(1.0 / 2.2))
                .roundToInt()
                .coerceIn(0, 255)
        }

        return Triple(
            gammaCorrect(linearRed),
            gammaCorrect(linearGreen),
            gammaCorrect(linearBlue)
        )
    }

    private fun Double.roundToOneDecimal(): Double {
        return (this * 10.0).roundToInt() / 10.0
    }
}