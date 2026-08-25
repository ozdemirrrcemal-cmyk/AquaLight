package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicevisual.dosing.DosingOperationalDeviceShell
import com.aqua.aqualight.ui.common.devicevisual.dosing.DosingPumpHeadVisual
import com.aqua.aqualight.ui.common.devicevisual.dosing.DosingPumpVisualPrimitives

@Immutable
data class DosingPumpHeadUiState(
    val channelNumber: Int,
    val visualState: DosingPumpVisualState? = null
)

enum class DosingPumpVisualState(
    @StringRes val stateLabelRes: Int
) {
    IDLE(R.string.device_dosing_pump_state_idle),
    SELECTED(R.string.device_dosing_pump_state_selected),
    RUNNING(R.string.device_dosing_pump_state_running),
    ERROR(R.string.device_dosing_pump_state_error)
}

/**
 * Dosing-operation facade. Its public behavior and hose-free appearance remain feature-owned.
 * Shared primitives only remove duplicated visual implementation.
 */
@Composable
fun DosingPumpDevice(
    pumpHeads: List<DosingPumpHeadUiState>,
    onPumpClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    require(
        pumpHeads.size == DOSING_PRO_2_PUMP_COUNT ||
            pumpHeads.size == DOSING_PRO_4_PUMP_COUNT
    )

    DosingOperationalDeviceShell(modifier = modifier) {
        DosingPumpDeck(
            pumpHeads = pumpHeads,
            onPumpClick = onPumpClick
        )
    }
}

@Composable
private fun DosingPumpDeck(
    pumpHeads: List<DosingPumpHeadUiState>,
    onPumpClick: ((Int) -> Unit)?
) {
    val isDosingPro2 = pumpHeads.size == DOSING_PRO_2_PUMP_COUNT

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = DosingPumpPalette.metalDeck,
                shape = DosingPumpVisualPrimitives.deviceDeckShape
            )
            .border(
                width = DosingPumpVisualPrimitives.edgeWidth,
                color = DosingPumpPalette.metalHighlight,
                shape = DosingPumpVisualPrimitives.deviceDeckShape
            )
            .padding(DosingPumpVisualPrimitives.deviceDeckInset),
        contentAlignment = Alignment.Center
    ) {
        val pro2PumpHeadSize = if (isDosingPro2) {
            minOf(
                DosingPumpVisualPrimitives.dosingPro2PumpHeadMaxSize,
                (maxWidth - DosingPumpVisualPrimitives.pumpSpacing) /
                    DOSING_PRO_2_PUMP_COUNT.toFloat()
            )
        } else {
            androidx.compose.ui.unit.Dp.Zero
        }

        Row(
            modifier = if (isDosingPro2) Modifier else Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DosingPumpVisualPrimitives.pumpSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            pumpHeads.forEach { pumpHead ->
                val pumpModifier = if (isDosingPro2) {
                    Modifier.size(pro2PumpHeadSize)
                } else {
                    Modifier
                        .weight(DosingPumpVisualPrimitives.normalScale)
                        .aspectRatio(DosingPumpVisualPrimitives.normalScale)
                }
                val visualState = pumpHead.visualState
                val stateLabel = visualState?.let { state ->
                    stringResource(state.stateLabelRes)
                }
                val contentDescription = stateLabel?.let { label ->
                    stringResource(
                        R.string.device_dosing_pump_channel_content_description,
                        pumpHead.channelNumber,
                        label
                    )
                }

                DosingPumpHeadVisual(
                    visualState = visualState?.toSharedVisualState(),
                    onClick = onPumpClick?.let { click ->
                        { click(pumpHead.channelNumber) }
                    },
                    contentDescriptionText = contentDescription,
                    stateDescriptionText = stateLabel,
                    compactGeometry = false,
                    modifier = pumpModifier
                )
            }
        }
    }
}

private const val DOSING_PRO_2_PUMP_COUNT = 2
private const val DOSING_PRO_4_PUMP_COUNT = 4
