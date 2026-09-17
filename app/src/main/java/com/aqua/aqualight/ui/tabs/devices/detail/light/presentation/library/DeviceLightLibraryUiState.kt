package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryTarget
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.DeviceLightCommercialErrorMessage

internal enum class DeviceLightLibraryTab {
    MANUAL,
    CUSTOM
}

internal data class DeviceLightLibraryUiState(
    val deviceUid: String = "",
    val target: DeviceLightLibraryTarget? = null,
    val selectedTab: DeviceLightLibraryTab = DeviceLightLibraryTab.MANUAL,
    val entries: List<DeviceLightLibraryEntry> = emptyList(),
    val initialLoading: Boolean = true,
    val readError: DeviceLightCommercialErrorMessage? = null,
    val activeLoadEntryId: String? = null
) {
    val visibleEntries: List<DeviceLightLibraryEntry>
        get() = entries.filter { entry ->
            when (selectedTab) {
                DeviceLightLibraryTab.MANUAL -> entry.kind == DeviceLightLibraryKind.MANUAL
                DeviceLightLibraryTab.CUSTOM -> entry.kind == DeviceLightLibraryKind.CUSTOM
            }
        }

    val showGlobalLoading: Boolean
        get() = activeLoadEntryId != null
}

internal data class DeviceLightLibraryActions(
    val onTabSelected: (DeviceLightLibraryTab) -> Unit,
    val onLoadClick: (String) -> Unit,
    val onMoreClick: (String) -> Unit,
    val onRetryClick: () -> Unit
)
