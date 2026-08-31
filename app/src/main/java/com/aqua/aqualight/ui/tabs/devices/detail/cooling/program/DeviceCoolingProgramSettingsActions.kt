package com.aqua.aqualight.ui.tabs.devices.detail.cooling.program

/** Event boundary between the program editor and its Fragment/ViewModel host. */
internal data class DeviceCoolingProgramSettingsActions(
    val onSlotClick: (Int) -> Unit,
    val onAddSlot: () -> Unit,
    val onDeleteSlot: (Int) -> Unit,
    val onStartTimeClick: (Int) -> Unit,
    val onEndTimeClick: (Int) -> Unit,
    val onFanLimitChange: (Int, Int) -> Unit
)
