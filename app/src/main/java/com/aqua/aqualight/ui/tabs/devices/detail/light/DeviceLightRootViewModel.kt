package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusParser
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceLightRootViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = DevicesRepositoryProvider.get(application)

    private val _uiState = MutableStateFlow(DeviceLightRootUiState())
    val uiState: StateFlow<DeviceLightRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: DeviceUid? = null
    private var observeJob: Job? = null
    private var runtimeStatusJob: Job? = null
    private var pendingStatusRequestId: String = ""

    private var runtimeChannelCountText: String? = null
    private var runtimeManualText: String? = null
    private var runtimeProgramsText: String? = null

    fun bind(
        deviceUidText: String,
        fallbackTitle: String
    ) {
        if (deviceUidText.isBlank()) {
            observeJob?.cancel()
            runtimeStatusJob?.cancel()
            clearRuntimeOverlay()

            _uiState.value = DeviceLightRootUiState(
                title = fallbackTitle.ifBlank { DEFAULT_TITLE },
                deviceUid = "",
                connectionStatus = "Missing deviceUid",
                authStatus = "Unknown"
            )
            return
        }

        val deviceUid = DeviceUid(deviceUidText)
        if (boundDeviceUid == deviceUid) {
            return
        }

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        runtimeStatusJob?.cancel()
        clearRuntimeOverlay()

        _uiState.value = DeviceLightRootUiState(
            title = fallbackTitle.ifBlank { DEFAULT_TITLE },
            deviceUid = deviceUid.value,
            connectionStatus = "Loading",
            authStatus = "Unknown"
        )

        observeJob = viewModelScope.launch {
            repository.observeDevice(deviceUid).collect { snapshot ->
                _uiState.value = (
                    snapshot
                        ?.toLightRootUiState(fallbackTitle = fallbackTitle)
                        ?: DeviceLightRootUiState(
                            title = fallbackTitle.ifBlank { DEFAULT_TITLE },
                            deviceUid = deviceUid.value,
                            connectionStatus = "Device not found",
                            authStatus = "Unknown"
                        )
                    ).withRuntimeOverlay()
            }
        }

        runtimeStatusJob = viewModelScope.launch {
            observeRuntimeStatus(deviceUid)
        }
    }

    private suspend fun observeRuntimeStatus(deviceUid: DeviceUid) {
        val events = repository.runtimeEvents()
        if (events == null) {
            updateRuntimeOverlay(
                manualText = "Light runtime WebSocket repository is not configured."
            )
            return
        }

        coroutineScope {
            launch {
                repository.connectRuntime(deviceUid)
                delay(800L)

                val result = repository.runtimeModules()
                    ?.light
                    ?.requestStatus(deviceUid)

                pendingStatusRequestId = result?.messageId.orEmpty()

                if (result?.isSuccess == true && pendingStatusRequestId.isNotBlank()) {
                    updateRuntimeOverlay(
                        manualText = "Light runtime status requested..."
                    )
                } else {
                    updateRuntimeOverlay(
                        manualText = "Light runtime status request could not be sent."
                    )
                }
            }

            events.collect { event ->
                handleRuntimeEvent(
                    deviceUid = deviceUid,
                    event = event
                )
            }
        }
    }

    private fun handleRuntimeEvent(
        deviceUid: DeviceUid,
        event: AqlWsEvent
    ) {
        if (event.deviceUid != deviceUid) return

        val message = (event as? AqlWsEvent.Message)
            ?.parsed
            ?: return

        val statusRequestId = pendingStatusRequestId
        if (statusRequestId.isBlank() || message.id != statusRequestId) {
            return
        }

        when (message) {
            is AqlWsIncomingMessage.Response -> {
                if (!message.ok) {
                    updateRuntimeStatusError(
                        statusCode = message.statusCode,
                        message = "Runtime status request failed."
                    )
                    return
                }

                val data = message.json.optJSONObject("data") ?: message.json
                val status = DeviceLightStatusParser.parse(data)

                updateRuntimeOverlay(
                channelCountText = status.channelCount.toString(),
                manualText = "Runtime channels: ${status.channelCount}, manual: ${status.manualSupported}, liveEdit: ${status.liveEditEnabled}",
                programsText = "Programs: ${status.programCount}, presets: ${status.presetsSupported}, simulation: ${status.simulationSupported}, writable: ${!status.runtime.readOnly}"
            )
            }

            is AqlWsIncomingMessage.Error -> {
                updateRuntimeOverlay(manualText = "Light runtime status error: ${message.statusCode} ${message.message}".trim())
            }

            else -> Unit
        }
    }

    private fun updateRuntimeStatusError(
        statusCode: Int,
        message: String
    ) {
        val errorText = "$DEFAULT_TITLE runtime status error: $statusCode $message".trim()
        updateRuntimeOverlay(manualText = errorText)
    }

    private fun updateRuntimeOverlay(
        channelCountText: String? = null,
        manualText: String? = null,
        programsText: String? = null
    ) {
        if (channelCountText != null) runtimeChannelCountText = channelCountText
        if (manualText != null) runtimeManualText = manualText
        if (programsText != null) runtimeProgramsText = programsText

        _uiState.value = _uiState.value.withRuntimeOverlay()
    }

    private fun clearRuntimeOverlay() {
        pendingStatusRequestId = ""
        runtimeChannelCountText = null
        runtimeManualText = null
        runtimeProgramsText = null
    }

    private fun DeviceLightRootUiState.withRuntimeOverlay(): DeviceLightRootUiState {
        return copy(
            channelCountText = runtimeChannelCountText ?: channelCountText,
            manualMenuText = runtimeManualText ?: manualMenuText,
            programsMenuText = runtimeProgramsText ?: programsMenuText
        )
    }

    private fun DeviceSnapshot.toLightRootUiState(fallbackTitle: String): DeviceLightRootUiState {
        val productName = product.displayName
            .ifBlank { product.model }
            .ifBlank { fallbackTitle }
            .ifBlank { DEFAULT_TITLE }

        val menuSections = DeviceRootMenuMapper.light(this)

        return DeviceLightRootUiState(
            title = productName,
            deviceUid = deviceUid.value,
            connectionStatus = connectionState.onlineState.connectionLabel(),
            authStatus = connectionState.onlineState.authLabel(),
            ipText = endpoint.ip.ifBlank { "Unknown" },
            firmwareText = firmwareLabel(),
            modelText = modelLabel(),
            channelCountText = if (limits.lightChannelCount > 0) {
                limits.lightChannelCount.toString()
            } else {
                "Unknown"
            },
            featuresText = featureLabel(),
            manualMenuText = menuSections.primaryText(
                emptyText = "Firmware has not exposed manual light controls yet."
            ),
            programsMenuText = menuSections.secondaryText(
                emptyText = "Firmware has not exposed light programs, presets or settings yet."
            )
        )
    }

    private fun DeviceSnapshot.firmwareLabel(): String {
        return listOf(
            firmwareVersion.ifBlank { null },
            firmwareBuild.ifBlank { null }
        )
            .filterNotNull()
            .joinToString(separator = " / ")
            .ifBlank { "Unknown" }
    }

    private fun DeviceSnapshot.modelLabel(): String {
        return listOf(
            product.model.ifBlank { null },
            product.hardwareRevision.ifBlank { null }
        )
            .filterNotNull()
            .joinToString(separator = " / ")
            .ifBlank { "Unknown" }
    }

    private fun DeviceSnapshot.featureLabel(): String {
        val labels = buildList {
            if (capabilities.manualLight) add("Manual light")
            if (capabilities.lightProgram) add("Program")
            if (capabilities.lightPresets) add("Presets")
            if (capabilities.lightSimulation) add("Simulation")
            if (capabilities.temperature) add("Temperature")
            if (capabilities.ota) add("OTA")
            supportedFeatures
                .filter { feature -> feature.isNotBlank() }
                .forEach { feature -> add(feature) }
            supportedScreens
                .filter { screen -> screen.isNotBlank() }
                .forEach { screen -> add(screen) }
        }

        return labels
            .distinct()
            .joinToString(separator = ", ")
            .ifBlank { "Unknown" }
    }

    private fun DeviceOnlineState.connectionLabel(): String {
        return when (this) {
            DeviceOnlineState.UNKNOWN -> "Unknown"
            DeviceOnlineState.DISCOVERING -> "Discovering"
            DeviceOnlineState.ONLINE_LAN -> "Online LAN"
            DeviceOnlineState.CONNECTING_WS -> "Connecting WebSocket"
            DeviceOnlineState.AUTHENTICATED -> "Authenticated"
            DeviceOnlineState.STALE -> "Stale"
            DeviceOnlineState.OFFLINE -> "Offline"
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE -> "Local network offline"
            DeviceOnlineState.AUTH_REQUIRED -> "Auth required"
            DeviceOnlineState.PROVISIONING -> "Provisioning"
            DeviceOnlineState.OTA_UPDATING -> "OTA updating"
            DeviceOnlineState.ERROR -> "Error"
        }
    }

    private fun DeviceOnlineState.authLabel(): String {
        return when (this) {
            DeviceOnlineState.AUTHENTICATED -> "Authenticated"
            DeviceOnlineState.AUTH_REQUIRED -> "Auth required"
            DeviceOnlineState.CONNECTING_WS -> "Authenticating"
            DeviceOnlineState.ERROR -> "Auth unknown"
            else -> "Not authenticated"
        }
    }

    private companion object {
        const val DEFAULT_TITLE = "Light"
    }
}

data class DeviceLightRootUiState(
    val title: String = "Light",
    val deviceUid: String = "",
    val connectionStatus: String = "Unknown",
    val authStatus: String = "Unknown",
    val ipText: String = "Unknown",
    val firmwareText: String = "Unknown",
    val modelText: String = "Unknown",
    val channelCountText: String = "Unknown",
    val featuresText: String = "Unknown",
    val manualMenuText: String = "Firmware has not exposed manual light controls yet.",
    val programsMenuText: String = "Firmware has not exposed light programs, presets or settings yet."
)
