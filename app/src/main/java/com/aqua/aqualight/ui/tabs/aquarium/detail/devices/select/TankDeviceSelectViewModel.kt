package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.card.DeviceCardStateMapper
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TankDeviceSelectViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    private val devicesStore =
        DevicesDataStoreManager.create(
            appContext
        )

    private val deviceCardStateMapper =
        DeviceCardStateMapper()

    private val selectItemMapper =
        TankDeviceSelectItemMapper()

    private val _uiState =
        MutableStateFlow(
            TankDeviceSelectUiState()
        )

    val uiState: StateFlow<TankDeviceSelectUiState> =
        _uiState.asStateFlow()

    private val _events =
        MutableSharedFlow<TankDeviceSelectEvent>(
            extraBufferCapacity = 1
        )

    val events: SharedFlow<TankDeviceSelectEvent> =
        _events.asSharedFlow()

    init {
        DevicePresenceMonitor.start(
            context = appContext
        )

        observeAvailableDevices()
    }

    private fun observeAvailableDevices() {
        viewModelScope.launch {
            combine(
                devicesStore.unassignedDevicesFlow,
                DevicePresenceMonitor.statuses
            ) { devices, statuses ->
                val cardStates =
                    deviceCardStateMapper.mapAll(
                        devices = devices,
                        statuses = statuses
                    )

                selectItemMapper.mapAll(
                    cardStates = cardStates
                )
            }.collect { items ->
                _uiState.update { current ->
                    current.copy(
                        devices = items,
                        isEmpty = items.isEmpty()
                    )
                }
            }
        }
    }

    fun assignDeviceToTank(
        deviceId: Long,
        tankId: Long
    ) {
        if (
            deviceId <= 0L ||
            tankId <= 0L ||
            _uiState.value.isAssigning
        ) {
            return
        }

        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isAssigning = true
                )
            }

            try {
                devicesStore.assignDeviceToTank(
                    deviceId = deviceId,
                    tankId = tankId
                )

                _events.emit(
                    TankDeviceSelectEvent.DeviceAssigned(
                        deviceId = deviceId,
                        tankId = tankId
                    )
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                _uiState.update { current ->
                    current.copy(
                        isAssigning = false
                    )
                }

                _events.emit(
                    TankDeviceSelectEvent.ShowAssignError
                )
            }
        }
    }
}
