package com.aqua.aqualight.ui.tabs.settings.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.card.DeviceCardStateMapper
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceStatusViewModel(
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

    private val deviceCardStateMapper =
        DeviceCardStateMapper()

    private val _uiState =
        MutableStateFlow(
            DeviceStatusUiState()
        )

    val uiState: StateFlow<DeviceStatusUiState> =
        _uiState.asStateFlow()

    init {
        DevicePresenceMonitor.start(
            context = appContext
        )

        observeDeviceStatus()
    }

    private fun observeDeviceStatus() {
        viewModelScope.launch {
            combine(
                devicesStore.devicesFlow,
                DevicePresenceMonitor.statuses,
                tankStore.tanksFlow
            ) { devices, statuses, tanks ->
                buildUiState(
                    devices = devices,
                    statuses = statuses,
                    tanks = tanks
                )
            }.collect { state ->
                _uiState.update {
                    state
                }
            }
        }
    }

    private fun buildUiState(
        devices: List<DevicesDataStoreManager.DeviceInfo>,
        statuses: Map<Long, DeviceStatusState>,
        tanks: List<SavedAquariumTank>
    ): DeviceStatusUiState {
        val uiDevices =
            deviceCardStateMapper.mapAll(
                devices = devices,
                statuses = statuses,
                tanks = tanks,
                unassignedTankText = NOT_CONNECTED_TEXT,
                unknownTankText = UNKNOWN_AQUARIUM_TEXT,
                includeLastSeenText = true
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
                    productLine = cardState.productLine,
                    productModel = cardState.productModel,
                    skuCode = cardState.skuCode,
                    setupCode = cardState.setupCode,
                    deviceUid = cardState.deviceUid,
                    macAddress = cardState.macAddress,
                    serialNumber = cardState.serialNumber,
                    shortId = cardState.shortId,
                    hardwareRevision = cardState.hardwareRevision,
                    firmwareVersion = cardState.firmwareVersion,
                    protocolVersion = cardState.protocolVersion,
                    productMetaText = cardState.productMetaText,
                    identityText = cardState.identityText,
                    networkText = cardState.networkText,
                    statusText = cardState.statusText
                )
            }

        return DeviceStatusUiState(
            devices = uiDevices,
            isEmpty = uiDevices.isEmpty()
        )
    }

    private companion object {
        const val NOT_CONNECTED_TEXT = "Not connected"
        const val UNKNOWN_AQUARIUM_TEXT = "Unknown aquarium"
    }
}

data class DeviceStatusUiState(
    val devices: List<DeviceCardUi> = emptyList(),
    val isEmpty: Boolean = true
)
