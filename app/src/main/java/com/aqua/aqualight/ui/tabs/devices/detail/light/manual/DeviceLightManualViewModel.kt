package com.aqua.aqualight.ui.tabs.devices.detail.light.manual

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.presets.LightPresetDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.LightRuntimeRepository
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightScene
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.SavedLightPreset
import kotlinx.coroutines.Job
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

    private val lightRuntimeRepository =
        LightRuntimeRepository()

    private val _uiState = MutableStateFlow(
        ManualLightUiState()
    )

    val uiState: StateFlow<ManualLightUiState> =
        _uiState.asStateFlow()

    private val eventsChannel =
        Channel<ManualLightEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()

    private var deviceId: Long = 0L
    private var observeRuntimeJob: Job? = null

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId

        observeRuntimeJob?.cancel()

        observeRuntimeJob = viewModelScope.launch {
            lightRuntimeRepository.observeManualRuntime(deviceId).collect { runtime ->
                _uiState.update { current ->
                    recalculateOutput(
                        current.copy(
                            isManualMode = runtime.isManualMode,
                            isManualScene = runtime.isManualScene,
                            activeSceneName = runtime.activeSceneName,
                            activeSceneSource = if (runtime.isManualScene) {
                                current.activeSceneSource ?: "Manual Scene"
                            } else {
                                null
                            },
                            isPowerOn = runtime.isPowerOn,
                            red = runtime.red,
                            green = runtime.green,
                            blue = runtime.blue,
                            white = runtime.white
                        )
                    )
                }
            }
        }
    }

    fun setPowerOn(
        enabled: Boolean
    ) {
        viewModelScope.launch {
            if (!hasValidDeviceId()) return@launch

            val result = lightRuntimeRepository.setManualPower(
                deviceId = deviceId,
                isPowerOn = enabled
            )

            if (!result.isSuccess) {
                eventsChannel.send(
                    ManualLightEvent.ShowError(
                        result.message ?: "Power state could not be changed"
                    )
                )
            }
        }
    }

    fun updateRed(
        value: Int
    ) {
        val current = _uiState.value

        updateManualOutput(
            red = value,
            green = current.green,
            blue = current.blue,
            white = current.white
        )
    }

    fun updateGreen(
        value: Int
    ) {
        val current = _uiState.value

        updateManualOutput(
            red = current.red,
            green = value,
            blue = current.blue,
            white = current.white
        )
    }

    fun updateBlue(
        value: Int
    ) {
        val current = _uiState.value

        updateManualOutput(
            red = current.red,
            green = current.green,
            blue = value,
            white = current.white
        )
    }

    fun updateWhite(
        value: Int
    ) {
        val current = _uiState.value

        updateManualOutput(
            red = current.red,
            green = current.green,
            blue = current.blue,
            white = value
        )
    }

    private fun updateManualOutput(
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ) {
        viewModelScope.launch {
            if (!hasValidDeviceId()) return@launch

            val result = lightRuntimeRepository.updateManualOutput(
                deviceId = deviceId,
                red = red,
                green = green,
                blue = blue,
                white = white
            )

            if (!result.isSuccess) {
                eventsChannel.send(
                    ManualLightEvent.ShowError(
                        result.message ?: "Manual output could not be updated"
                    )
                )
            }
        }
    }

    fun applyScene(
        scene: ManualLightScene
    ) {
        val sceneName = scene.toDisplayName()

        viewModelScope.launch {
            if (!hasValidDeviceId()) return@launch

            val result = lightRuntimeRepository.applyManualScene(
                deviceId = deviceId,
                sceneName = sceneName,
                red = scene.red,
                green = scene.green,
                blue = scene.blue,
                white = scene.white
            )

            if (result.isSuccess) {
                _uiState.update { state ->
                    state.copy(
                        activeSceneSource = "Manual Scenes"
                    )
                }

                eventsChannel.send(
                    ManualLightEvent.ShowMessage("$sceneName applied")
                )
            } else {
                eventsChannel.send(
                    ManualLightEvent.ShowError(
                        result.message ?: "Scene could not be applied"
                    )
                )
            }
        }
    }

    fun applyOffScene() {
        setPowerOn(false)
    }

    fun resumeAuto() {
        viewModelScope.launch {
            if (!hasValidDeviceId()) return@launch

            val result = lightRuntimeRepository.resumeAuto(
                deviceId = deviceId
            )

            if (result.isSuccess) {
                eventsChannel.send(
                    ManualLightEvent.ShowMessage("Auto schedule resumed")
                )
            } else {
                eventsChannel.send(
                    ManualLightEvent.ShowError(
                        result.message ?: "Auto schedule could not be resumed"
                    )
                )
            }
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
            ).any { value ->
                value > 0
            }

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
            ).any { value ->
                value > 0
            }

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

    private suspend fun hasValidDeviceId(): Boolean {
        if (deviceId > 0L) {
            return true
        }

        eventsChannel.send(
            ManualLightEvent.ShowError("Device information is missing")
        )

        return false
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

        fun gammaCorrect(
            value: Double
        ): Int {
            val normalized = (value / max)
                .coerceIn(0.0, 1.0)

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

    private fun ManualLightScene.toDisplayName(): String {
        return when (this) {
            ManualLightScene.PLANT_GROWTH -> "Plant Growth"
            ManualLightScene.FISH_DISPLAY -> "Fish Display"
            ManualLightScene.SHRIMP_SAFE -> "Shrimp Safe"
            ManualLightScene.BLUE_ACCENT -> "Blue Accent"
            ManualLightScene.RED_ACCENT -> "Red Accent"
            ManualLightScene.FULL_SPECTRUM -> "Full Spectrum"
        }
    }

    override fun onCleared() {
        observeRuntimeJob?.cancel()
        super.onCleared()
    }
}