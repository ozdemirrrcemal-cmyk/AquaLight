package com.aqua.aqualight.ui.tabs.devices.detail.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.ui.common.devicepresence.DevicePresencePresentationMapper
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootKind
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceTimerRootViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DevicesRepositoryProvider.get(application)
    private val _uiState = MutableStateFlow(DeviceTimerRootUiState())
    val uiState: StateFlow<DeviceTimerRootUiState> = _uiState.asStateFlow()

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

    private fun emptyState(title: String, deviceUid: String): DeviceTimerRootUiState =
        DeviceTimerRootUiState(
            title = title,
            deviceUid = deviceUid,
            connectionStatus = "Offline",
            accessStatus = "Unavailable",
            primaryCountLabel = KIND.primaryCountLabel,
            primarySectionTitle = KIND.primarySectionTitle,
            primarySectionPlaceholder = KIND.primarySectionPlaceholder,
            secondarySectionTitle = KIND.secondarySectionTitle,
            secondarySectionPlaceholder = KIND.secondarySectionPlaceholder
        )

    private fun DeviceSnapshot.toRootUiState(fallbackTitle: String): DeviceTimerRootUiState {
        val titleText = product.displayName
            .ifBlank { product.model }
            .ifBlank { fallbackTitle }
            .ifBlank { DEFAULT_TITLE }
        val menuSections = DeviceRootMenuMapper.overview(kind = KIND, snapshot = this)
        val state = connectionState.onlineState
        return DeviceTimerRootUiState(
            title = titleText,
            deviceUid = deviceUid.value,
            connectionStatus = DevicePresencePresentationMapper.availabilityLabel(state),
            accessStatus = DevicePresencePresentationMapper.accessLabel(state),
            ipText = endpoint.ip.ifBlank { "Unknown" },
            firmwareText = firmwareLabel(),
            modelText = modelLabel(),
            primaryCountLabel = KIND.primaryCountLabel,
            primaryCountText = limits.timerChannelCount.takeIf { count -> count > 0 }?.toString() ?: "Unknown",
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
            if (capabilities.standaloneTimer) add("Timer")
            if (capabilities.timeSync) add("Time sync")
            if (capabilities.ota) add("OTA")
            supportedFeatures.filter { feature -> feature.isNotBlank() }.forEach { feature -> add(feature) }
            supportedScreens.filter { screen -> screen.isNotBlank() }.forEach { screen -> add(screen) }
        }
        return labels.distinct().joinToString(separator = ", ").ifBlank { "Unknown" }
    }

    private companion object {
        val KIND = DeviceRootKind.TIMER
        const val DEFAULT_TITLE = "Timer"
    }
}

data class DeviceTimerRootUiState(
    val title: String = "Timer",
    val deviceUid: String = "",
    val connectionStatus: String = "Offline",
    val accessStatus: String = "Unavailable",
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
