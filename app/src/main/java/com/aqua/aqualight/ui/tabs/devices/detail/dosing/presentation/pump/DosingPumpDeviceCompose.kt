package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aqua.aqualight.ui.common.dosing.pump.DosingPumpDevice as SharedDosingPumpDevice

typealias DosingPumpHeadUiState =
    com.aqua.aqualight.ui.common.dosing.pump.DosingPumpHeadUiState

typealias DosingPumpVisualState =
    com.aqua.aqualight.ui.common.dosing.pump.DosingPumpVisualState

/**
 * Feature-owned compatibility facade for the canonical shared Dose Pro renderer.
 *
 * The rendering implementation lives in ui/common/dosing/pump so every device surface uses the
 * same component. Keeping this facade preserves the Dosing feature API and architecture ownership
 * contract without duplicating any Canvas, palette, layout or animation implementation.
 */
@Composable
fun DosingPumpDevice(
    pumpHeads: List<DosingPumpHeadUiState>,
    onPumpClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    SharedDosingPumpDevice(
        pumpHeads = pumpHeads,
        onPumpClick = onPumpClick,
        modifier = modifier
    )
}
