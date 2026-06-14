package com.aqua.aqualight.ui.tabs.devices.detail.light.manual

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.data.devices.light.math.LightOutputMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.color.LightRgbwChannels
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightControlMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightPreset
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightScene
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Data-ready manual light control state machine.
 *
 * The screen currently has no device data contract connected on purpose. The UI
 * still models the commercial behavior correctly:
 *
 * AUTO            -> sliders display the runtime output that the schedule/device
 *                    reports. The controls look passive, but touching them is
 *                    the explicit action that starts manual override.
 * MANUAL_OVERRIDE -> slider changes are previewed instantly and flushed through
 *                    a throttled low-latency command path.
 * SCENE_OVERRIDE  -> a quick scene is selected, highlighted, and sent as one
 *                    manual output command.
 *
 * When the real repository/service is added, replace the private device stub
 * methods at the bottom. The UI contract does not need to change.
 */
class DeviceLightManualViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        ManualLightUiState()
    )
    val uiState: StateFlow<ManualLightUiState> =
        _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ManualLightEvent>()
    val events: SharedFlow<ManualLightEvent> =
        _events.asSharedFlow()

    private var deviceId: Long = 0L

    private var controlMode: ManualLightControlMode = ManualLightControlMode.AUTO
    private var runtimeChannels: LightRgbwChannels = LightRgbwChannels(0, 0, 0, 0)
    private var manualDraftChannels: LightRgbwChannels = runtimeChannels
    private var selectedScene: ManualLightScene? = null
    private var savedPresets: List<ManualLightPreset> = emptyList()

    private var isSliderInteractionActive: Boolean = false
    private var pendingFlushJob: Job? = null
    private var lastCommandFlushAtMillis: Long = 0L
    private var lastCommandedChannels: LightRgbwChannels = runtimeChannels

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId

        if (deviceId <= 0L) {
            _uiState.value = buildState(
                controlsEnabled = false,
                connectionStatusText = LIGHT_DEVICE_INFORMATION_MISSING,
                outputHintText = LIGHT_DEVICE_INFORMATION_MISSING
            )
            return
        }

        refreshRuntimeSnapshot()
    }

    fun setPowerOn(
        enabled: Boolean
    ) {
        if (enabled) {
            activateManualOverride(
                channels = displayedChannels(),
                scene = null,
                immediateFlush = true
            )
        } else {
            resumeAuto()
        }
    }

    fun applyScene(
        scene: ManualLightScene
    ) {
        val channels = LightRgbwChannels(
            red = scene.red,
            green = scene.green,
            blue = scene.blue,
            white = scene.white
        ).sanitized()

        activateManualOverride(
            channels = channels,
            scene = scene,
            immediateFlush = true
        )
    }

    fun resumeAuto() {
        if (deviceId <= 0L) return

        pendingFlushJob?.cancel()
        pendingFlushJob = null
        selectedScene = null
        isSliderInteractionActive = false

        viewModelScope.launch {
            sendResumeAutoCommand()

            controlMode = ManualLightControlMode.AUTO
            runtimeChannels = readRuntimeChannelsFromDevice().sanitized()
            manualDraftChannels = runtimeChannels

            renderCurrentState()
        }
    }

    fun saveAs() {
        if (!_uiState.value.controlsEnabled) return

        viewModelScope.launch {
            _events.emit(
                ManualLightEvent.ShowSavePresetSheet
            )
        }
    }

    fun savePreset(
        name: String
    ) {
        val safeName = name.trim()
        if (safeName.isBlank()) return

        val channels = displayedChannels().sanitized()
        val preset = ManualLightPreset(
            id = "manual-${System.currentTimeMillis()}",
            name = safeName,
            red = channels.safeRed,
            green = channels.safeGreen,
            blue = channels.safeBlue,
            white = channels.safeWhite,
            createdAtMillis = System.currentTimeMillis()
        )

        savedPresets = savedPresets + preset
        renderCurrentState()

        viewModelScope.launch {
            _events.emit(
                ManualLightEvent.ShowMessage(
                    "Preset saved"
                )
            )
        }
    }

    fun previewRed(
        value: Int
    ) {
        updateManualChannel(
            red = value
        )
    }

    fun previewGreen(
        value: Int
    ) {
        updateManualChannel(
            green = value
        )
    }

    fun previewBlue(
        value: Int
    ) {
        updateManualChannel(
            blue = value
        )
    }

    fun previewWhite(
        value: Int
    ) {
        updateManualChannel(
            white = value
        )
    }

    fun beginSliderInteraction() {
        if (deviceId <= 0L) return

        isSliderInteractionActive = true
        if (controlMode == ManualLightControlMode.AUTO) {
            manualDraftChannels = runtimeChannels
        }
    }

    fun endSliderInteraction() {
        isSliderInteractionActive = false
        if (controlMode != ManualLightControlMode.AUTO) {
            queueManualOutputFlush(
                channels = manualDraftChannels,
                immediate = true
            )
        }
    }

    private fun refreshRuntimeSnapshot() {
        runtimeChannels = readRuntimeChannelsFromDevice().sanitized()

        if (controlMode == ManualLightControlMode.AUTO) {
            manualDraftChannels = runtimeChannels
            selectedScene = null
        }

        renderCurrentState()
    }

    private fun updateManualChannel(
        red: Int? = null,
        green: Int? = null,
        blue: Int? = null,
        white: Int? = null
    ) {
        if (deviceId <= 0L) return

        val base = if (controlMode == ManualLightControlMode.AUTO) {
            runtimeChannels
        } else {
            manualDraftChannels
        }

        val updatedChannels = LightRgbwChannels(
            red = red ?: base.safeRed,
            green = green ?: base.safeGreen,
            blue = blue ?: base.safeBlue,
            white = white ?: base.safeWhite
        ).sanitized()

        activateManualOverride(
            channels = updatedChannels,
            scene = null,
            immediateFlush = false
        )
    }

    private fun activateManualOverride(
        channels: LightRgbwChannels,
        scene: ManualLightScene?,
        immediateFlush: Boolean
    ) {
        manualDraftChannels = channels.sanitized()
        selectedScene = scene
        controlMode = if (scene == null) {
            ManualLightControlMode.MANUAL_OVERRIDE
        } else {
            ManualLightControlMode.SCENE_OVERRIDE
        }

        renderCurrentState()
        queueManualOutputFlush(
            channels = manualDraftChannels,
            immediate = immediateFlush
        )
    }

    private fun queueManualOutputFlush(
        channels: LightRgbwChannels,
        immediate: Boolean
    ) {
        if (deviceId <= 0L) return

        pendingFlushJob?.cancel()

        val now = SystemClock.uptimeMillis()
        val waitMillis = if (immediate || lastCommandFlushAtMillis <= 0L) {
            0L
        } else {
            (COMMAND_THROTTLE_MILLIS - (now - lastCommandFlushAtMillis))
                .coerceAtLeast(0L)
        }

        pendingFlushJob = viewModelScope.launch {
            if (waitMillis > 0L) {
                delay(waitMillis)
            }

            flushManualOutput(
                channels.sanitized()
            )
        }
    }

    private suspend fun flushManualOutput(
        channels: LightRgbwChannels
    ) {
        val safeChannels = channels.sanitized()

        sendManualOutputCommand(
            safeChannels
        )

        lastCommandedChannels = safeChannels
        lastCommandFlushAtMillis = SystemClock.uptimeMillis()
    }

    private fun renderCurrentState() {
        _uiState.value = buildState()
    }

    private fun buildState(
        controlsEnabled: Boolean = deviceId > 0L,
        connectionStatusText: String = modeSubtitle(),
        outputHintText: String = modeOutputHint()
    ): ManualLightUiState {
        val channels = displayedChannels().sanitized()
        val outputPercent = calculateOutputPercent(
            channels
        )
        val isManual = controlMode == ManualLightControlMode.MANUAL_OVERRIDE
        val isScene = controlMode == ManualLightControlMode.SCENE_OVERRIDE

        return ManualLightUiState(
            controlMode = controlMode,
            isManualMode = isManual,
            isManualScene = isScene,
            isPowerOn = controlMode != ManualLightControlMode.AUTO || outputPercent > 0,
            activeSceneName = selectedScene?.title,
            activeSceneSource = selectedScene?.name,
            masterOutputPercent = outputPercent,
            red = channels.safeRed,
            green = channels.safeGreen,
            blue = channels.safeBlue,
            white = channels.safeWhite,
            estimatedPowerWatts = 0.0,
            hasPowerCalibration = false,
            powerText = "$outputPercent%",
            savedPresets = savedPresets,
            isDeviceOnline = controlsEnabled,
            controlsEnabled = controlsEnabled,
            connectionStatusText = connectionStatusText,
            outputHintText = outputHintText
        )
    }

    private fun displayedChannels(): LightRgbwChannels {
        return if (controlMode == ManualLightControlMode.AUTO) {
            runtimeChannels
        } else {
            manualDraftChannels
        }
    }

    private fun calculateOutputPercent(
        channels: LightRgbwChannels
    ): Int {
        return LightOutputMath.outputPercent(
            red = channels.safeRed,
            green = channels.safeGreen,
            blue = channels.safeBlue,
            white = channels.safeWhite
        )
    }

    private fun modeSubtitle(): String {
        return when (controlMode) {
            ManualLightControlMode.AUTO -> {
                "Automatic schedule is running"
            }

            ManualLightControlMode.MANUAL_OVERRIDE -> {
                "Live RGBW override active"
            }

            ManualLightControlMode.SCENE_OVERRIDE -> {
                selectedScene?.title ?: "Quick scene override active"
            }
        }
    }

    private fun modeOutputHint(): String {
        return when (controlMode) {
            ManualLightControlMode.AUTO -> {
                "Auto output preview · drag any slider to override"
            }

            ManualLightControlMode.MANUAL_OVERRIDE -> {
                if (isSliderInteractionActive) {
                    "Live override · sending while you adjust"
                } else {
                    "Manual override · sliders are controlling output"
                }
            }

            ManualLightControlMode.SCENE_OVERRIDE -> {
                "Scene output · drag a slider to customize"
            }
        }
    }

    private fun LightRgbwChannels.sanitized(): LightRgbwChannels {
        return LightRgbwChannels(
            red = safeRed,
            green = safeGreen,
            blue = safeBlue,
            white = safeWhite
        )
    }

    /**
     * TODO(data): Replace with the real light runtime stream/repository. This
     * must return the current AUTO/schedule output, not the last manual draft.
     */
    private fun readRuntimeChannelsFromDevice(): LightRgbwChannels {
        return runtimeChannels
    }

    /**
     * TODO(data): Replace with the ESP/light command call. Keep this method fast;
     * the ViewModel already throttles slider movement and sends a final flush on
     * touch release.
     */
    private suspend fun sendManualOutputCommand(
        channels: LightRgbwChannels
    ) {
        // Intentionally no-op until the light command/data layer is connected.
    }

    /**
     * TODO(data): Replace with the command that disables manual override and
     * resumes the active program/schedule on the device.
     */
    private suspend fun sendResumeAutoCommand() {
        // Intentionally no-op until the light command/data layer is connected.
    }

    companion object {
        private const val COMMAND_THROTTLE_MILLIS = 75L
    }
}
