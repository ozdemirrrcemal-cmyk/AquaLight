package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.access.DeviceAccessGuard
import com.aqua.aqualight.data.devices.access.DeviceOpenResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TankDetailViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val deviceAccessGuard =
        DeviceAccessGuard(
            context = application.applicationContext
        )

    private val _uiState =
        MutableStateFlow(
            TankDetailUiState()
        )

    val uiState: StateFlow<TankDetailUiState> =
        _uiState.asStateFlow()

    private val _events =
        MutableSharedFlow<TankDetailEvent>(
            extraBufferCapacity = 1
        )

    val events: SharedFlow<TankDetailEvent> =
        _events.asSharedFlow()

    fun selectTab(
        tab: TankDetailTab
    ) {
        _uiState.update { current ->
            current.copy(
                selectedTab = tab
            )
        }
    }

    fun openDevice(
        deviceId: Long,
        deviceTitle: String
    ) {
        if (
            deviceId <= 0L ||
            _uiState.value.isOpeningDevice
        ) {
            return
        }

        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isOpeningDevice = true
                )
            }

            try {
                val result =
                    deviceAccessGuard.resolveForOpen(
                        deviceId = deviceId
                    )

                when (result) {
                    is DeviceOpenResult.Allowed -> {
                        _events.emit(
                            TankDetailEvent.NavigateToDeviceRouter(
                                deviceId = result.device.id,
                                deviceIp = result.ip,
                                deviceTitle = deviceTitle
                            )
                        )
                    }

                    is DeviceOpenResult.Offline -> {
                        _events.emit(
                            TankDetailEvent.ShowOffline
                        )
                    }

                    DeviceOpenResult.NotFound -> {
                        _events.emit(
                            TankDetailEvent.ShowNotFound
                        )
                    }

                    is DeviceOpenResult.Unsupported -> {
                        _events.emit(
                            TankDetailEvent.ShowUnsupported
                        )
                    }
                }
            } catch (exception: Exception) {
                _events.emit(
                    TankDetailEvent.ShowOpenFailed
                )
            } finally {
                _uiState.update { current ->
                    current.copy(
                        isOpeningDevice = false
                    )
                }
            }
        }
    }

    data class TankDetailUiState(
        val selectedTab: TankDetailTab = TankDetailTab.DEVICES,
        val isOpeningDevice: Boolean = false
    )

    sealed interface TankDetailEvent {

        data class NavigateToDeviceRouter(
            val deviceId: Long,
            val deviceIp: String,
            val deviceTitle: String
        ) : TankDetailEvent

        object ShowOffline : TankDetailEvent

        object ShowNotFound : TankDetailEvent

        object ShowUnsupported : TankDetailEvent

        object ShowOpenFailed : TankDetailEvent
    }
}