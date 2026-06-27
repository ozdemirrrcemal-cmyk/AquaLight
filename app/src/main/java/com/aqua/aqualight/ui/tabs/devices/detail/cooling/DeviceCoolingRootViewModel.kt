package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatusParser
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootKind
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceCoolingRootViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = DevicesRepositoryProvider.get(application)

    private val _uiState = MutableStateFlow(DeviceCoolingRootUiState(title = DEFAULT_TITLE))
    val uiState: StateFlow<DeviceCoolingRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: DeviceUid? = null
    private var observeJob: Job? = null
    private var runtimeStatusJob: Job? = null

    private var runtimePrimaryCountText: String? = null
    private var runtimePrimaryPlaceholder: String? = null
    private var runtimeSecondaryPlaceholder: String? = null

    fun bind(
        deviceUidText: String,
        fallbackTitle: String
    ) {
        if (deviceUidText.isBlank()) {
            observeJob?.cancel()
            runtimeStatusJob?.cancel()
            clearRuntimeOverlay()

            _uiState.value = DeviceCoolingRootUiState(
                title = fallbackTitle.ifBlank { DEFAULT_TITLE },
                deviceUid = "",
                connectionStatus = "Missing deviceUid",
                authStatus = "Unknown",
                primaryCountLabel = KIND.primaryCountLabel,
                primarySectionTitle = KIND.primarySectionTitle,
                primarySectionPlaceholder = KIND.primarySectionPlaceholder,
                secondarySectionTitle = KIND.secondarySectionTitle,
                secondarySectionPlaceholder = KIND.secondarySectionPlaceholder
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

        _uiState.value = DeviceCoolingRootUiState(
            title = fallbackTitle.ifBlank { DEFAULT_TITLE },
            deviceUid = deviceUid.value,
            connectionStatus = "Loading",
            authStatus = "Unknown",
            primaryCountLabel = KIND.primaryCountLabel,
            primarySectionTitle = KIND.primarySectionTitle,
            primarySectionPlaceholder = KIND.primarySectionPlaceholder,
            secondarySectionTitle = KIND.secondarySectionTitle,
            secondarySectionPlaceholder = KIND.secondarySectionPlaceholder
        )

        observeJob = viewModelScope.launch {
            repository.observeDevice(deviceUid).collect { snapshot ->
                _uiState.value = (
                    snapshot
                        ?.toRootUiState(fallbackTitle = fallbackTitle)
                        ?: DeviceCoolingRootUiState(
                            title = fallbackTitle.ifBlank { DEFAULT_TITLE },
                            deviceUid = deviceUid.value,
                            connectionStatus = "Device not found",
                            authStatus = "Unknown",
                            primaryCountLabel = KIND.primaryCountLabel,
                            primarySectionTitle = KIND.primarySectionTitle,
                            primarySectionPlaceholder = KIND.primarySectionPlaceholder,
                            secondarySectionTitle = KIND.secondarySectionTitle,
                            secondarySectionPlaceholder = KIND.secondarySectionPlaceholder
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
                primaryPlaceholder = "$DEFAULT_TITLE runtime WebSocket repository is not configured."
            )
            return
        }

        coroutineScope {
            launch {
                repository.connectRuntime(deviceUid)
                delay(800L)

                val sent = repository.runtimeModules()?.cooling?.requestStatus(deviceUid)?.isSuccess == true

                if (sent) {
                    updateRuntimeOverlay(
                        primaryPlaceholder = "$DEFAULT_TITLE runtime status requested..."
                    )
                } else {
                    updateRuntimeOverlay(
                        primaryPlaceholder = "$DEFAULT_TITLE runtime status request could not be sent."
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

        val response = (event as? AqlWsEvent.Message)
            ?.parsed as? AqlWsIncomingMessage.Response
            ?: return

        if (response.module != DeviceCoolingRuntimeContract.MODULE ||
            response.action != DeviceCoolingRuntimeContract.Action.STATUS_GET
        ) {
            return
        }

        if (!response.ok) {
            updateRuntimeOverlay(
                primaryPlaceholder = "$DEFAULT_TITLE runtime status error: ${response.statusCode}"
            )
            return
        }

        val data = response.json.optJSONObject("data") ?: response.json
        val status = DeviceCoolingStatusParser.parse(data)

        __STATUS_RENDER__
    }

    private fun updateRuntimeOverlay(
        primaryCountText: String? = null,
        primaryPlaceholder: String? = null,
        secondaryPlaceholder: String? = null
    ) {
        if (primaryCountText != null) runtimePrimaryCountText = primaryCountText
        if (primaryPlaceholder != null) runtimePrimaryPlaceholder = primaryPlaceholder
        if (secondaryPlaceholder != null) runtimeSecondaryPlaceholder = secondaryPlaceholder

        _uiState.value = _uiState.value.withRuntimeOverlay()
    }

    private fun clearRuntimeOverlay() {
        runtimePrimaryCountText = null
        runtimePrimaryPlaceholder = null
        runtimeSecondaryPlaceholder = null
    }

    private fun DeviceCoolingRootUiState.withRuntimeOverlay(): DeviceCoolingRootUiState {
        return copy(
            primaryCountText = runtimePrimaryCountText ?: primaryCountText,
            primarySectionPlaceholder = runtimePrimaryPlaceholder ?: primarySectionPlaceholder,
            secondarySectionPlaceholder = runtimeSecondaryPlaceholder ?: secondarySectionPlaceholder
        )
    }

    private fun DeviceSnapshot.toRootUiState(fallbackTitle: String): DeviceCoolingRootUiState {
        val productName = product.displayName
            .ifBlank { product.model }
            .ifBlank { fallbackTitle }
            .ifBlank { DEFAULT_TITLE }

        val menuSections = DeviceRootMenuMapper.overview(
            kind = KIND,
            snapshot = this
        )

        return DeviceCoolingRootUiState(
            title = productName,
            deviceUid = deviceUid.value,
            connectionStatus = connectionState.onlineState.connectionLabel(),
            authStatus = connectionState.onlineState.authLabel(),
            ipText = endpoint.ip.ifBlank { "Unknown" },
            firmwareText = firmwareLabel(),
            modelText = modelLabel(),
            primaryCountLabel = KIND.primaryCountLabel,
            primaryCountText = primaryCount().takeIf { count -> count > 0 }?.toString() ?: "Unknown",
            featuresText = featureLabel(),
            primarySectionTitle = KIND.primarySectionTitle,
            primarySectionPlaceholder = menuSections.primaryText(
                emptyText = KIND.primarySectionPlaceholder
            ),
            secondarySectionTitle = KIND.secondarySectionTitle,
            secondarySectionPlaceholder = menuSections.secondaryText(
                emptyText = KIND.secondarySectionPlaceholder
            )
        )
    }

    private fun DeviceSnapshot.primaryCount(): Int {
        return when (KIND) {
            DeviceRootKind.DOSING -> limits.dosingChannelCount
            DeviceRootKind.TIMER -> limits.timerChannelCount
            DeviceRootKind.COOLING -> limits.fanChannelCount
        }
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
            when (KIND) {
                DeviceRootKind.DOSING -> if (capabilities.dosing) add("Dosing")
                DeviceRootKind.TIMER -> if (capabilities.standaloneTimer) add("Timer")
                DeviceRootKind.COOLING -> {
                    if (capabilities.cooling) add("Cooling")
                    if (capabilities.fan) add("Fan")
                    if (capabilities.temperature) add("Temperature")
                }
            }

            if (capabilities.timeSync) add("Time sync")
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
        val KIND = DeviceRootKind.COOLING
        const val DEFAULT_TITLE = "Cooling"
    }
}

data class DeviceCoolingRootUiState(
    val title: String = "Cooling",
    val deviceUid: String = "",
    val connectionStatus: String = "Unknown",
    val authStatus: String = "Unknown",
    val ipText: String = "Unknown",
    val firmwareText: String = "Unknown",
    val modelText: String = "Unknown",
    val primaryCountLabel: String = "Channels",
    val primaryCountText: String = "Unknown",
    val featuresText: String = "Unknown",
    val primarySectionTitle: String = "Controls",
    val primarySectionPlaceholder: String = "Controls hazırlanıyor.",
    val secondarySectionTitle: String = "Schedules",
    val secondarySectionPlaceholder: String = "Schedules hazırlanıyor."
)
