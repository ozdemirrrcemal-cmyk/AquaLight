package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

internal data class DeviceLightCustomCurveActions(
    val onEveryDayClick: () -> Unit,
    val onWeekdayClick: (Int) -> Unit,
    val onGraphTimeClick: (Long) -> Unit,
    val onAddPointClick: () -> Unit,
    val onEditTimeClick: () -> Unit,
    val onDuplicatePointClick: () -> Unit,
    val onDeletePointClick: () -> Unit,
    val onChannelChanged: (DeviceLightCustomChannelId, Int) -> Unit,
    val onChannelStep: (DeviceLightCustomChannelId, Int) -> Unit,
    val onPreviewTimeChanged: (Long) -> Unit,
    val onPreviewClick: () -> Unit,
    val onLoadClick: () -> Unit,
    val onSaveAsClick: () -> Unit,
    val onResetClick: () -> Unit
)
