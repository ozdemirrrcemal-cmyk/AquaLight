package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannelDescriptor
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryTarget
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.DeviceLightCommercialErrorMessage

internal enum class DeviceLightLibraryTab {
    MANUAL,
    CUSTOM
}

internal data class DeviceLightLibraryCardPresentation(
    val descriptors: List<DeviceLightLibraryChannelDescriptor>,
    val selectable: Boolean = false
)

internal data class DeviceLightLibraryUiState(
    val deviceUid: String = "",
    val target: DeviceLightLibraryTarget? = null,
    val selectedTab: DeviceLightLibraryTab = DeviceLightLibraryTab.MANUAL,
    val entries: List<DeviceLightLibraryEntry> = emptyList(),
    val initialLoading: Boolean = true,
    val readError: DeviceLightCommercialErrorMessage? = null,
    val connectionVisualState: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE
) {
    val visibleEntries: List<DeviceLightLibraryEntry>
        get() = entries.filter { entry ->
            when (selectedTab) {
                DeviceLightLibraryTab.MANUAL -> entry.kind == DeviceLightLibraryKind.MANUAL
                DeviceLightLibraryTab.CUSTOM -> entry.kind == DeviceLightLibraryKind.CUSTOM
            }
        }

    val hasPresentationSnapshot: Boolean
        get() = target != null


}

internal data class DeviceLightLibraryActions(
    val onTabSelected: (DeviceLightLibraryTab) -> Unit,
    val onEntryClick: (String) -> Unit,
    val onMoreClick: (String) -> Unit,
    val onRetryClick: () -> Unit
)

internal const val DEVICE_LIGHT_LIBRARY_SELECTION_RESULT =
    "device_light_library_selection_result"
internal const val DEVICE_LIGHT_LIBRARY_SELECTION_MANUAL = "MANUAL"
internal const val DEVICE_LIGHT_LIBRARY_SELECTION_CUSTOM = "CUSTOM"
