package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.automation.LightAutomationDataStoreManager
import com.aqua.aqualight.data.devices.light.automation.model.LightAutomationSettings
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
import com.aqua.aqualight.data.devices.light.runtime.LightManualRuntimeState
import com.aqua.aqualight.data.devices.light.runtime.LightManualRuntimeStore
import com.aqua.aqualight.data.devices.light.runtime.LightProgramRuntimeEvaluator
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
                            devices = items,
                            errorMessage = _uiState.value.errorMessage
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
            try {
                devicesStore.removeDeviceFromTank(
                    deviceId = deviceId
                )
            } catch (exception: Exception) {
                exception.printStackTrace()
                _uiState.update { current ->
                    current.copy(
                        errorMessage = appContext.getString(R.string.aquarium_error_device_remove_failed)
                    )
                }
            }
        }
    }

    fun consumeErrorMessage() {
        _uiState.update { current ->
            current.copy(
                errorMessage = null
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
                        ),
                        DevicePresenceMonitor.statuses
                    ) { liveState, manualRuntime, automationSettings, statuses ->
                        LightRuntimeSource(
                            liveState = liveState,
                            manualRuntime = manualRuntime,
                            automationSettings = automationSettings,
                            presenceState = statuses[deviceId]
                        )
                    }.collect { source ->
                        val isOnline =
                            source.presenceState?.isOnline == true

                        val liveState =
                            if (isOnline) {
                                source.liveState
                            } else {
                                LightDeviceLiveState.initial(
                                    deviceId = deviceId
                                )
                            }

                        val manualRuntime =
                            source.manualRuntime

                        val automationSettings =
                            source.automationSettings

                        if (!isOnline) {
                            LightManualRuntimeStore.clear(
                                deviceId = deviceId
                            )
                        }

                        lightStatesFlow.update { current ->
                            current + (
                                deviceId to liveState
                            )
                        }

                        lightModeOverridesFlow.update { current ->
                            if (!isOnline) {
                                return@update current - deviceId
                            }

                            val currentMinute =
                                liveState.deviceTime
                                    ?.takeIf { liveState.hasDeviceTime }
                                    ?.curvePoint
                                    ?.totalMinutes

                            val modeOverride =
                                buildModeOverrideFromManualRuntime(
                                    runtime = manualRuntime
                                ) ?: currentMinute?.let { minute ->
                                    buildModeOverrideFromAutomation(
                                        settings = automationSettings,
                                        currentMinute = minute
                                    )
                                }

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

        return TankLightModeOverride(
            mode = TankLightCardMode.MOONLIGHT,
            title = TankAssignedDeviceText.MOONLIGHT_MODE_TITLE,
            leftText = LightProgramRuntimeEvaluator.labelForMinute(startMinute),
            rightText = LightProgramRuntimeEvaluator.labelForMinute(endMinute),
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

        if (runtime.isManualScene) {
            val sceneName =
                runtime.activeSceneName.orEmpty()
                    .ifBlank {
                        TankAssignedDeviceText.SCENE_MODE_TITLE
                    }

            return TankLightModeOverride(
                mode = TankLightCardMode.SCENE,
                title = sceneName
            )
        }

        if (runtime.isManualMode) {
            return TankLightModeOverride(
                mode = TankLightCardMode.MANUAL,
                title = TankAssignedDeviceText.MANUAL_CONTROL_TITLE
            )
        }

        return null
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
        val automationSettings: LightAutomationSettings,
        val presenceState: DeviceStatusState?
    )

    private data class SourceState(
        val devices: List<DevicesDataStoreManager.DeviceInfo>,
        val statuses: Map<Long, DeviceStatusState>,
        val programs: List<SavedLightProgram>,
        val lightStates: Map<Long, LightDeviceLiveState>,
        val lightModeOverrides: Map<Long, TankLightModeOverride>
    )
}