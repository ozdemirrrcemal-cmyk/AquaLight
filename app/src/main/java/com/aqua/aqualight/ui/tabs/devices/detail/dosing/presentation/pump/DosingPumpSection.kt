package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.ui.common.devicevisual.dosing.DosingPumpDevice
import com.aqua.aqualight.ui.common.devicevisual.dosing.DosingPumpHeadUiState
import com.aqua.aqualight.ui.common.devicevisual.dosing.DosingPumpVisualState

@Composable
internal fun DosingPumpSection(
    pumpCount: Int,
    pumpHeads: List<DosingPumpHeadUiState>,
    onPumpClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val maximumDeviceWidth = if (pumpCount == DOSING_PRO_2_PUMP_COUNT) {
            DOSING_PRO_2_MAX_WIDTH
        } else {
            DOSING_PRO_4_MAX_WIDTH
        }
        val resolvedDeviceWidth = minOf(maxWidth, maximumDeviceWidth)
        DosingPumpDevice(
            pumpHeads = pumpHeads,
            onPumpClick = onPumpClick,
            modifier = Modifier.width(resolvedDeviceWidth)
        )
    }
}

@Composable
internal fun DosingSelectedPumpSection(
    pumpCount: Int,
    selectedChannelNumber: Int,
    modifier: Modifier = Modifier
) {
    val exactPumpCount = exactDosingPumpCountOrNull(pumpCount) ?: return
    val pumpHeads = List(exactPumpCount) { index ->
        DosingPumpHeadUiState(
            channelNumber = index + 1,
            visualState = if (index + 1 == selectedChannelNumber) {
                DosingPumpVisualState.SELECTED
            } else {
                DosingPumpVisualState.IDLE
            }
        )
    }
    DosingPumpSection(
        pumpCount = exactPumpCount,
        pumpHeads = pumpHeads,
        onPumpClick = null,
        modifier = modifier.padding(
            start = SCREEN_HORIZONTAL_PADDING,
            top = SCREEN_TOP_PADDING,
            end = SCREEN_HORIZONTAL_PADDING
        )
    )
}

internal fun exactDosingPumpCountOrNull(pumpCount: Int): Int? = when (pumpCount) {
    DOSING_PRO_2_PUMP_COUNT -> DOSING_PRO_2_PUMP_COUNT
    DOSING_PRO_4_PUMP_COUNT -> DOSING_PRO_4_PUMP_COUNT
    else -> null
}

private const val DOSING_PRO_2_PUMP_COUNT = 2
private const val DOSING_PRO_4_PUMP_COUNT = 4
private const val SCREEN_HORIZONTAL_PADDING_DP = 16
private const val SCREEN_TOP_PADDING_DP = 12
private const val DOSING_PRO_2_MAX_WIDTH_DP = 360
private const val DOSING_PRO_4_MAX_WIDTH_DP = 760
private val SCREEN_HORIZONTAL_PADDING = SCREEN_HORIZONTAL_PADDING_DP.dp
private val SCREEN_TOP_PADDING = SCREEN_TOP_PADDING_DP.dp
private val DOSING_PRO_2_MAX_WIDTH = DOSING_PRO_2_MAX_WIDTH_DP.dp
private val DOSING_PRO_4_MAX_WIDTH = DOSING_PRO_4_MAX_WIDTH_DP.dp
