package com.aqua.aqualight.ui.tabs.devices.detail.cooling.program

/** Event boundary between the program editor and its Fragment/ViewModel host. */
internal data class DeviceCoolingProgramSettingsActions(
    val onSlotClick: (String) -> Unit,
    val onAddSlot: () -> Unit,
    val onDeleteSlot: (String) -> Unit,
    val onStartTimeClick: (String) -> Unit,
    val onEndTimeClick: (String) -> Unit,
    val onFanLimitChange: (String, Int) -> Unit
)
