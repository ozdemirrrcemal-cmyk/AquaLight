package com.aqua.aqualight.ui.tabs.devices.detail.dosing

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

class DeviceDosingRootViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = DevicesRepositoryProvider.get(application)

    private val _uiState = MutableStateFlow(DeviceDosingRootUiState(title = DEFAULT_TITLE))
    val uiState: StateFlow<DeviceDosingRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: DeviceUid? = null
    private var observeJob: Job? = null

    fun bind(
        deviceUidText: String,
        fallbackTitle: String
    ) {
        if (deviceUidText.isBlank()) {
            observeJob?.cancel()

            _uiState.value = DeviceDosingRootUiState(
                title = fallbackTitle.ifBlank { DEFAULT_TITLE },
                deviceUid = "",
                connectionStatus = "Offline",
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
        repository.connectRuntime(deviceUid)

        _uiState.value = DeviceDosingRootUiState(
            title = fallbackTitle.ifBlank { DEFAULT_TITLE },
            deviceUid = deviceUid.value,
            connectionStatus = "Offline",
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
                        ?: DeviceDosingRootUiState(
                            title = fallbackTitle.ifBlank { DEFAULT_TITLE },
                            deviceUid = deviceUid.value,
                            connectionStatus = "Offline",
                            primaryCountLabel = KIND.primaryCountLabel,
                            primarySectionTitle = KIND.primarySectionTitle,
                            primarySectionPlaceholder = KIND.primarySectionPlaceholder,
                            secondarySectionTitle = KIND.secondarySectionTitle,
                            secondarySectionPlaceholder = KIND.secondarySectionPlaceholder
                        )
                    )
            }
        }
    }

    private fun DeviceSnapshot.toRootUiState(fallbackTitle: String): DeviceDosingRootUiState {
        val productName = product.displayName
            .ifBlank { product.model }
            .ifBlank { fallbackTitle }
            .ifBlank { DEFAULT_TITLE }

        val menuSections = DeviceRootMenuMapper.overview(
            kind = KIND,
            snapshot = this
        )

        return DeviceDosingRootUiState(
            title = productName,
            deviceUid = deviceUid.value,
            connectionStatus = DevicePresencePresentationMapper.availabilityLabel(connectionState.onlineState),
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


    private companion object {
        val KIND = DeviceRootKind.DOSING
        const val DEFAULT_TITLE = "Dosing"
    }
}

data class DeviceDosingRootUiState(
    val title: String = "Dosing",
    val deviceUid: String = "",
    val connectionStatus: String = "Unknown",
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
