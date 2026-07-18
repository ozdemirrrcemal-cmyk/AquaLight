package com.aqua.aqualight.ui.tabs.devices.detail.common

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.text.AppTextResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceRootOverviewViewModel(
    private val operations: DeviceRootOperations,
    private val textResolver: AppTextResolver
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
            title = title.ifBlank { fallbackTitle }.ifBlank { text(kind.defaultTitleRes) },
            deviceUid = deviceUid,
            connectionStatus = DeviceRootPresentationMapper.availabilityLabel(this, textResolver),
            ipText = ipAddress.ifBlank { text(R.string.device_runtime_unknown) },
            firmwareText = firmwareLabel.ifBlank { text(R.string.device_runtime_unknown) },
            modelText = modelLabel.ifBlank { text(R.string.device_runtime_unknown) },
            primaryCountLabel = text(kind.primaryCountLabelRes),
            primaryCountText = count.takeIf { it > 0 }?.toString()
                ?: text(R.string.device_runtime_unknown),
            featuresText = DeviceRootPresentationMapper.overviewFeatureLabel(
                snapshot = this,
                kind = kind,
                textResolver = textResolver
            ),
            primarySectionTitle = text(kind.primarySectionTitleRes),
            primarySectionPlaceholder = menuSections.primaryText(
                textResolver,
                kind.primarySectionPlaceholderRes
            ),
            secondarySectionTitle = text(kind.secondarySectionTitleRes),
            secondarySectionPlaceholder = menuSections.secondaryText(
                textResolver,
                kind.secondarySectionPlaceholderRes
            )
        )
    }

    private fun emptyState(
        kind: DeviceRootKind,
        fallbackTitle: String,
        deviceUid: String
    ) = DeviceRootOverviewUiState(
        title = fallbackTitle.ifBlank { text(kind.defaultTitleRes) },
        deviceUid = deviceUid,
        connectionStatus = text(R.string.device_runtime_offline),
        ipText = text(R.string.device_runtime_unknown),
        firmwareText = text(R.string.device_runtime_unknown),
        modelText = text(R.string.device_runtime_unknown),
        primaryCountLabel = text(kind.primaryCountLabelRes),
        primaryCountText = text(R.string.device_runtime_unknown),
        featuresText = text(R.string.device_runtime_unknown),
        primarySectionTitle = text(kind.primarySectionTitleRes),
        primarySectionPlaceholder = text(kind.primarySectionPlaceholderRes),
        secondarySectionTitle = text(kind.secondarySectionTitleRes),
        secondarySectionPlaceholder = text(kind.secondarySectionPlaceholderRes)
    )

    private fun text(@StringRes resId: Int): String = textResolver.get(resId)
}

enum class DeviceRootKind(
    @StringRes val defaultTitleRes: Int,
    @StringRes val primaryCountLabelRes: Int,
    @StringRes val primarySectionTitleRes: Int,
    @StringRes val primarySectionPlaceholderRes: Int,
    @StringRes val secondarySectionTitleRes: Int,
    @StringRes val secondarySectionPlaceholderRes: Int
) {
    DOSING(
        defaultTitleRes = R.string.device_root_dosing_title,
        primaryCountLabelRes = R.string.device_root_dosing_count_label,
        primarySectionTitleRes = R.string.device_root_dosing_primary_title,
        primarySectionPlaceholderRes = R.string.device_root_dosing_primary_empty,
        secondarySectionTitleRes = R.string.device_root_dosing_secondary_title,
        secondarySectionPlaceholderRes = R.string.device_root_dosing_secondary_empty
    ),
    TIMER(
        defaultTitleRes = R.string.device_root_timer_title,
        primaryCountLabelRes = R.string.device_root_timer_count_label,
        primarySectionTitleRes = R.string.device_root_timer_primary_title,
        primarySectionPlaceholderRes = R.string.device_root_timer_primary_empty,
        secondarySectionTitleRes = R.string.device_root_timer_secondary_title,
        secondarySectionPlaceholderRes = R.string.device_root_timer_secondary_empty
    ),
    COOLING(
        defaultTitleRes = R.string.device_root_cooling_title,
        primaryCountLabelRes = R.string.device_root_cooling_count_label,
        primarySectionTitleRes = R.string.device_root_cooling_primary_title,
        primarySectionPlaceholderRes = R.string.device_root_cooling_primary_empty,
        secondarySectionTitleRes = R.string.device_root_cooling_secondary_title,
        secondarySectionPlaceholderRes = R.string.device_root_cooling_secondary_empty
    )
}

data class DeviceRootOverviewUiState(
    val title: String = "",
    val deviceUid: String = "",
    val connectionStatus: String = "",
    val ipText: String = "",
    val firmwareText: String = "",
    val modelText: String = "",
    val primaryCountLabel: String = "",
    val primaryCountText: String = "",
    val featuresText: String = "",
    val primarySectionTitle: String = "",
    val primarySectionPlaceholder: String = "",
    val secondarySectionTitle: String = "",
    val secondarySectionPlaceholder: String = ""
)
