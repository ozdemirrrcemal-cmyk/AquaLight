package com.aqua.aqualight.ui.tabs.devices.detail.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceRootOverviewViewModel(
    private val operations: DeviceRootOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceRootOverviewUiState())
    val uiState: StateFlow<DeviceRootOverviewUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: String = ""
    private var observeJob: Job? = null

    fun bind(
        kind: DeviceRootKind,
        deviceUidText: String,
        fallbackTitle: String
    ) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            observeJob?.cancel()
            boundDeviceUid = ""
            _uiState.value = emptyState(kind, fallbackTitle, "")
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        _uiState.value = emptyState(kind, fallbackTitle, deviceUid)
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid).collect { snapshot ->
                _uiState.value = snapshot?.toOverviewState(kind, fallbackTitle)
                    ?: emptyState(kind, fallbackTitle, deviceUid)
            }
        }
    }

    private fun DeviceRootSnapshot.toOverviewState(
        kind: DeviceRootKind,
        fallbackTitle: String
    ): DeviceRootOverviewUiState {
        val menuSections = DeviceRootMenuMapper.overview(kind = kind, snapshot = this)
        val count = DeviceRootPresentationMapper.primaryCount(this, kind)
        return DeviceRootOverviewUiState(
            title = title.ifBlank { fallbackTitle }.ifBlank { kind.defaultTitle },
            deviceUid = deviceUid,
            connectionStatus = DeviceRootPresentationMapper.availabilityLabel(this),
            ipText = ipAddress.ifBlank { "Unknown" },
            firmwareText = firmwareLabel.ifBlank { "Unknown" },
            modelText = modelLabel.ifBlank { "Unknown" },
            primaryCountLabel = kind.primaryCountLabel,
            primaryCountText = count.takeIf { it > 0 }?.toString() ?: "Unknown",
            featuresText = DeviceRootPresentationMapper.overviewFeatureLabel(this, kind),
            primarySectionTitle = kind.primarySectionTitle,
            primarySectionPlaceholder = menuSections.primaryText(kind.primarySectionPlaceholder),
            secondarySectionTitle = kind.secondarySectionTitle,
            secondarySectionPlaceholder = menuSections.secondaryText(kind.secondarySectionPlaceholder)
        )
    }

    private fun emptyState(
        kind: DeviceRootKind,
        fallbackTitle: String,
        deviceUid: String
    ) = DeviceRootOverviewUiState(
        title = fallbackTitle.ifBlank { kind.defaultTitle },
        deviceUid = deviceUid,
        connectionStatus = "Offline",
        primaryCountLabel = kind.primaryCountLabel,
        primarySectionTitle = kind.primarySectionTitle,
        primarySectionPlaceholder = kind.primarySectionPlaceholder,
        secondarySectionTitle = kind.secondarySectionTitle,
        secondarySectionPlaceholder = kind.secondarySectionPlaceholder
    )
}

enum class DeviceRootKind(
    val defaultTitle: String,
    val primaryCountLabel: String,
    val primarySectionTitle: String,
    val primarySectionPlaceholder: String,
    val secondarySectionTitle: String,
    val secondarySectionPlaceholder: String
) {
    DOSING(
        defaultTitle = "Dosing",
        primaryCountLabel = "Dosing channels",
        primarySectionTitle = "Channel controls",
        primarySectionPlaceholder = "Dosing channel controls hazırlanıyor.",
        secondarySectionTitle = "Schedules",
        secondarySectionPlaceholder = "Dosing schedules hazırlanıyor."
    ),
    TIMER(
        defaultTitle = "Timer",
        primaryCountLabel = "Timer channels",
        primarySectionTitle = "Timer channels",
        primarySectionPlaceholder = "Timer channel controls hazırlanıyor.",
        secondarySectionTitle = "Schedules",
        secondarySectionPlaceholder = "Timer schedules hazırlanıyor."
    ),
    COOLING(
        defaultTitle = "Cooling",
        primaryCountLabel = "Fan outputs",
        primarySectionTitle = "Fan control",
        primarySectionPlaceholder = "Fan control hazırlanıyor.",
        secondarySectionTitle = "Temperature automation",
        secondarySectionPlaceholder = "Temperature automation hazırlanıyor."
    )
}

data class DeviceRootOverviewUiState(
    val title: String = "Device",
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
