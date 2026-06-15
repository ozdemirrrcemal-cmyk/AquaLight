package com.aqua.aqualight.ui.tabs.devices.detail.light.manual

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.light.LightMode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.light.math.LightOutputMath
import com.aqua.aqualight.data.devices.light.presets.LightPresetDataStoreManager
import com.aqua.aqualight.data.devices.light.math.LightRgbwPowerCalibration
import com.aqua.aqualight.data.devices.runtime.light.LightLocalOverrideStore
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeDeviceAccessor
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.color.LightRgbwChannels
import com.aqua.aqualight.data.devices.light.presets.model.SavedLightPreset
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Manual light control state machine connected to the shared Light runtime
 * gateway.
 *
 * AUTO            -> sliders display the runtime output that the schedule/device
 *                    reports. Touching a slider starts manual override.
 * MANUAL_OVERRIDE -> slider changes are previewed instantly and flushed through
 *                    a throttled low-latency command path.
 * SCENE_OVERRIDE  -> legacy firmware receives the scene as a normal manual
 *                    RGBW output command. Future firmware can expose a real
 *                    scene mode without changing this screen.
 *
 * Center ring/text is power, not output. Power is calculated only from firmware
 * channel watt values. If runtime watt calibration is unavailable, the screen
 * shows "-- W" instead of a fake catalog/fallback value.
 */
class DeviceLightManualViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val runtimeAccessor = LightRuntimeDeviceAccessor(
        context = appContext
    )

    private val presetStore = LightPresetDataStoreManager.create(
        context = appContext
    )

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
    private var powerCalibration: LightRgbwPowerCalibration? = null
    private var selectedScene: ManualLightScene? = null
    private var savedPresets: List<ManualLightPreset> = emptyList()
    private var savedPresetCollectorJob: Job? = null

    private var isSliderInteractionActive: Boolean = false
    private var manualCommandWorkerJob: Job? = null
    private var pendingManualChannels: LightRgbwChannels? = null
    private var lastSubmittedManualChannels: LightRgbwChannels? = null
    private var forceNextManualSend: Boolean = false
    private var lastCommandFlushAtMillis: Long = 0L

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

        _uiState.value = buildState(
            controlsEnabled = false,
            connectionStatusText = "Connecting to light controller",
            outputHintText = "Reading live RGBW output"
        )

        observeSavedPresets()

        refreshRuntimeSnapshot(
            showLoading = true,
            forceAutoMode = false
        )
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

        manualCommandWorkerJob?.cancel()
        manualCommandWorkerJob = null
        pendingManualChannels = null
        lastSubmittedManualChannels = null
        forceNextManualSend = false
        selectedScene = null
        isSliderInteractionActive = false

        viewModelScope.launch {
            _events.emit(
                ManualLightEvent.SetLoading(true)
            )

            when (val result = sendResumeAutoCommand()) {
                is ApiResult.Success -> {
                    controlMode = ManualLightControlMode.AUTO
                    val refreshed = refreshRuntimeSnapshotNow(
                        showLoading = false,
                        forceAutoMode = true
                    )
                    if (refreshed) {
                        _events.emit(
                            ManualLightEvent.ShowMessage(
                                "Auto schedule resumed"
                            )
                        )
                    }
                }

                is ApiResult.Error -> {
                    _events.emit(
                        ManualLightEvent.ShowError(
                            result.error.message
                        )
                    )
                    renderCurrentState()
                }
            }

            _events.emit(
                ManualLightEvent.SetLoading(false)
            )
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
        if (safeName.isBlank() || deviceId <= 0L) return

        val channels = displayedChannels().sanitized()

        viewModelScope.launch {
            runCatching {
                presetStore.savePreset(
                    deviceId = deviceId,
                    name = safeName,
                    channels = channels
                )
            }.onSuccess {
                _events.emit(
                    ManualLightEvent.ShowMessage(
                        "Preset saved"
                    )
                )
            }.onFailure { exception ->
                _events.emit(
                    ManualLightEvent.ShowError(
                        exception.message ?: "Preset could not be saved"
                    )
                )
            }
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
        if (deviceId <= 0L || !_uiState.value.controlsEnabled) return

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

    private fun observeSavedPresets() {
        savedPresetCollectorJob?.cancel()
        savedPresetCollectorJob = viewModelScope.launch {
            presetStore.presetsForDeviceFlow(deviceId).collect { presets ->
                savedPresets = presets.map { preset ->
                    preset.toManualLightPreset()
                }
                _uiState.value = _uiState.value.copy(
                    savedPresets = savedPresets
                )
            }
        }
    }

    private fun refreshRuntimeSnapshot(
        showLoading: Boolean,
        forceAutoMode: Boolean
    ) {
        viewModelScope.launch {
            refreshRuntimeSnapshotNow(
                showLoading = showLoading,
                forceAutoMode = forceAutoMode
            )
        }
    }

    private suspend fun refreshRuntimeSnapshotNow(
        showLoading: Boolean,
        forceAutoMode: Boolean
    ): Boolean {
        if (showLoading) {
            _events.emit(
                ManualLightEvent.SetLoading(true)
            )
        }

        val success = when (val snapshot = runtimeAccessor.readSnapshot(deviceId)) {
            is ApiResult.Success -> {
                applyRuntimeSnapshot(
                    snapshot = snapshot.value,
                    forceAutoMode = forceAutoMode
                )
                renderCurrentState()
                true
            }

            is ApiResult.Error -> {
                _uiState.value = buildState(
                    controlsEnabled = false,
                    connectionStatusText = "Light controller is not reachable",
                    outputHintText = snapshot.error.message
                )
                _events.emit(
                    ManualLightEvent.ShowError(
                        snapshot.error.message
                    )
                )
                false
            }
        }

        if (showLoading) {
            _events.emit(
                ManualLightEvent.SetLoading(false)
            )
        }

        return success
    }

    private fun applyRuntimeSnapshot(
        snapshot: LightRuntimeSnapshot,
        forceAutoMode: Boolean
    ) {
        runtimeChannels = snapshot.channels.toManualChannels().sanitized()
        powerCalibration = snapshot.powerCalibration

        controlMode = if (forceAutoMode) {
            ManualLightControlMode.AUTO
        } else {
            when (snapshot.mode) {
                LightMode.MANUAL -> ManualLightControlMode.MANUAL_OVERRIDE
                LightMode.SCENE -> ManualLightControlMode.SCENE_OVERRIDE
                else -> ManualLightControlMode.AUTO
            }
        }

        selectedScene = if (controlMode == ManualLightControlMode.SCENE_OVERRIDE) {
            snapshot.toManualScene()
        } else {
            null
        }

        manualDraftChannels = runtimeChannels
    }

    private fun updateManualChannel(
        red: Int? = null,
        green: Int? = null,
        blue: Int? = null,
        white: Int? = null
    ) {
        if (deviceId <= 0L || !_uiState.value.controlsEnabled) return

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
        if (!_uiState.value.controlsEnabled) return

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

        pendingManualChannels = channels.sanitized()
        forceNextManualSend = forceNextManualSend || immediate

        if (manualCommandWorkerJob?.isActive == true) {
            return
        }

        manualCommandWorkerJob = viewModelScope.launch {
            runManualCommandLoop()
        }
    }

    private suspend fun runManualCommandLoop() {
        while (true) {
            val targetChannels = pendingManualChannels?.sanitized() ?: break
            val shouldForceSend = forceNextManualSend

            if (!shouldForceSend && targetChannels == lastSubmittedManualChannels) {
                break
            }

            val waitMillis = manualCommandDelayMillis(
                forceSend = shouldForceSend
            )
            forceNextManualSend = false

            if (waitMillis > 0L) {
                delay(waitMillis)
            }

            val latestChannels = pendingManualChannels?.sanitized() ?: break
            if (!shouldForceSend && latestChannels == lastSubmittedManualChannels) {
                break
            }

            flushManualOutput(
                latestChannels
            )

            if (pendingManualChannels?.sanitized() == latestChannels && !forceNextManualSend) {
                break
            }
        }
    }

    private fun manualCommandDelayMillis(
        forceSend: Boolean
    ): Long {
        if (forceSend || lastCommandFlushAtMillis <= 0L) {
            return 0L
        }

        val now = SystemClock.uptimeMillis()
        return (COMMAND_THROTTLE_MILLIS - (now - lastCommandFlushAtMillis))
            .coerceAtLeast(0L)
    }

    private suspend fun flushManualOutput(
        channels: LightRgbwChannels
    ) {
        val safeChannels = channels.sanitized()
        lastSubmittedManualChannels = safeChannels

        when (val result = sendManualOutputCommand(safeChannels)) {
            is ApiResult.Success -> {
                recordSceneOverrideIfNeeded(safeChannels)
                lastCommandFlushAtMillis = SystemClock.uptimeMillis()
            }

            is ApiResult.Error -> {
                lastSubmittedManualChannels = null
                _events.emit(
                    ManualLightEvent.ShowError(
                        result.error.message
                    )
                )
            }
        }
    }

    private fun recordSceneOverrideIfNeeded(
        channels: LightRgbwChannels
    ) {
        val scene = selectedScene ?: return
        if (controlMode != ManualLightControlMode.SCENE_OVERRIDE) return

        LightLocalOverrideStore.recordScene(
            deviceId = deviceId,
            sceneName = scene.title,
            sceneSource = scene.name,
            channels = channels.toApiChannelValues()
        )
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
        val currentPowerWatts = calculateCurrentPowerWatts(
            channels
        )
        val maxPowerWatts = powerCalibration?.maxWatt
        val powerLoadPercent = powerCalibration?.powerLoadPercent(
            redPercent = channels.safeRed,
            greenPercent = channels.safeGreen,
            bluePercent = channels.safeBlue,
            whitePercent = channels.safeWhite
        )?.takeIf {
            currentPowerWatts != null && maxPowerWatts != null
        } ?: 0
        val isManual = controlMode == ManualLightControlMode.MANUAL_OVERRIDE
        val isScene = controlMode == ManualLightControlMode.SCENE_OVERRIDE

        return ManualLightUiState(
            controlMode = controlMode,
            isManualMode = isManual,
            isManualScene = isScene,
            isPowerOn = controlMode != ManualLightControlMode.AUTO || outputPercent > 0,
            activeSceneName = selectedScene?.title,
            activeSceneSource = selectedScene?.name,
            powerLoadPercent = powerLoadPercent,
            currentPowerWatts = currentPowerWatts,
            maxPowerWatts = maxPowerWatts,
            powerText = formatPowerText(currentPowerWatts),
            red = channels.safeRed,
            green = channels.safeGreen,
            blue = channels.safeBlue,
            white = channels.safeWhite,
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

    private fun calculateCurrentPowerWatts(
        channels: LightRgbwChannels
    ): Double? {
        return powerCalibration?.currentWatt(
            redPercent = channels.safeRed,
            greenPercent = channels.safeGreen,
            bluePercent = channels.safeBlue,
            whitePercent = channels.safeWhite
        )
    }

    private fun formatPowerText(
        watts: Double?
    ): String {
        val value = watts?.takeIf { it >= 0.0 } ?: return "-- W"
        val rounded = (value * 10.0).roundToInt() / 10.0
        val roundedInt = rounded.roundToInt()

        return if (abs(rounded - roundedInt) < 0.05) {
            "$roundedInt W"
        } else {
            String.format(
                Locale.US,
                "%.1f W",
                rounded
            )
        }
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
                "Auto power preview · drag any slider to override"
            }

            ManualLightControlMode.MANUAL_OVERRIDE -> {
                if (isSliderInteractionActive) {
                    "Live power preview · sending while you adjust"
                } else {
                    "Manual override · sliders are controlling output"
                }
            }

            ManualLightControlMode.SCENE_OVERRIDE -> {
                "Scene power preview · drag a slider to customize"
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

    private fun LightChannelValues.toManualChannels(): LightRgbwChannels {
        val values = normalized()
        return LightRgbwChannels(
            red = values.red,
            green = values.green,
            blue = values.blue,
            white = values.white
        )
    }

    private fun LightRuntimeSnapshot.toManualScene(): ManualLightScene? {
        val source = activeSceneSource ?: localOverride?.sceneSource
        if (!source.isNullOrBlank()) {
            ManualLightScene.values().firstOrNull { scene ->
                scene.name.equals(source, ignoreCase = true)
            }?.let { scene ->
                return scene
            }
        }

        val name = activeSceneName ?: localOverride?.sceneName
        if (!name.isNullOrBlank()) {
            ManualLightScene.values().firstOrNull { scene ->
                scene.title.equals(name, ignoreCase = true)
            }?.let { scene ->
                return scene
            }
        }

        return null
    }

    private fun SavedLightPreset.toManualLightPreset(): ManualLightPreset {
        return ManualLightPreset(
            id = id,
            name = name,
            red = red.coerceIn(0, 100),
            green = green.coerceIn(0, 100),
            blue = blue.coerceIn(0, 100),
            white = white.coerceIn(0, 100),
            createdAtMillis = createdAt
        )
    }

    private fun LightRgbwChannels.toApiChannelValues(): LightChannelValues {
        val channels = sanitized()
        return LightChannelValues(
            red = channels.safeRed,
            green = channels.safeGreen,
            blue = channels.safeBlue,
            white = channels.safeWhite
        ).normalized()
    }

    private suspend fun sendManualOutputCommand(
        channels: LightRgbwChannels
    ): ApiResult<Unit> {
        return runtimeAccessor.setManualOutput(
            deviceId = deviceId,
            channelValues = channels.toApiChannelValues()
        )
    }

    private suspend fun sendResumeAutoCommand(): ApiResult<Unit> {
        return runtimeAccessor.resumeAuto(
            deviceId = deviceId
        )
    }

    companion object {
        private const val COMMAND_THROTTLE_MILLIS = 120L
    }
}
