package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.automation.LightAutomationDataStoreManager
import com.aqua.aqualight.data.devices.light.automation.model.LightAutomationSettings
import com.aqua.aqualight.data.devices.light.automation.model.MoonlightChannel
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
import com.aqua.aqualight.data.devices.light.runtime.LightManualRuntimeState
import com.aqua.aqualight.data.devices.light.runtime.LightOutputMath
import com.aqua.aqualight.data.devices.light.runtime.LightRuntimeRepository
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class TankDetailDevicesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    private val devicesStore =
        DevicesDataStoreManager.create(
            appContext
        )

    private val lightProgramsStore =
        LightProgramsDataStoreManager(
            appContext
        )

    private val lightAutomationStore =
        LightAutomationDataStoreManager(
            appContext
        )

    private val lightRuntimeRepository =
        LightRuntimeRepository()

    private val mapper =
        TankAssignedDeviceUiMapper()

    private val _uiState =
        MutableStateFlow(
            TankDetailDevicesUiState()
        )

    val uiState: StateFlow<TankDetailDevicesUiState> =
        _uiState.asStateFlow()

    private val lightStatesFlow =
        MutableStateFlow<Map<Long, LightDeviceLiveState>>(
            emptyMap()
        )

    private val lightModeOverridesFlow =
        MutableStateFlow<Map<Long, TankLightModeOverride>>(
            emptyMap()
        )

    private val lightLiveJobs =
        mutableMapOf<Long, Job>()

    private var observeJob: Job? =
        null

    private var tankId: Long =
        0L

    private val liveRefreshOwnerKey =
        "TankDetailDevicesViewModel_${System.identityHashCode(this)}"

    fun initialize(
        tankId: Long
    ) {
        if (
            this.tankId == tankId &&
            observeJob != null
        ) {
            return
        }

        this.tankId =
            tankId

        observeJob?.cancel()

        clearLightObservers()

        if (tankId <= 0L) {
            _uiState.value =
                TankDetailDevicesUiState()

            return
        }

        DevicePresenceMonitor.start(
            context = appContext
        )

        observeJob =
            viewModelScope.launch {
                combine(
                    devicesStore.devicesForTankFlow(
                        tankId
                    ),
                    DevicePresenceMonitor.statuses,
                    lightProgramsStore.programsFlow,
                    lightStatesFlow,
                    lightModeOverridesFlow
                ) { devices, statuses, programs, lightStates, lightModeOverrides ->
                    SourceState(
                        devices = devices,
                        statuses = statuses,
                        programs = programs,
                        lightStates = lightStates,
                        lightModeOverrides = lightModeOverrides
                    )
                }.collect { state ->
                    updateLightObservers(
                        devices = state.devices
                    )

                    val now =
                        System.currentTimeMillis()

                    val items =
                        state.devices.map { device ->
                            mapper.map(
                                device = device,
                                statuses = state.statuses,
                                programs = state.programs,
                                lightState = state.lightStates[device.id],
                                now = now,
                                modeOverride = state.lightModeOverrides[device.id]
                            )
                        }

                    _uiState.value =
                        TankDetailDevicesUiState(
                            devices = items
                        )
                }
            }
    }
	
	fun removeDeviceFromTank(
    deviceId: Long
) {
    if (deviceId <= 0L) {
        return
    }

    viewModelScope.launch {
        devicesStore.removeDeviceFromTank(
            deviceId = deviceId
        )
    }
}

    private fun updateLightObservers(
        devices: List<DevicesDataStoreManager.DeviceInfo>
    ) {
        val lightDeviceIds =
            devices
                .filter { device ->
                    mapper.isLightDeviceForObserver(
                        device = device
                    )
                }
                .map { device ->
                    device.id
                }
                .toSet()

        val removedIds =
            lightLiveJobs.keys - lightDeviceIds

        removedIds.forEach { deviceId ->
            lightLiveJobs.remove(
                deviceId
            )?.cancel()

            lightStatesFlow.update { current ->
                current - deviceId
            }

            lightModeOverridesFlow.update { current ->
                current - deviceId
            }

            LightDeviceLiveRefreshManager.stop(
                deviceId = deviceId,
                ownerKey = liveRefreshOwnerKey
            )
        }

        lightDeviceIds.forEach { deviceId ->
            if (lightLiveJobs.containsKey(deviceId)) {
                return@forEach
            }

            LightDeviceLiveRefreshManager.start(
                context = appContext,
                deviceId = deviceId,
                ownerKey = liveRefreshOwnerKey
            )

            LightDeviceLiveRefreshManager.refreshNow(
                context = appContext,
                deviceId = deviceId
            )

            lightLiveJobs[deviceId] =
                viewModelScope.launch {
                    combine(
                        LightDeviceLiveRefreshManager.observe(
                            deviceId = deviceId
                        ),
                        lightRuntimeRepository.observeManualRuntime(
                            deviceId = deviceId
                        ),
                        lightAutomationStore.observeSettings(
                            deviceId = deviceId
                        )
                    ) { liveState, manualRuntime, automationSettings ->
                        LightRuntimeSource(
                            liveState = liveState,
                            manualRuntime = manualRuntime,
                            automationSettings = automationSettings
                        )
                    }.collect { source ->
                        val liveState =
                            source.liveState

                        val manualRuntime =
                            source.manualRuntime

                        val automationSettings =
                            source.automationSettings

                        lightStatesFlow.update { current ->
                            current + (
                                deviceId to liveState
                            )
                        }

                        lightModeOverridesFlow.update { current ->
                            val currentMinute =
                                liveState.deviceTime?.curvePoint?.totalMinutes
                                    ?: currentPhoneMinute()

                            val modeOverride =
                                buildModeOverrideFromManualRuntime(
                                    runtime = manualRuntime
                                ) ?: buildModeOverrideFromAutomation(
                                    settings = automationSettings,
                                    currentMinute = currentMinute
                                )

                            if (modeOverride == null) {
                                current - deviceId
                            } else {
                                current + (
                                    deviceId to modeOverride
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun buildModeOverrideFromAutomation(
        settings: LightAutomationSettings,
        currentMinute: Int
    ): TankLightModeOverride? {
        val moonlight = settings.moonlight

        if (!moonlight.enabled) {
            return null
        }

        val startMinute = if (moonlight.followProgramEnd) {
            moonlight.startTime.totalMinutes
        } else {
            moonlight.startTime.totalMinutes
        }

        val endMinute = moonlight.endTime.totalMinutes

        if (!isMinuteInRange(
                currentMinute = currentMinute,
                startMinute = startMinute,
                endMinute = endMinute
            )
        ) {
            return null
        }

        val intensity = moonlight.intensityPercent.coerceIn(1, 15)
        val softWhite = (intensity / 2).coerceAtLeast(1)
        val red = 0
        val green = 0
        val blue = when (moonlight.channel) {
            MoonlightChannel.BLUE,
            MoonlightChannel.BLUE_WHITE -> intensity
            MoonlightChannel.WHITE -> 0
        }
        val white = when (moonlight.channel) {
            MoonlightChannel.WHITE -> intensity
            MoonlightChannel.BLUE_WHITE -> softWhite
            MoonlightChannel.BLUE -> 0
        }

        return TankLightModeOverride(
            mode = TankLightCardMode.MOONLIGHT,
            title = "Moonlight Mode",
            outputPercent = LightOutputMath.outputPercent(
                red = red,
                green = green,
                blue = blue,
                white = white
            ),
            red = red,
            green = green,
            blue = blue,
            white = white,
            leftText = labelForMinute(startMinute),
            rightText = labelForMinute(endMinute),
            timelineProgressPercent = moonlightProgressPercent(
                currentMinute = currentMinute,
                startMinute = startMinute,
                endMinute = endMinute
            )
        )
    }

    private fun isMinuteInRange(
        currentMinute: Int,
        startMinute: Int,
        endMinute: Int
    ): Boolean {
        if (startMinute == endMinute) {
            return false
        }

        return if (startMinute < endMinute) {
            currentMinute >= startMinute && currentMinute < endMinute
        } else {
            currentMinute >= startMinute || currentMinute < endMinute
        }
    }

    private fun moonlightProgressPercent(
        currentMinute: Int,
        startMinute: Int,
        endMinute: Int
    ): Int {
        if (startMinute == endMinute) {
            return 0
        }

        val duration =
            if (endMinute > startMinute) {
                endMinute - startMinute
            } else {
                (MINUTES_PER_DAY - startMinute) + endMinute
            }

        if (duration <= 0) {
            return 0
        }

        val elapsed =
            if (currentMinute >= startMinute) {
                currentMinute - startMinute
            } else {
                (MINUTES_PER_DAY - startMinute) + currentMinute
            }

        return ((elapsed.toDouble() / duration.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun buildModeOverrideFromManualRuntime(
        runtime: LightManualRuntimeState
    ): TankLightModeOverride? {
        val isManualActive =
            runtime.isManualMode || runtime.isManualScene

        if (!isManualActive) {
            return null
        }

        val outputPercent =
            manualOutputPercent(
                runtime = runtime
            )

        val red =
            manualChannelPercent(
                isPowerOn = runtime.isPowerOn,
                value = runtime.red
            )

        val green =
            manualChannelPercent(
                isPowerOn = runtime.isPowerOn,
                value = runtime.green
            )

        val blue =
            manualChannelPercent(
                isPowerOn = runtime.isPowerOn,
                value = runtime.blue
            )

        val white =
            manualChannelPercent(
                isPowerOn = runtime.isPowerOn,
                value = runtime.white
            )

        if (runtime.isManualScene) {
            val sceneName =
                runtime.activeSceneName.orEmpty()
                    .ifBlank {
                        "Scene Mode"
                    }

            return TankLightModeOverride(
                mode = TankLightCardMode.SCENE,
                title = sceneName,
                outputPercent = outputPercent,
                red = red,
                green = green,
                blue = blue,
                white = white
            )
        }

        if (runtime.isManualMode) {
            return TankLightModeOverride(
                mode = TankLightCardMode.MANUAL,
                title = "Manual Control",
                outputPercent = outputPercent,
                red = red,
                green = green,
                blue = blue,
                white = white
            )
        }

        return null
    }

    private fun currentPhoneMinute(): Int {
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
            calendar.get(java.util.Calendar.MINUTE)
    }

    private fun labelForMinute(
        minute: Int
    ): String {
        val normalized = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val hour = normalized / 60
        val min = normalized % 60
        return "%02d:%02d".format(hour, min)
    }

    private fun manualOutputPercent(
        runtime: LightManualRuntimeState
    ): Int {
        if (!runtime.isPowerOn) {
            return 0
        }

        return LightOutputMath.outputPercent(
            red = runtime.red,
            green = runtime.green,
            blue = runtime.blue,
            white = runtime.white
        )
    }

    private fun manualChannelPercent(
        isPowerOn: Boolean,
        value: Int
    ): Int {
        if (!isPowerOn) {
            return 0
        }

        return value.coerceIn(
            0,
            100
        )
    }

    private fun clearLightObservers() {
        lightLiveJobs.forEach { entry ->
            entry.value.cancel()

            LightDeviceLiveRefreshManager.stop(
                deviceId = entry.key,
                ownerKey = liveRefreshOwnerKey
            )
        }

        lightLiveJobs.clear()

        lightStatesFlow.value =
            emptyMap()

        lightModeOverridesFlow.value =
            emptyMap()
    }

    override fun onCleared() {
        observeJob?.cancel()

        clearLightObservers()

        super.onCleared()
    }

    private companion object {
        private const val MINUTES_PER_DAY = 24 * 60
    }

    private data class LightRuntimeSource(
        val liveState: LightDeviceLiveState,
        val manualRuntime: LightManualRuntimeState,
        val automationSettings: LightAutomationSettings
    )

    private data class SourceState(
        val devices: List<DevicesDataStoreManager.DeviceInfo>,
        val statuses: Map<Long, DeviceStatusState>,
        val programs: List<SavedLightProgram>,
        val lightStates: Map<Long, LightDeviceLiveState>,
        val lightModeOverrides: Map<Long, TankLightModeOverride>
    )
}