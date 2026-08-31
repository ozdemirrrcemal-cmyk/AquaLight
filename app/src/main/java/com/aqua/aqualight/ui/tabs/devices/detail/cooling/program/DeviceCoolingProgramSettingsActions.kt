package com.aqua.aqualight.ui.tabs.devices.detail.cooling.program

/** Interaction callbacks for the Program settings surface. */
internal data class DeviceCoolingProgramSettingsActions(
    val onSlotClick: (Int) -> Unit,
    val onAddSlot: () -> Unit,
    val onDeleteSlot: (Int) -> Unit,
    val onStartTimeClick: (Int) -> Unit,
    val onEndTimeClick: (Int) -> Unit,
    val onFanOnTemperatureClick: (Int) -> Unit,
    val onTargetFanPercentChange: (Int, Int) -> Unit
)
