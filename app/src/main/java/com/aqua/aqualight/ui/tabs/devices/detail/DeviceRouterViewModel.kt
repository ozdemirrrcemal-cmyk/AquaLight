package com.aqua.aqualight.ui.tabs.devices.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.routing.DeviceRouterDestination
import com.aqua.aqualight.data.devices.routing.DeviceRouterRequest
import com.aqua.aqualight.data.devices.routing.DeviceRouterUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceRouterViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val routerUseCase =
        DeviceRouterUseCase(
            context = application.applicationContext
        )

    private val _uiState =
        MutableStateFlow(
            DeviceRouterUiState()
        )

    val uiState: StateFlow<DeviceRouterUiState> =
        _uiState.asStateFlow()

    private val _events =
        Channel<DeviceRouterEvent>(
            capacity = Channel.BUFFERED
        )

    val events: Flow<DeviceRouterEvent> =
        _events.receiveAsFlow()

    private var hasResolvedRoute = false

    fun resolveRoute(
        deviceId: Long,
        deviceTitle: String
    ) {
        if (hasResolvedRoute) {
            return
        }

        hasResolvedRoute = true

        _uiState.update { state ->
            state.copy(
                isRouting = true
            )
        }

        viewModelScope.launch {
            val destination = try {
                routerUseCase.resolveDestination(
                    request = DeviceRouterRequest(
                        deviceId = deviceId,
                        deviceTitle = deviceTitle
                    )
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    throw exception
                }

                exception.printStackTrace()

                DeviceRouterDestination.Unsupported(
                    title = DEFAULT_DEVICE_TITLE,
                    message = "Device information could not be opened."
                )
            }

            _uiState.update { state ->
                state.copy(
                    isRouting = false
                )
            }

            _events.send(
                DeviceRouterEvent.OpenDestination(
                    destination = destination
                )
            )
        }
    }

    private companion object {
        const val DEFAULT_DEVICE_TITLE = "Device"
    }
}

data class DeviceRouterUiState(
    val isRouting: Boolean = false
)

sealed class DeviceRouterEvent {

    data class OpenDestination(
        val destination: DeviceRouterDestination
    ) : DeviceRouterEvent()
}
