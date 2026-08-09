@file:Suppress("FunctionNaming", "MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * The physical device shell and every channel card are rendered from one exact catalog-derived
 * pump/channel count. Unsupported or inconsistent identities fail closed instead of guessing.
 */
@Composable
internal fun DeviceDosingCatalogScreen(
    pumpCount: Int,
    channels: List<DosingChannelCardUiState>,
    modifier: Modifier = Modifier,
    pumpStates: List<DosingPumpVisualState> = emptyList(),
    onPumpClick: (Int) -> Unit = {}
) {
    val exactPumpCount = exactDosingPumpCountOrNull(pumpCount)
    if (exactPumpCount == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }
    val exactChannels = exactDosingChannelsOrEmpty(exactPumpCount, channels)

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
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                val maximumDeviceWidth = if (exactPumpCount == DOSING_PRO_2_PUMP_COUNT) {
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

        if (exactChannels.isNotEmpty()) {
            item(key = DEVICE_TO_CARDS_SPACER_KEY) {
                Spacer(modifier = Modifier.height(DEVICE_TO_CARDS_EXTRA_SPACING))
            }
        }

        items(
            items = exactChannels,
            key = DosingChannelCardUiState::slotId
        ) { channel ->
            DosingChannelCard(
                state = channel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

internal fun exactDosingPumpCountOrNull(pumpCount: Int): Int? = when (pumpCount) {
    DOSING_PRO_2_PUMP_COUNT -> DOSING_PRO_2_PUMP_COUNT
    DOSING_PRO_4_PUMP_COUNT -> DOSING_PRO_4_PUMP_COUNT
    else -> null
}

internal fun exactDosingChannelsOrEmpty(
    pumpCount: Int,
    channels: List<DosingChannelCardUiState>
): List<DosingChannelCardUiState> {
    val expectedPositions = (1..pumpCount).toList()
    val positions = channels.map(DosingChannelCardUiState::channelNumber)
    val uniqueSlotIds = channels.map(DosingChannelCardUiState::slotId).toSet().size == channels.size
    val uniqueWireKeys = channels.map(DosingChannelCardUiState::wireKey).toSet().size == channels.size
    return channels.takeIf {
        channels.size == pumpCount &&
            positions == expectedPositions &&
            uniqueSlotIds &&
            uniqueWireKeys
    }.orEmpty()
}

private const val DEVICE_ITEM_KEY = "dosing-device"
private const val DEVICE_TO_CARDS_SPACER_KEY = "dosing-device-card-gap"
private const val DOSING_PRO_2_PUMP_COUNT = 2
private const val DOSING_PRO_4_PUMP_COUNT = 4
private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SCREEN_TOP_PADDING = 12.dp
private val SCREEN_BOTTOM_PADDING = 24.dp
private val CHANNEL_CARD_SPACING = 10.dp
private val DEVICE_TO_CARDS_EXTRA_SPACING = 4.dp
private val DOSING_PRO_2_MAX_WIDTH = 360.dp
private val DOSING_PRO_4_MAX_WIDTH = 760.dp
