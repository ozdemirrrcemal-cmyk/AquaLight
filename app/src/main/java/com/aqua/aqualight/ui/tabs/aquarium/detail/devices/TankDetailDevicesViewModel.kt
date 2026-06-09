package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
import com.aqua.aqualight.data.devices.light.runtime.LightManualRuntimeState
import com.aqua.aqualight.data.devices.light.runtime.LightOutputMath
import com.aqua.aqualight.data.devices.light.runtime.LightRuntimeRepository
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private fun updateLightObservers(
        devices: List<DevicesDataStoreManager.DeviceInfoUi>
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
                        )
                    ) { liveState, manualRuntime ->
                        liveState to manualRuntime
                    }.collect { pair ->
                        val liveState =
                            pair.first

                        val manualRuntime =
                            pair.second

                        lightStatesFlow.update { current ->
                            current + (
                                deviceId to liveState
                            )
                        }

                        lightModeOverridesFlow.update { current ->
                            val modeOverride =
                                buildModeOverrideFromManualRuntime(
                                    runtime = manualRuntime
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

    private data class SourceState(
        val devices: List<DevicesDataStoreManager.DeviceInfoUi>,
        val statuses: Map<Long, DeviceStatusState>,
        val programs: List<SavedLightProgram>,
        val lightStates: Map<Long, LightDeviceLiveState>,
        val lightModeOverrides: Map<Long, TankLightModeOverride>
    )
}