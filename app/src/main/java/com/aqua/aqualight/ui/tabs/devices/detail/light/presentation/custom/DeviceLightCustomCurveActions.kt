package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

internal data class DeviceLightCustomCurveActions(
    val onWeekdayClick: (Int) -> Unit,
    val onGraphPointClick: (Long) -> Unit,
    val onGraphPointLongClick: (Long) -> Unit,
    val onPlayheadChanged: (Long) -> Unit,
    val onPlayheadChangeFinished: () -> Unit,
    val onPlayheadTimeClick: () -> Unit,
    val onChannelChanged: (DeviceLightCustomChannelId, Int) -> Unit,
    val onPreviewClick: () -> Unit,
    val onLoadClick: () -> Unit,
    val onSaveAsClick: () -> Unit
)
