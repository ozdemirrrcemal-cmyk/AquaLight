package com.aqua.aqualight.ui.tabs.devices.detail.light.manual

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.presets.LightPresetDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightDeviceCommandManager
import com.aqua.aqualight.data.devices.light.runtime.LightChannelSemantic
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
import com.aqua.aqualight.data.devices.light.runtime.LightManualRuntimeState
import com.aqua.aqualight.data.devices.light.runtime.LightRuntimeRepository
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightScene
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.SavedLightPreset
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceLightManualViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
    application.applicationContext

    private val lightPresetDataStoreManager =
    LightPresetDataStoreManager(appContext)

    private val lightRuntimeRepository =
    LightRuntimeRepository(
        commandManager = Esp32LightDeviceCommandManager(
            context = appContext
        )
    )

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

    private val pendingManualChannelValues =
    mutableMapOf<LightChannelSemantic, Int>()

    private var pendingFullManualOutput = false
    private var manualChannelSendLoopJob: Job? = null
    private var lastManualChannelSendStartedAtMillis: Long = 0L
    private var lastManualChannelErrorToastAtMillis: Long = 0L
    private var isSliderInteractionActive = false

    private val liveRefreshOwnerKey =
    "DeviceLightManualViewModel_${System.identityHashCode(this)}"

    fun initialize(
        deviceId: Long
    ) {
        val previousDeviceId = this.deviceId

        if (
            previousDeviceId > 0L &&
            previousDeviceId != deviceId
        ) {
            LightDeviceLiveRefreshManager.stop(
                deviceId = previousDeviceId,
                ownerKey = liveRefreshOwnerKey
            )
        }

        this.deviceId = deviceId

        observeRuntimeJob?.cancel()

        LightDeviceLiveRefreshManager.start(
            context = appContext,
            deviceId = deviceId,
            ownerKey = liveRefreshOwnerKey
        )

        observeRuntimeJob = viewModelScope.launch {
            combine(
                lightRuntimeRepository.observeManualRuntime(deviceId),
                LightDeviceLiveRefreshManager.observe(deviceId)
            ) {
                runtime, liveState ->
                runtime to liveState
            }.collect {
                (runtime, liveState) ->
                _uiState.update {
                    current ->
                    val preservePreviewValues = isManualLiveEditing()

                    val runtimeState = applyRuntimeState(
                        current = current,
                        runtime = runtime,
                        preservePreviewValues = preservePreviewValues
                    )

                    applyLiveDeviceState(
                        state = runtimeState,
                        liveState = liveState,
                        preservePreviewValues = preservePreviewValues
                    )
                }
            }
        }

        LightDeviceLiveRefreshManager.refreshNow(
            context = appContext,
            deviceId = deviceId
        )
    }

    fun setPowerOn(
        enabled: Boolean
    ) {
        val currentState = _uiState.value

        if (
            !enabled &&
            !currentState.isManualMode &&
            !currentState.isManualScene
        ) {
            return
        }

        cancelPendingManualChannelSends()

        viewModelScope.launch {
            if (!hasValidDeviceId()) {
                return@launch
            }

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
            } else {
                LightDeviceLiveRefreshManager.refreshNow(
                    context = appContext,
                    deviceId = deviceId
                )
            }
        }
    }

    fun previewRed(
        value: Int
    ) {
        updatePreviewChannel(
            semantic = LightChannelSemantic.RED,
            red = value
        )
    }

    fun previewGreen(
        value: Int
    ) {
        updatePreviewChannel(
            semantic = LightChannelSemantic.GREEN,
            green = value
        )
    }

    fun previewBlue(
        value: Int
    ) {
        updatePreviewChannel(
            semantic = LightChannelSemantic.BLUE,
            blue = value
        )
    }

    fun previewWhite(
        value: Int
    ) {
        updatePreviewChannel(
            semantic = LightChannelSemantic.WHITE,
            white = value
        )
    }

    fun beginSliderInteraction() {
        isSliderInteractionActive = true
    }

    fun endSliderInteraction() {
        isSliderInteractionActive = false
    }

    private fun updatePreviewChannel(
        semantic: LightChannelSemantic,
        red: Int? = null,
        green: Int? = null,
        blue: Int? = null,
        white: Int? = null
    ) {
        val wasManualOverrideActive =
        _uiState.value.isManualMode || _uiState.value.isManualScene

        _uiState.update {
            state ->
            val newRed = red?.coerceIn(0, 100) ?: state.red
            val newGreen = green?.coerceIn(0, 100) ?: state.green
            val newBlue = blue?.coerceIn(0, 100) ?: state.blue
            val newWhite = white?.coerceIn(0, 100) ?: state.white

            recalculateOutput(
                state.copy(
                    isManualMode = true,
                    isManualScene = false,
                    activeSceneName = null,
                    activeSceneSource = null,
                    isPowerOn = listOf(
                        newRed,
                        newGreen,
                        newBlue,
                        newWhite
                    ).any {
                        value ->
                        value > 0
                    },
                    red = newRed,
                    green = newGreen,
                    blue = newBlue,
                    white = newWhite
                )
            ).withCalculatedPowerText()
        }

        if (wasManualOverrideActive) {
            enqueueManualChannelSend(
                semantic = semantic
            )
        } else {
            enqueueFullManualOutputSend()
        }
    }

    private fun enqueueFullManualOutputSend() {
        pendingFullManualOutput = true
        pendingManualChannelValues.clear()

        if (manualChannelSendLoopJob?.isActive == true) {
            return
        }

        startManualChannelSendLoop()
    }

    private fun enqueueManualChannelSend(
        semantic: LightChannelSemantic
    ) {
        val state = _uiState.value

        val value = when (semantic) {
            LightChannelSemantic.RED -> state.red
            LightChannelSemantic.GREEN -> state.green
            LightChannelSemantic.BLUE -> state.blue
            LightChannelSemantic.WHITE -> state.white
            LightChannelSemantic.UNKNOWN -> return
        }

        pendingManualChannelValues[semantic] = value

        if (manualChannelSendLoopJob?.isActive == true) {
            return
        }

        startManualChannelSendLoop()
    }

    private fun startManualChannelSendLoop() {
        manualChannelSendLoopJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastManualChannelSendStartedAtMillis
                val waitMs = MIN_MANUAL_CHANNEL_SEND_INTERVAL_MS - elapsed

                if (waitMs > 0L) {
                    delay(waitMs)
                }

                if (pendingFullManualOutput) {
                    pendingFullManualOutput = false
                    pendingManualChannelValues.clear()

                    val state = _uiState.value

                    lastManualChannelSendStartedAtMillis =
                    System.currentTimeMillis()

                    sendFullManualOutput(
                        red = state.red,
                        green = state.green,
                        blue = state.blue,
                        white = state.white
                    )

                    continue
                }

                val nextEntry = pendingManualChannelValues.entries.firstOrNull()
                ?: break

                val semantic = nextEntry.key
                val value = nextEntry.value

                pendingManualChannelValues.remove(semantic)

                lastManualChannelSendStartedAtMillis =
                System.currentTimeMillis()

                sendManualChannelValue(
                    semantic = semantic,
                    value = value
                )
            }

            manualChannelSendLoopJob = null

            if (
                pendingFullManualOutput ||
                pendingManualChannelValues.isNotEmpty()
            ) {
                startManualChannelSendLoop()
            }
        }
    }

    private suspend fun sendManualChannelValue(
        semantic: LightChannelSemantic,
        value: Int
    ) {
        if (!hasValidDeviceId()) {
            return
        }

        val result = lightRuntimeRepository.updateManualChannel(
            deviceId = deviceId,
            semantic = semantic,
            valuePercent = value
        )

        if (!result.isSuccess) {
            maybeShowManualChannelError(
                message = result.message ?: "Manual channel could not be sent"
            )
        }
    }

    private suspend fun sendFullManualOutput(
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ) {
        if (!hasValidDeviceId()) {
            return
        }

        val result = lightRuntimeRepository.updateManualOutput(
            deviceId = deviceId,
            red = red,
            green = green,
            blue = blue,
            white = white
        )

        if (!result.isSuccess) {
            maybeShowManualChannelError(
                message = result.message ?: "Manual output could not be sent"
            )
        }
    }

    private suspend fun maybeShowManualChannelError(
        message: String
    ) {
        val now = System.currentTimeMillis()

        if (
            now - lastManualChannelErrorToastAtMillis <
            MANUAL_CHANNEL_ERROR_TOAST_COOLDOWN_MS
        ) {
            return
        }

        lastManualChannelErrorToastAtMillis = now

        eventsChannel.send(
            ManualLightEvent.ShowError(message)
        )
    }

    private fun isManualLiveEditing(): Boolean {
        return isSliderInteractionActive ||
        pendingFullManualOutput ||
        manualChannelSendLoopJob?.isActive == true ||
        pendingManualChannelValues.isNotEmpty()
    }

    fun applyScene(
        scene: ManualLightScene
    ) {
        cancelPendingManualChannelSends()

        val sceneName = scene.toDisplayName()

        viewModelScope.launch {
            if (!hasValidDeviceId()) {
                return@launch
            }

            val result = lightRuntimeRepository.applyManualScene(
                deviceId = deviceId,
                sceneName = sceneName,
                red = scene.red,
                green = scene.green,
                blue = scene.blue,
                white = scene.white
            )

            if (result.isSuccess) {
                _uiState.update {
                    state ->
                    state.copy(
                        activeSceneSource = "Manual Scenes"
                    )
                }

                LightDeviceLiveRefreshManager.refreshNow(
                    context = appContext,
                    deviceId = deviceId
                )

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

    fun resumeAuto() {
        cancelPendingManualChannelSends()

        viewModelScope.launch {
            if (!hasValidDeviceId()) {
                return@launch
            }

            val result = lightRuntimeRepository.resumeAuto(
                deviceId = deviceId
            )

            if (result.isSuccess) {
                LightDeviceLiveRefreshManager.refreshNow(
                    context = appContext,
                    deviceId = deviceId
                )

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
            ).any {
                value ->
                value > 0
            }

            if (!hasAnyOutput) {
                eventsChannel.send(
                    ManualLightEvent.ShowError(
                        "Set at least one channel before saving"
                    )
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
                    ManualLightEvent.ShowError(
                        "Preset name is required"
                    )
                )
                return@launch
            }

            val state = _uiState.value

            val hasAnyOutput = listOf(
                state.red,
                state.green,
                state.blue,
                state.white
            ).any {
                value ->
                value > 0
            }

            if (!hasAnyOutput) {
                eventsChannel.send(
                    ManualLightEvent.ShowError(
                        "Set at least one channel before saving"
                    )
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

    private fun cancelPendingManualChannelSends() {
        manualChannelSendLoopJob?.cancel()
        manualChannelSendLoopJob = null

        pendingFullManualOutput = false
        pendingManualChannelValues.clear()
        isSliderInteractionActive = false
    }

    private fun applyRuntimeState(
        current: ManualLightUiState,
        runtime: LightManualRuntimeState,
        preservePreviewValues: Boolean
    ): ManualLightUiState {
        val isManualOverrideActive =
        runtime.isManualMode || runtime.isManualScene

        return current.copy(
            isManualMode = runtime.isManualMode,
            isManualScene = runtime.isManualScene,
            activeSceneName = runtime.activeSceneName,
            activeSceneSource = if (runtime.isManualScene) {
                current.activeSceneSource ?: "Manual Scene"
            } else {
                null
            },
            isPowerOn = when {
                preservePreviewValues -> {
                    current.isPowerOn
                }

                isManualOverrideActive -> {
                    runtime.isPowerOn
                } else -> {
                    false
                }
            },
            red = when {
                preservePreviewValues -> {
                    current.red
                }

                isManualOverrideActive -> {
                    runtime.red
                } else -> {
                    0
                }
            },
            green = when {
                preservePreviewValues -> {
                    current.green
                }

                isManualOverrideActive -> {
                    runtime.green
                } else -> {
                    0
                }
            },
            blue = when {
                preservePreviewValues -> {
                    current.blue
                }

                isManualOverrideActive -> {
                    runtime.blue
                } else -> {
                    0
                }
            },
            white = when {
                preservePreviewValues -> {
                    current.white
                }

                isManualOverrideActive -> {
                    runtime.white
                } else -> {
                    0
                }
            }
        )
    }

    private fun applyLiveDeviceState(
        state: ManualLightUiState,
        liveState: LightDeviceLiveState,
        preservePreviewValues: Boolean
    ): ManualLightUiState {
        val isManualOverrideActive =
        state.isManualMode || state.isManualScene

        val shouldApplyLiveToManualControls =
        liveState.hasLiveChannels &&
        !preservePreviewValues &&
        isManualOverrideActive

        val liveChannelState = if (shouldApplyLiveToManualControls) {
            state.copy(
                isPowerOn = liveState.actualPowerWatts
                ?.let {
                    watts ->
                    watts > 0.0
                }
                ?: (liveState.actualOutputPercent > 0),

                red = liveState.channelValuePercent(
                    LightChannelSemantic.RED
                ) ?: state.red,

                green = liveState.channelValuePercent(
                    LightChannelSemantic.GREEN
                ) ?: state.green,

                blue = liveState.channelValuePercent(
                    LightChannelSemantic.BLUE
                ) ?: state.blue,

                white = liveState.channelValuePercent(
                    LightChannelSemantic.WHITE
                ) ?: state.white
            )
        } else {
            state
        }

        val calibratedState = liveChannelState.copy(
            redMaxWatts = liveState.channelFor(
                LightChannelSemantic.RED
            )?.maxWatts ?: liveChannelState.redMaxWatts,

            greenMaxWatts = liveState.channelFor(
                LightChannelSemantic.GREEN
            )?.maxWatts ?: liveChannelState.greenMaxWatts,

            blueMaxWatts = liveState.channelFor(
                LightChannelSemantic.BLUE
            )?.maxWatts ?: liveChannelState.blueMaxWatts,

            whiteMaxWatts = liveState.channelFor(
                LightChannelSemantic.WHITE
            )?.maxWatts ?: liveChannelState.whiteMaxWatts
        )

        val calculatedState = recalculateOutput(
            state = calibratedState
        )

        return if (shouldApplyLiveToManualControls) {
            calculatedState.copy(
                masterOutputPercent = calculatedState.masterOutputPercent,
                powerText = liveState.actualPowerWatts
                ?.let {
                    watts ->
                    formatWatts(watts)
                }
                ?: "-- W"
            )
        } else {
            calculatedState.withCalculatedPowerText()
        }
    }

    private fun LightDeviceLiveState.channelValuePercent(
        semantic: LightChannelSemantic
    ): Int? {
        return channelFor(
            semantic = semantic
        )?.valuePercent?.coerceIn(0, 100)
    }

    private fun ManualLightUiState.withCalculatedPowerText(): ManualLightUiState {
        return if (hasPowerCalibration) {
            copy(
                powerText = formatWatts(estimatedPowerWatts)
            )
        } else {
            copy(
                powerText = "-- W"
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
            !state.isPowerOn -> {
                0
            }

            hasPowerCalibration -> {
                ((estimatedPower / maxPower) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
            } else -> {
                fallbackOutputPercent
            }
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

    private fun formatWatts(
        watts: Double
    ): String {
        val roundedTenths = (watts * 10.0)
        .roundToInt()

        val absoluteTenths = abs(roundedTenths)
        val sign = if (roundedTenths < 0) {
            "-"
        } else {
            ""
        }

        val wholePart = absoluteTenths / 10
        val decimalPart = absoluteTenths % 10

        return if (decimalPart == 0) {
            "$sign${wholePart}W"
        } else {
            "$sign${wholePart}.${decimalPart}W"
        }
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
        cancelPendingManualChannelSends()

        if (deviceId > 0L) {
            LightDeviceLiveRefreshManager.stop(
                deviceId = deviceId,
                ownerKey = liveRefreshOwnerKey
            )
        }

        super.onCleared()
    }

    companion object {
        private const val MIN_MANUAL_CHANNEL_SEND_INTERVAL_MS = 120L
        private const val MANUAL_CHANNEL_ERROR_TOAST_COOLDOWN_MS = 3_000L
    }
}