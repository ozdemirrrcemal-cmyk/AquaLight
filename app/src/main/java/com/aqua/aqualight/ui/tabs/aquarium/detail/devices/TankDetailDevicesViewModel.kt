package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.automation.LightAutomationDataStoreManager
import com.aqua.aqualight.data.devices.light.automation.model.LightAutomationSettings
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
import com.aqua.aqualight.data.devices.light.runtime.LightEffectiveRuntimeResolver
import com.aqua.aqualight.data.devices.light.runtime.LightEffectiveRuntimeState
import com.aqua.aqualight.data.devices.light.runtime.LightManualRuntimeState
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
        LightRuntimeRepository(appContext)

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

    private val lightRuntimeStatesFlow =
        MutableStateFlow<Map<Long, LightEffectiveRuntimeState>>(
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
                    lightRuntimeStatesFlow
                ) { devices, statuses, programs, lightStates, lightRuntimeStates ->
                    SourceState(
                        devices = devices,
                        statuses = statuses,
                        programs = programs,
                        lightStates = lightStates,
                        lightRuntimeStates = lightRuntimeStates
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
                                runtimeState = state.lightRuntimeStates[device.id]
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

            lightRuntimeStatesFlow.update { current ->
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

            seedInitialLightRuntimeState(
                deviceId = deviceId
            )

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

                        lightRuntimeStatesFlow.update { current ->
                            val currentMinute =
                                liveState.deviceTime?.curvePoint?.totalMinutes
                                    ?: currentPhoneMinute()

                            val runtimeState =
                                LightEffectiveRuntimeResolver.resolve(
                                    deviceId = deviceId,
                                    manualRuntime = manualRuntime,
                                    automationSettings = automationSettings,
                                    currentMinute = currentMinute
                                )

                            current + (deviceId to runtimeState)
                        }
                    }
                }
        }
    }


    private fun seedInitialLightRuntimeState(
        deviceId: Long
    ) {
        val manualRuntime =
            lightRuntimeRepository.currentManualRuntime(
                deviceId = deviceId
            )

        val runtimeState =
            LightEffectiveRuntimeResolver.resolve(
                deviceId = deviceId,
                manualRuntime = manualRuntime,
                automationSettings = null,
                currentMinute = currentPhoneMinute()
            )

        lightRuntimeStatesFlow.update { current ->
            current + (deviceId to runtimeState)
        }
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

        lightRuntimeStatesFlow.value =
            emptyMap()
    }

    override fun onCleared() {
        observeJob?.cancel()

        clearLightObservers()

        super.onCleared()
    }

    private fun currentPhoneMinute(): Int {
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
            calendar.get(java.util.Calendar.MINUTE)
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
        val lightRuntimeStates: Map<Long, LightEffectiveRuntimeState>
    )
}