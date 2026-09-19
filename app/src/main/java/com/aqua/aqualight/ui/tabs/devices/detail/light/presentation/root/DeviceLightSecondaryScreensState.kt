package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightAdaptationSummary
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightSystemSummary

internal data class DeviceLightSecondaryScreensState(
    val enabled: Boolean,
    val adaptation: DeviceLightAdaptationSummary,
    val systemSupported: Boolean,
    val system: DeviceLightSystemSummary?
)
