package com.aqua.aqualight.ui.tabs.devices

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.ui.tabs.devices.route.DeviceMenuOpenGate
import com.aqua.aqualight.ui.tabs.devices.route.DeviceMenuOpenGateResult
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

            var removedCount = 0
            val failedDeviceUids = linkedSetOf<String>()

            selected.forEach { rawDeviceUid ->
                val deviceUid = DeviceUid(rawDeviceUid)
                val deviceDeleteSucceeded = runCatching {
                    repository.forgetDevice(deviceUid)
                }.onFailure { error ->
                    Log.e(
                        TAG,
                        "Failed to delete device ${deviceUid.value}.",
                        error
                    )
                }.isSuccess

                if (!deviceDeleteSucceeded) {
                    failedDeviceUids += rawDeviceUid
                    return@forEach
                }

                removedCount += 1

                runCatching {
                    assignmentRepository.removeDeviceFromAnyTank(deviceUid)
                }.onFailure { error ->
                    Log.e(
                        TAG,
                        "Device deleted but assignment cleanup failed for ${deviceUid.value}.",
                        error
                    )
                }
            }

            selectedDeviceUids.value = failedDeviceUids
            deletingDevices.value = false
            clockMillis.value = System.currentTimeMillis()

            if (failedDeviceUids.isNotEmpty()) {
                _events.send(
                    DevicesEvent.ShowDeleteFailure(
                        removedCount = removedCount,
                        failedCount = failedDeviceUids.size
                    )
                )
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
        val deviceData = combine(
            repository.devices,
            assignmentRepository.assignedTankByDevice()
        ) { snapshots, assignmentByDeviceUid ->
            snapshots to assignmentByDeviceUid
        }

        viewModelScope.launch {
            combine(
                deviceData,
                clockMillis,
                selectedDeviceUids,
                openingDeviceMenu,
                deletingDevices
            ) { data, now, selectedUids, isOpeningDeviceMenu, isDeletingDevices ->
                val (snapshots, assignmentByDeviceUid) = data
                val cards = snapshots.map { snapshot ->
                    val assignedTankText = assignmentByDeviceUid[snapshot.deviceUid]
                        ?.tankName
                        ?.takeIf { tankName -> tankName.isNotBlank() }
                        ?.let { tankName ->
                            getApplication<Application>().getString(
                                R.string.device_assigned_tank_supporting_text,
                                tankName
                            )
                        }
                        .orEmpty()
                    val card = DeviceCardMapper.map(
                        snapshot = snapshot,
                        assignedTankText = assignedTankText,
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
                    isOpeningDeviceMenu = isOpeningDeviceMenu,
                    isDeletingDevices = isDeletingDevices
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
        const val TAG = "DevicesViewModel"
        const val UI_CLOCK_TICK_INTERVAL_MS = 5_000L
    }
}
