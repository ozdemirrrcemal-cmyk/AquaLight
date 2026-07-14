package com.aqua.aqualight.ui.tabs.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.remove.OwnerDeviceDataCleaner
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.ui.tabs.devices.route.DeviceMenuOpenGate
import com.aqua.aqualight.ui.tabs.devices.route.DeviceMenuOpenGateResult
import java.util.concurrent.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DevicesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = DevicesRepositoryProvider.get(application)
    private val assignmentRepository =
        TankDeviceAssignmentRepositoryProvider.get(application)
    private val deviceDataCleaner = OwnerDeviceDataCleaner.create(
        devicesRepository = repository,
        assignmentRepository = assignmentRepository
    )
    private val menuOpenGate = DeviceMenuOpenGate(repository)
    private val clockMillis = MutableStateFlow(System.currentTimeMillis())
    private val selectedDeviceUids = MutableStateFlow<Set<String>>(emptySet())
    private val openingDeviceMenu = MutableStateFlow(false)
    private val deletingDevices = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private val _events = Channel<DevicesEvent>(capacity = Channel.BUFFERED)
    val events: Flow<DevicesEvent> = _events.receiveAsFlow()

    init {
        repository.start(viewModelScope)
        observeDevices()
        startUiClockTicker()
        repository.refreshVisibleDevices()
    }

    fun onScreenVisible() {
        repository.refreshVisibleDevices()
        clockMillis.value = System.currentTimeMillis()
    }

    fun onDeviceClicked(deviceUid: String) {
        if (deviceUid.isBlank() || deletingDevices.value) return

        if (_uiState.value.selectionMode) {
            toggleDeviceSelection(deviceUid)
            return
        }

        if (openingDeviceMenu.value) {
            return
        }

        viewModelScope.launch {
            openingDeviceMenu.value = true
            val result = runCatching {
                menuOpenGate.resolve(deviceUid)
            }.getOrElse {
                DeviceMenuOpenGateResult.Blocked(
                    title = "",
                    messageRes = R.string.device_menu_offline_message
                )
            }
            openingDeviceMenu.value = false
            clockMillis.value = System.currentTimeMillis()

            when (result) {
                is DeviceMenuOpenGateResult.OpenRoute -> {
                    _events.send(DevicesEvent.OpenRoute(result.route))
                }

                is DeviceMenuOpenGateResult.Blocked -> {
                    _events.send(
                        DevicesEvent.ShowDeviceUnavailable(
                            title = result.title,
                            messageRes = result.messageRes
                        )
                    )
                }
            }
        }
    }

    fun onDeviceLongClicked(deviceUid: String) {
        if (deviceUid.isBlank() || deletingDevices.value) return
        selectedDeviceUids.value = selectedDeviceUids.value + deviceUid
    }

    fun clearSelection() {
        if (deletingDevices.value) return
        selectedDeviceUids.value = emptySet()
    }

    fun deleteSelectedDevices() {
        val selected = selectedDeviceUids.value
        if (selected.isEmpty() || deletingDevices.value) return

        viewModelScope.launch {
            deletingDevices.value = true

            try {
                val result = deviceDataCleaner.deleteDevices(
                    selected.map(::DeviceUid)
                )
                val failedUids = result.failures
                    .map { failure -> failure.deviceUid.value }
                    .toSet()

                selectedDeviceUids.value = failedUids
                clockMillis.value = System.currentTimeMillis()

                when {
                    result.isCompleteSuccess -> Unit

                    result.isCompleteFailure -> {
                        _events.send(
                            DevicesEvent.ShowDeleteFailed(
                                failedCount = result.failedCount
                            )
                        )
                    }

                    else -> {
                        _events.send(
                            DevicesEvent.ShowDeletePartialSuccess(
                                succeededCount = result.succeededCount,
                                failedCount = result.failedCount
                            )
                        )
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }

                _events.send(
                    DevicesEvent.ShowDeleteFailed(
                        failedCount = selected.size
                    )
                )
            } finally {
                deletingDevices.value = false
            }
        }
    }

    private fun toggleDeviceSelection(deviceUid: String) {
        val current = selectedDeviceUids.value
        selectedDeviceUids.value = if (deviceUid in current) {
            current - deviceUid
        } else {
            current + deviceUid
        }
    }

    private fun observeDevices() {
        val operationState = combine(
            openingDeviceMenu,
            deletingDevices
        ) { isOpeningDeviceMenu, isDeletingDevices ->
            OperationState(
                isOpeningDeviceMenu = isOpeningDeviceMenu,
                isDeletingDevices = isDeletingDevices
            )
        }

        viewModelScope.launch {
            combine(
                repository.devices,
                assignmentRepository.assignedTankNamesByDevice(),
                clockMillis,
                selectedDeviceUids,
                operationState
            ) { snapshots, tankNamesByDevice, now, selectedUids, operation ->
                val cards = snapshots.map { snapshot ->
                    val card = DeviceCardMapper.map(
                        snapshot = snapshot,
                        assignedTankName = tankNamesByDevice[snapshot.deviceUid],
                        nowMillis = now
                    )
                    card.copy(isSelected = card.deviceUid in selectedUids)
                }
                val visibleSelectedCount = cards.count { card -> card.isSelected }

                DevicesUiState(
                    devices = cards,
                    isEmpty = cards.isEmpty(),
                    isDiscovering = cards.isEmpty(),
                    selectionMode = visibleSelectedCount > 0,
                    selectedCount = visibleSelectedCount,
                    isOpeningDeviceMenu = operation.isOpeningDeviceMenu,
                    isDeletingDevices = operation.isDeletingDevices
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun startUiClockTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(UI_CLOCK_TICK_INTERVAL_MS)
                clockMillis.value = System.currentTimeMillis()
            }
        }
    }

    private data class OperationState(
        val isOpeningDeviceMenu: Boolean,
        val isDeletingDevices: Boolean
    )

    data class DevicesUiState(
        val devices: List<DeviceCardUi> = emptyList(),
        val isEmpty: Boolean = true,
        val isDiscovering: Boolean = true,
        val selectionMode: Boolean = false,
        val selectedCount: Int = 0,
        val isOpeningDeviceMenu: Boolean = false,
        val isDeletingDevices: Boolean = false
    )

    private companion object {
        const val UI_CLOCK_TICK_INTERVAL_MS = 5_000L
    }
}
