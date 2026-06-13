package com.aqua.aqualight.ui.tabs.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.access.DeviceAccessGuard
import com.aqua.aqualight.data.devices.access.DeviceOpenResult
import com.aqua.aqualight.data.devices.card.DeviceCardStateMapper
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DevicesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    private val devicesStore =
        DevicesDataStoreManager.create(
            appContext
        )

    private val tankStore =
        AquariumTankDataStoreManager(
            appContext
        )

    private val deviceAccessGuard =
        DeviceAccessGuard(
            context = appContext
        )

    private val deviceCardStateMapper =
        DeviceCardStateMapper()

    private val _uiState =
        MutableStateFlow(
            DevicesUiState()
        )

    val uiState: StateFlow<DevicesUiState> =
        _uiState.asStateFlow()

    private val _events =
        MutableSharedFlow<DevicesEvent>(
            extraBufferCapacity = 1
        )

    val events: SharedFlow<DevicesEvent> =
        _events.asSharedFlow()

    init {
        DevicePresenceMonitor.start(
            context = appContext
        )

        observeDevices()
    }

    private fun observeDevices() {
        viewModelScope.launch {
            combine(
                devicesStore.devicesFlow,
                DevicePresenceMonitor.statuses,
                tankStore.tanksFlow
            ) { devices, statuses, tanks ->
                buildDeviceCards(
                    devices = devices,
                    tanks = tanks,
                    statuses = statuses
                )
            }.collect { cards ->
                _uiState.update { current ->
                    current.copy(
                        devices = cards,
                        isEmpty = cards.isEmpty(),
                        selectionMode = if (cards.isEmpty()) {
                            false
                        } else {
                            current.selectionMode
                        }
                    )
                }
            }
        }
    }

    private fun buildDeviceCards(
        devices: List<DevicesDataStoreManager.DeviceInfo>,
        tanks: List<SavedAquariumTank>,
        statuses: Map<Long, com.aqua.aqualight.data.devices.presence.DeviceStatusState>
    ): List<DeviceCardUi> {
        return deviceCardStateMapper.mapAll(
            devices = devices,
            statuses = statuses,
            tanks = tanks,
            unassignedTankText = "",
            unknownTankText = "Unknown aquarium"
        ).map { cardState ->
            DeviceCardUi(
                id = cardState.deviceId,
                displayName = cardState.title,
                familyName = cardState.familyName,
                tankName = cardState.tankName,
                ip = cardState.ip,
                serial = cardState.serial,
                firmwareBuild = cardState.firmwareBuild,
                isOnline = cardState.isOnline,
                lastSeenText = cardState.lastSeenText,
                productId = cardState.productId,
                productKey = cardState.productKey,
                category = cardState.category,
                deviceType = cardState.deviceType
            )
        }
    }

    fun openDevice(
        device: DeviceCardUi
    ) {
        if (
            device.id <= 0L ||
            _uiState.value.isOpeningDevice ||
            _uiState.value.isDeletingDevices
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
                        deviceId = device.id
                    )

                when (result) {
                    is DeviceOpenResult.Allowed -> {
                        _events.emit(
                            DevicesEvent.NavigateToDeviceRouter(
                                deviceId = result.device.id,
                                deviceIp = result.ip,
                                deviceTitle = device.displayName
                            )
                        )
                    }

                    is DeviceOpenResult.Offline -> {
                        _events.emit(
                            DevicesEvent.ShowOffline
                        )
                    }

                    DeviceOpenResult.NotFound -> {
                        _events.emit(
                            DevicesEvent.ShowNotFound
                        )
                    }

                    is DeviceOpenResult.Unsupported -> {
                        _events.emit(
                            DevicesEvent.ShowUnsupported
                        )
                    }
                }
            } catch (exception: Exception) {
                _events.emit(
                    DevicesEvent.ShowOpenFailed
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

    fun enterSelectionMode() {
        if (_uiState.value.selectionMode) {
            return
        }

        _uiState.update { current ->
            current.copy(
                selectionMode = true
            )
        }
    }

    fun onSelectionChanged(
        selectedCount: Int
    ) {
        if (selectedCount == 0) {
            exitSelectionMode()
        }
    }

    fun exitSelectionMode() {
        if (!_uiState.value.selectionMode) {
            return
        }

        _uiState.update { current ->
            current.copy(
                selectionMode = false
            )
        }
    }

    fun deleteSelectedDevices(
        ids: Set<Long>
    ) {
        if (
            ids.isEmpty() ||
            _uiState.value.isDeletingDevices
        ) {
            if (ids.isEmpty()) {
                exitSelectionMode()
            }

            return
        }

        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isDeletingDevices = true
                )
            }

            try {
                devicesStore.deleteDevices(
                    ids = ids
                )

                _uiState.update { current ->
                    current.copy(
                        selectionMode = false
                    )
                }
            } catch (exception: Exception) {
                _events.emit(
                    DevicesEvent.ShowDeleteFailed
                )
            } finally {
                _uiState.update { current ->
                    current.copy(
                        isDeletingDevices = false
                    )
                }
            }
        }
    }

    data class DevicesUiState(
        val devices: List<DeviceCardUi> = emptyList(),
        val isEmpty: Boolean = true,
        val selectionMode: Boolean = false,
        val isOpeningDevice: Boolean = false,
        val isDeletingDevices: Boolean = false
    )

    sealed interface DevicesEvent {

        data class NavigateToDeviceRouter(
            val deviceId: Long,
            val deviceIp: String,
            val deviceTitle: String
        ) : DevicesEvent

        data object ShowOffline : DevicesEvent

        data object ShowNotFound : DevicesEvent

        data object ShowUnsupported : DevicesEvent

        data object ShowOpenFailed : DevicesEvent

        data object ShowDeleteFailed : DevicesEvent
    }
}