package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R

/**
 * Shared visual shell for every Cool Pro catalog variant (1F, 2F and 3F).
 *
 * All controls intentionally render as display-only placeholders in this first visual stage. The
 * device-specific command and telemetry wiring can therefore be added without forking the screen.
 */
@Composable
internal fun DeviceCoolingCatalogScreen(
    state: DeviceCoolingRootUiState,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoolingDashboardPalette.background)
    ) {
        CoolingDashboardHeader(
            isOnline = state.connectionStatusRes == R.string.device_online,
            onBackClick = onBackClick,
            onSettingsClick = onSettingsClick
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SCREEN_HORIZONTAL_PADDING,
                top = SCREEN_TOP_PADDING,
                end = SCREEN_HORIZONTAL_PADDING,
                bottom = SCREEN_BOTTOM_PADDING
            ),
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP)
        ) {
            item(key = "cooling-hero") {
                CoolingAquariumHero()
            }
            item(key = "temperature-chart") {
                CoolingTemperatureChart()
            }
            item(key = "summary-cards") {
                CoolingSummaryCards(
                    isOnline = state.connectionStatusRes == R.string.device_online
                )
            }
            item(key = "profile-and-manual") {
                CoolingProfileAndManualControls()
            }
        }
    }
}

@Composable
private fun CoolingSummaryCards(isOnline: Boolean) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= FOUR_CARD_MIN_WIDTH) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CARD_GAP)
            ) {
                CoolingFanGaugeCard(
                    modifier = Modifier
                        .weight(FAN_GAUGE_WEIGHT)
                        .height(SUMMARY_CARD_HEIGHT)
                )
                CoolingFanModeCard(
                    modifier = Modifier
                        .weight(FAN_MODE_WEIGHT)
                        .height(SUMMARY_CARD_HEIGHT)
                )
                CoolingPowerCard(
                    modifier = Modifier
                        .weight(POWER_WEIGHT)
                        .height(SUMMARY_CARD_HEIGHT)
                )
                CoolingStatusCard(
                    isOnline = isOnline,
                    modifier = Modifier
                        .weight(STATUS_WEIGHT)
                        .height(SUMMARY_CARD_HEIGHT)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CARD_GAP)
                ) {
                    CoolingFanGaugeCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(SUMMARY_CARD_HEIGHT)
                    )
                    CoolingFanModeCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(SUMMARY_CARD_HEIGHT)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CARD_GAP)
                ) {
                    CoolingPowerCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(SUMMARY_CARD_HEIGHT)
                    )
                    CoolingStatusCard(
                        isOnline = isOnline,
                        modifier = Modifier
                            .weight(1f)
                            .height(SUMMARY_CARD_HEIGHT)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoolingProfileAndManualControls() {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= BOTTOM_ROW_MIN_WIDTH) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CARD_GAP)
            ) {
                CoolingProfileCard(
                    modifier = Modifier
                        .weight(PROFILE_WEIGHT)
                        .height(BOTTOM_CARD_HEIGHT)
                )
                CoolingManualFanCard(
                    modifier = Modifier
                        .weight(MANUAL_WEIGHT)
                        .height(BOTTOM_CARD_HEIGHT)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                CoolingProfileCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BOTTOM_CARD_HEIGHT)
                )
                CoolingManualFanCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BOTTOM_CARD_HEIGHT)
                )
            }
        }
    }
}

@Preview(widthDp = 512, heightDp = 920, backgroundColor = 0xFF00101C, showBackground = true)
@Composable
private fun DeviceCoolingCatalogScreenPreview() {
    DeviceCoolingCatalogScreen(
        state = DeviceCoolingRootUiState(connectionStatusRes = R.string.device_online),
        onBackClick = {},
        onSettingsClick = {}
    )
}

private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SCREEN_TOP_PADDING = 12.dp
private val SCREEN_BOTTOM_PADDING = 28.dp
private val SECTION_GAP = 10.dp
private val CARD_GAP = 8.dp
private val SUMMARY_CARD_HEIGHT = 176.dp
private val BOTTOM_CARD_HEIGHT = 106.dp
private val FOUR_CARD_MIN_WIDTH = 456.dp
private val BOTTOM_ROW_MIN_WIDTH = 440.dp
private const val FAN_GAUGE_WEIGHT = 0.96f
private const val FAN_MODE_WEIGHT = 1.02f
private const val POWER_WEIGHT = 0.9f
private const val STATUS_WEIGHT = 1.08f
private const val PROFILE_WEIGHT = 1.08f
private const val MANUAL_WEIGHT = 1f
