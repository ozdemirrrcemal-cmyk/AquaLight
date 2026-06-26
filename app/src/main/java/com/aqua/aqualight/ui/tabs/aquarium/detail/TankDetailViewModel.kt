package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TankDetailViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _isOpeningDevice = MutableStateFlow(false)
    val isOpeningDevice: StateFlow<Boolean> = _isOpeningDevice.asStateFlow()

    private val _events = MutableSharedFlow<TankDetailEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TankDetailEvent> = _events.asSharedFlow()

    fun openDevice(deviceId: Long, deviceTitle: String) {
        viewModelScope.launch {
            _events.emit(TankDetailEvent.ShowUnsupported)
        }
    }

    sealed interface TankDetailEvent {
        data class NavigateToDeviceRouter(val deviceId: Long, val deviceTitle: String) : TankDetailEvent
        object ShowOffline : TankDetailEvent
        object ShowNotFound : TankDetailEvent
        object ShowUnsupported : TankDetailEvent
        object ShowOpenFailed : TankDetailEvent
    }
}
