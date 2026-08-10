package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Final Dose Pro root composition.
 *
 * Catalog/channel integrity is owned by the application layer. This Dosing-only screen chooses the
 * supported physical layout from the catalog-derived pump count and renders the supplied channel
 * presentation state without re-defining catalog validity rules.
 */
@Composable
internal fun DeviceDosingCatalogScreen(
    pumpCount: Int,
    channels: List<DosingChannelCardUiState>,
    onChannelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    pumpStates: List<DosingPumpVisualState> = emptyList()
) {
    val exactPumpCount = exactDosingPumpCountOrNull(pumpCount)
    if (exactPumpCount == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val pumpHeads = List(exactPumpCount) { index ->
        DosingPumpHeadUiState(
            channelNumber = index + 1,
            visualState = pumpStates.getOrElse(index) { DosingPumpVisualState.IDLE }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SCREEN_HORIZONTAL_PADDING,
            top = SCREEN_TOP_PADDING,
            end = SCREEN_HORIZONTAL_PADDING,
            bottom = SCREEN_BOTTOM_PADDING
        ),
        verticalArrangement = Arrangement.spacedBy(CHANNEL_CARD_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = DEVICE_ITEM_KEY) {
            DosingPumpSection(
                pumpCount = exactPumpCount,
                pumpHeads = pumpHeads,
                onPumpClick = { channelNumber ->
                    channels.firstOrNull { channel ->
                        channel.channelNumber == channelNumber
                    }?.let { channel ->
                        onChannelClick(channel.slotId)
                    }
                }
            )
        }

        if (channels.isNotEmpty()) {
            item(key = DEVICE_TO_CARDS_SPACER_KEY) {
                Spacer(modifier = Modifier.height(DEVICE_TO_CARDS_EXTRA_SPACING))
            }
        }

        items(
            items = channels,
            key = DosingChannelCardUiState::slotId
        ) { channel ->
            DosingChannelCard(
                state = channel,
                onClick = { onChannelClick(channel.slotId) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

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

private const val DEVICE_ITEM_KEY = "dosing-device"
private const val DEVICE_TO_CARDS_SPACER_KEY = "dosing-device-card-gap"
private const val DOSING_PRO_2_PUMP_COUNT = 2
private const val DOSING_PRO_4_PUMP_COUNT = 4
private const val SCREEN_HORIZONTAL_PADDING_DP = 16
private const val SCREEN_TOP_PADDING_DP = 12
private const val SCREEN_BOTTOM_PADDING_DP = 24
private const val CHANNEL_CARD_SPACING_DP = 10
private const val DEVICE_TO_CARDS_EXTRA_SPACING_DP = 4
private const val DOSING_PRO_2_MAX_WIDTH_DP = 360
private const val DOSING_PRO_4_MAX_WIDTH_DP = 760
private val SCREEN_HORIZONTAL_PADDING = SCREEN_HORIZONTAL_PADDING_DP.dp
private val SCREEN_TOP_PADDING = SCREEN_TOP_PADDING_DP.dp
private val SCREEN_BOTTOM_PADDING = SCREEN_BOTTOM_PADDING_DP.dp
private val CHANNEL_CARD_SPACING = CHANNEL_CARD_SPACING_DP.dp
private val DEVICE_TO_CARDS_EXTRA_SPACING = DEVICE_TO_CARDS_EXTRA_SPACING_DP.dp
private val DOSING_PRO_2_MAX_WIDTH = DOSING_PRO_2_MAX_WIDTH_DP.dp
private val DOSING_PRO_4_MAX_WIDTH = DOSING_PRO_4_MAX_WIDTH_DP.dp
