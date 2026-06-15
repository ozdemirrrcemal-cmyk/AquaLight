package com.aqua.aqualight.ui.tabs.devices.add

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.add.DeviceAddCandidate
import com.aqua.aqualight.data.devices.add.DeviceAddSelection
import com.aqua.aqualight.data.devices.add.DeviceAddSetupTarget
import com.aqua.aqualight.data.devices.add.DeviceAddUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceAddViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val addUseCase =
        DeviceAddUseCase(
            context = application.applicationContext
        )

    private val _uiState =
        MutableStateFlow(
            DeviceAddUiState()
        )

    val uiState: StateFlow<DeviceAddUiState> =
        _uiState.asStateFlow()

    private val _events =
        Channel<DeviceAddEvent>(
            capacity = Channel.BUFFERED
        )

    val events: Flow<DeviceAddEvent> =
        _events.receiveAsFlow()

    fun loadCandidates() {
        if (_uiState.value.isLoading) {
            return
        }

        _uiState.update {
            it.copy(
                contentMode = DeviceAddContentMode.LOADING,
                isLoading = true,
                candidates = emptyList(),
                headerTitleRes = R.string.device_add_searching_title,
                retryEnabled = false,
                subtitleRes = R.string.device_add_searching_subtitle
            )
        }

        viewModelScope.launch {
            val candidates = try {
                addUseCase.loadCandidates()
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    throw exception
                }

                exception.printStackTrace()
                emptyList()
            }

            _uiState.update {
                if (candidates.isEmpty()) {
                    it.copy(
                        contentMode = DeviceAddContentMode.EMPTY,
                        isLoading = false,
                        candidates = emptyList(),
                        headerTitleRes = R.string.device_add_title,
                        retryEnabled = true,
                        subtitleRes = R.string.device_add_empty_subtitle
                    )
                } else {
                    it.copy(
                        contentMode = DeviceAddContentMode.CANDIDATES,
                        isLoading = false,
                        candidates = candidates,
                        headerTitleRes = R.string.device_add_title,
                        retryEnabled = true,
                        subtitleRes = null
                    )
                }
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                contentMode = DeviceAddContentMode.PERMISSION_REQUIRED,
                isLoading = false,
                candidates = emptyList(),
                headerTitleRes = R.string.device_add_title,
                retryEnabled = true,
                subtitleRes = R.string.device_add_permission_required
            )
        }

        viewModelScope.launch {
            _events.send(
                DeviceAddEvent.ShowMessage(
                    messageRes = R.string.device_add_permission_message,
                    level = DeviceAddMessageLevel.WARNING
                )
            )
        }
    }

    fun onCandidateClicked(
        candidate: DeviceAddCandidate
    ) {
        if (_uiState.value.isSaving) {
            return
        }

        _uiState.update {
            it.copy(
                isSaving = true
            )
        }

        viewModelScope.launch {
            try {
                when (
                    val selection = addUseCase.selectCandidate(
                        candidate = candidate
                    )
                ) {
                    is DeviceAddSelection.OpenDevice -> {
                        _events.send(
                            DeviceAddEvent.OpenDevice(
                                deviceId = selection.deviceId,
                                deviceTitle = selection.deviceTitle
                            )
                        )
                    }

                    is DeviceAddSelection.OpenSetupFlow -> {
                        _events.send(
                            DeviceAddEvent.OpenSetupFlow(
                                setupTarget = selection.setupTarget
                            )
                        )
                    }
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    throw exception
                }

                exception.printStackTrace()

                _events.send(
                    DeviceAddEvent.ShowMessage(
                        messageRes = R.string.device_add_save_error,
                        level = DeviceAddMessageLevel.ERROR
                    )
                )
            } finally {
                _uiState.update {
                    it.copy(
                        isSaving = false
                    )
                }
            }
        }
    }
}

data class DeviceAddUiState(
    val contentMode: DeviceAddContentMode = DeviceAddContentMode.IDLE,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val candidates: List<DeviceAddCandidate> = emptyList(),
    @StringRes val headerTitleRes: Int = R.string.device_add_title,
    val retryEnabled: Boolean = true,
    @StringRes val subtitleRes: Int? = null
)

enum class DeviceAddContentMode {
    IDLE,
    LOADING,
    CANDIDATES,
    EMPTY,
    PERMISSION_REQUIRED
}

sealed class DeviceAddEvent {

    data class ShowMessage(
        @StringRes val messageRes: Int,
        val level: DeviceAddMessageLevel
    ) : DeviceAddEvent()

    data class OpenDevice(
        val deviceId: Long,
        val deviceTitle: String
    ) : DeviceAddEvent()

    data class OpenSetupFlow(
        val setupTarget: DeviceAddSetupTarget
    ) : DeviceAddEvent()
}

enum class DeviceAddMessageLevel {
    WARNING,
    ERROR
}
