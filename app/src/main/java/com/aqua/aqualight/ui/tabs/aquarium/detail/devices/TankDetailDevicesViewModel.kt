package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
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
                    lightStatesFlow
                ) { devices, statuses, programs, lightStates ->
                    SourceState(
                        devices = devices,
                        statuses = statuses,
                        programs = programs,
                        lightStates = lightStates
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
                                now = now
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
                    LightDeviceLiveRefreshManager.observe(
                        deviceId = deviceId
                    ).collect { liveState ->
                        lightStatesFlow.update { current ->
                            current + (
                                deviceId to liveState
                                )
                        }
                    }
                }
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
        val lightStates: Map<Long, LightDeviceLiveState>
    )
}