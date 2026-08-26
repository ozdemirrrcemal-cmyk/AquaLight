package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry

/**
 * Keeps the leading dosing summary glyphs on the same horizontal center axis
 * as the channel marker/pump head without changing the glyphs' own size.
 */
@Composable
internal fun DosingLeadingIconSlot(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.width(AquaDeviceCardGeometry.markerSize),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
