package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.ui.common.devicepresence.DevicePresencePresentationMapper
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootKind
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceCoolingRootViewModel(
    private val repository: DevicesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingRootUiState())
    val uiState: StateFlow<DeviceCoolingRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: DeviceUid? = null
    private var observeJob: Job? = null

    fun bind(deviceUidText: String, fallbackTitle: String) {
        if (deviceUidText.isBlank()) {
            observeJob?.cancel()
            _uiState.value = emptyState(fallbackTitle.ifBlank { DEFAULT_TITLE }, "")
            return
        }

        val deviceUid = DeviceUid(deviceUidText)
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        _uiState.value = emptyState(fallbackTitle.ifBlank { DEFAULT_TITLE }, deviceUid.value)

        observeJob = viewModelScope.launch {
            repository.observeDevice(deviceUid).collect { snapshot ->
                _uiState.value = snapshot?.toRootUiState(fallbackTitle)
                    ?: emptyState(fallbackTitle.ifBlank { DEFAULT_TITLE }, deviceUid.value)
            }
        }
    }

    private fun emptyState(title: String, deviceUid: String): DeviceCoolingRootUiState =
        DeviceCoolingRootUiState(
            title = title,
            deviceUid = deviceUid,
            connectionStatus = "Offline",
            primaryCountLabel = KIND.primaryCountLabel,
            primarySectionTitle = KIND.primarySectionTitle,
            primarySectionPlaceholder = KIND.primarySectionPlaceholder,
            secondarySectionTitle = KIND.secondarySectionTitle,
            secondarySectionPlaceholder = KIND.secondarySectionPlaceholder
        )

    private fun DeviceSnapshot.toRootUiState(fallbackTitle: String): DeviceCoolingRootUiState {
        val titleText = product.displayName
            .ifBlank { product.model }
            .ifBlank { fallbackTitle }
            .ifBlank { DEFAULT_TITLE }
        val menuSections = DeviceRootMenuMapper.overview(kind = KIND, snapshot = this)
        val state = connectionState.onlineState
        return DeviceCoolingRootUiState(
            title = titleText,
            deviceUid = deviceUid.value,
            connectionStatus = DevicePresencePresentationMapper.availabilityLabel(state),
            ipText = endpoint.ip.ifBlank { "Unknown" },
            firmwareText = firmwareLabel(),
            modelText = modelLabel(),
            primaryCountLabel = KIND.primaryCountLabel,
            primaryCountText = limits.fanOutputCount.takeIf { count -> count > 0 }?.toString() ?: "Unknown",
            featuresText = featureLabel(),
            primarySectionTitle = KIND.primarySectionTitle,
            primarySectionPlaceholder = menuSections.primaryText(emptyText = KIND.primarySectionPlaceholder),
            secondarySectionTitle = KIND.secondarySectionTitle,
            secondarySectionPlaceholder = menuSections.secondaryText(emptyText = KIND.secondarySectionPlaceholder)
        )
    }

    private fun DeviceSnapshot.firmwareLabel(): String =
        listOf(firmwareVersion.ifBlank { null }, firmwareBuild.ifBlank { null })
            .filterNotNull()
            .joinToString(separator = " / ")
            .ifBlank { "Unknown" }

    private fun DeviceSnapshot.modelLabel(): String =
        listOf(product.model.ifBlank { null }, product.hardwareRevision.ifBlank { null })
            .filterNotNull()
            .joinToString(separator = " / ")
            .ifBlank { "Unknown" }

    private fun DeviceSnapshot.featureLabel(): String {
        val labels = buildList {
            if (capabilities.cooling) add("Cooling")
            if (capabilities.fan) add("Fan")
            if (capabilities.temperature) add("Temperature")
            if (capabilities.timeSync) add("Time sync")
            if (capabilities.ota) add("OTA")
            supportedFeatures.filter { feature -> feature.isNotBlank() }.forEach { feature -> add(feature) }
            supportedScreens.filter { screen -> screen.isNotBlank() }.forEach { screen -> add(screen) }
        }
        return labels.distinct().joinToString(separator = ", ").ifBlank { "Unknown" }
    }

    private companion object {
        val KIND = DeviceRootKind.COOLING
        const val DEFAULT_TITLE = "Cooling"
    }
}

data class DeviceCoolingRootUiState(
    val title: String = "Cooling",
    val deviceUid: String = "",
    val connectionStatus: String = "Offline",
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
