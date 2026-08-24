package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingChannelCard
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingChannelCardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump.DosingPumpHeadUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump.DosingPumpSection
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump.DosingPumpVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump.exactDosingPumpCountOrNull

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
            visualState = pumpStates.getOrNull(index)
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        DosingPumpSection(
            pumpCount = exactPumpCount,
            pumpHeads = pumpHeads,
            onPumpClick = { channelNumber ->
                channels.firstOrNull { channel ->
                    channel.channelNumber == channelNumber
                }?.let { channel ->
                    onChannelClick(channel.slotId)
                }
            },
            modifier = Modifier.padding(
                start = SCREEN_HORIZONTAL_PADDING,
                top = SCREEN_TOP_PADDING,
                end = SCREEN_HORIZONTAL_PADDING
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = SCREEN_HORIZONTAL_PADDING,
                top = CARD_LIST_TOP_PADDING,
                end = SCREEN_HORIZONTAL_PADDING,
                bottom = SCREEN_BOTTOM_PADDING
            ),
            verticalArrangement = Arrangement.spacedBy(CHANNEL_CARD_SPACING),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
}

private const val SCREEN_HORIZONTAL_PADDING_DP = 16
private const val SCREEN_TOP_PADDING_DP = 12
private const val SCREEN_BOTTOM_PADDING_DP = 24
private const val CHANNEL_CARD_SPACING_DP = 10
private const val DEVICE_TO_CARDS_EXTRA_SPACING_DP = 4
private const val CARD_LIST_TOP_PADDING_DP =
    CHANNEL_CARD_SPACING_DP * 2 + DEVICE_TO_CARDS_EXTRA_SPACING_DP
private val SCREEN_HORIZONTAL_PADDING = SCREEN_HORIZONTAL_PADDING_DP.dp
private val SCREEN_TOP_PADDING = SCREEN_TOP_PADDING_DP.dp
private val SCREEN_BOTTOM_PADDING = SCREEN_BOTTOM_PADDING_DP.dp
private val CHANNEL_CARD_SPACING = CHANNEL_CARD_SPACING_DP.dp
private val CARD_LIST_TOP_PADDING = CARD_LIST_TOP_PADDING_DP.dp
