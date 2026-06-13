package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.devices.TankAssignedDevicesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TankDetailDevicesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    private val assignedDevicesRepository =
        TankAssignedDevicesRepository(
            context = appContext
        )

    private val mapper =
        TankAssignedDeviceUiMapper()

    private val _uiState =
        MutableStateFlow(
            TankDetailDevicesUiState()
        )

    val uiState: StateFlow<TankDetailDevicesUiState> =
        _uiState.asStateFlow()

    private var observeJob: Job? =
        null

    private var tankId: Long =
        0L

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

        if (tankId <= 0L) {
            _uiState.value =
                TankDetailDevicesUiState()

            return
        }

        observeJob =
            viewModelScope.launch {
                assignedDevicesRepository.assignedDeviceCardsFlow(
                    tankId = tankId,
                    unknownAquariumText = appContext.getString(
                        R.string.aquarium_device_unknown_aquarium
                    )
                ).collect { snapshots ->
                    val items =
                        snapshots.map { snapshot ->
                            mapper.map(
                                snapshot = snapshot
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
                assignedDevicesRepository.removeDeviceFromTank(
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

    override fun onCleared() {
        observeJob?.cancel()

        super.onCleared()
    }
}
