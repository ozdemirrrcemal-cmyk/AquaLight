package com.aqua.aqualight.ui.tabs.devices.detail.common

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.ui.common.devicepresence.DevicePresencePresentationMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceRootOverviewViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = DevicesRepositoryProvider.get(application)

    private val _uiState = MutableStateFlow(DeviceRootOverviewUiState())
    val uiState: StateFlow<DeviceRootOverviewUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: DeviceUid? = null
    private var observeJob: Job? = null

    fun bind(
        kind: DeviceRootKind,
        deviceUidText: String,
        fallbackTitle: String
    ) {
        if (deviceUidText.isBlank()) {
            _uiState.value = DeviceRootOverviewUiState(
                title = fallbackTitle.ifBlank { kind.defaultTitle },
                deviceUid = "",
                connectionStatus = "Offline",
                primaryCountLabel = kind.primaryCountLabel,
                primarySectionTitle = kind.primarySectionTitle,
                primarySectionPlaceholder = kind.primarySectionPlaceholder,
                secondarySectionTitle = kind.secondarySectionTitle,
                secondarySectionPlaceholder = kind.secondarySectionPlaceholder
            )
            return
        }

        val deviceUid = DeviceUid(deviceUidText)
        if (boundDeviceUid == deviceUid) {
            return
        }

        boundDeviceUid = deviceUid
        observeJob?.cancel()

        _uiState.value = DeviceRootOverviewUiState(
            title = fallbackTitle.ifBlank { kind.defaultTitle },
            deviceUid = deviceUid.value,
            connectionStatus = "Offline",
            primaryCountLabel = kind.primaryCountLabel,
            primarySectionTitle = kind.primarySectionTitle,
            primarySectionPlaceholder = kind.primarySectionPlaceholder,
            secondarySectionTitle = kind.secondarySectionTitle,
            secondarySectionPlaceholder = kind.secondarySectionPlaceholder
        )

        observeJob = viewModelScope.launch {
            repository.observeDevice(deviceUid).collect { snapshot ->
                _uiState.value = snapshot
                    ?.toOverviewState(
                        kind = kind,
                        fallbackTitle = fallbackTitle
                    )
                    ?: DeviceRootOverviewUiState(
                        title = fallbackTitle.ifBlank { kind.defaultTitle },
                        deviceUid = deviceUid.value,
                        connectionStatus = "Offline",
                        primaryCountLabel = kind.primaryCountLabel,
                        primarySectionTitle = kind.primarySectionTitle,
                        primarySectionPlaceholder = kind.primarySectionPlaceholder,
                        secondarySectionTitle = kind.secondarySectionTitle,
                        secondarySectionPlaceholder = kind.secondarySectionPlaceholder
                    )
            }
        }
    }

    private fun DeviceSnapshot.toOverviewState(
        kind: DeviceRootKind,
        fallbackTitle: String
    ): DeviceRootOverviewUiState {
        val productName = product.displayName
            .ifBlank { product.model }
            .ifBlank { fallbackTitle }
            .ifBlank { kind.defaultTitle }

        val menuSections = DeviceRootMenuMapper.overview(
            kind = kind,
            snapshot = this
        )

        return DeviceRootOverviewUiState(
            title = productName,
            deviceUid = deviceUid.value,
            connectionStatus = DevicePresencePresentationMapper.availabilityLabel(connectionState.onlineState),
            ipText = endpoint.ip.ifBlank { "Unknown" },
            firmwareText = firmwareLabel(),
            modelText = modelLabel(),
            primaryCountLabel = kind.primaryCountLabel,
            primaryCountText = primaryCount(kind).takeIf { count -> count > 0 }?.toString() ?: "Unknown",
            featuresText = featureLabel(kind),
            primarySectionTitle = kind.primarySectionTitle,
            primarySectionPlaceholder = menuSections.primaryText(
                emptyText = kind.primarySectionPlaceholder
            ),
            secondarySectionTitle = kind.secondarySectionTitle,
            secondarySectionPlaceholder = menuSections.secondaryText(
                emptyText = kind.secondarySectionPlaceholder
            )
        )
    }

    private fun DeviceSnapshot.primaryCount(kind: DeviceRootKind): Int {
        return when (kind) {
            DeviceRootKind.DOSING -> limits.dosingChannelCount
            DeviceRootKind.TIMER -> limits.timerChannelCount
            DeviceRootKind.COOLING -> limits.fanOutputCount
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

    private fun DeviceSnapshot.featureLabel(kind: DeviceRootKind): String {
        val labels = buildList {
            when (kind) {
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
