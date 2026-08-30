@file:Suppress("LongMethod", "UnusedPrivateMember")

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingGeometry
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

/**
 * Shared visual shell for every Cool Pro catalog variant (1F, 2F and 3F).
 *
 * All controls intentionally render as display-only placeholders in this first visual stage. The
 * device-specific command and telemetry wiring can therefore be added without forking the screen.
 */
@Composable
internal fun DeviceCoolingCatalogScreen(
    state: DeviceCoolingRootUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .alpha(if (state.contentEnabled) {
                    CONTENT_ENABLED_ALPHA
                } else {
                    CONTENT_DISABLED_ALPHA
                })
                .semantics {
                    if (!state.contentEnabled) disabled()
                },
            contentPadding = PaddingValues(
                start = AquaCoolingGeometry.screenHorizontalPadding,
                top = AquaCoolingGeometry.screenTopPadding,
                end = AquaCoolingGeometry.screenHorizontalPadding,
                bottom = AquaCoolingGeometry.screenBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingGeometry.sectionGap)
        ) {
            item(key = "cooling-hero") {
                CoolingAquariumHero(fanRunning = true)
            }
            item(key = "temperature-chart") {
                CoolingTemperatureChart()
            }
            item(key = "summary-cards") {
                CoolingSummaryCards(
                    isOnline = state.connectionVisualState == DeviceConnectionVisualState.ONLINE
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
        if (maxWidth >= AquaCoolingGeometry.fourCardMinWidth) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AquaCoolingGeometry.cardGap)
            ) {
                CoolingFanGaugeCard(
                    modifier = Modifier
                        .weight(FAN_GAUGE_WEIGHT)
                        .height(AquaCoolingGeometry.summaryCardHeight)
                )
                CoolingFanModeCard(
                    modifier = Modifier
                        .weight(FAN_MODE_WEIGHT)
                        .height(AquaCoolingGeometry.summaryCardHeight)
                )
                CoolingPowerCard(
                    modifier = Modifier
                        .weight(POWER_WEIGHT)
                        .height(AquaCoolingGeometry.summaryCardHeight)
                )
                CoolingStatusCard(
                    isOnline = isOnline,
                    modifier = Modifier
                        .weight(STATUS_WEIGHT)
                        .height(AquaCoolingGeometry.summaryCardHeight)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(AquaCoolingGeometry.cardGap)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AquaCoolingGeometry.cardGap)
                ) {
                    CoolingFanGaugeCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(AquaCoolingGeometry.summaryCardHeight)
                    )
                    CoolingFanModeCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(AquaCoolingGeometry.summaryCardHeight)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AquaCoolingGeometry.cardGap)
                ) {
                    CoolingPowerCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(AquaCoolingGeometry.summaryCardHeight)
                    )
                    CoolingStatusCard(
                        isOnline = isOnline,
                        modifier = Modifier
                            .weight(1f)
                            .height(AquaCoolingGeometry.summaryCardHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoolingProfileAndManualControls() {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= AquaCoolingGeometry.bottomRowMinWidth) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AquaCoolingGeometry.cardGap)
            ) {
                CoolingProfileCard(
                    modifier = Modifier
                        .weight(PROFILE_WEIGHT)
                        .height(AquaCoolingGeometry.bottomCardHeight)
                )
                CoolingManualFanCard(
                    modifier = Modifier
                        .weight(MANUAL_WEIGHT)
                        .height(AquaCoolingGeometry.bottomCardHeight)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(AquaCoolingGeometry.cardGap)) {
                CoolingProfileCard(
                    modifier = Modifier
                        .fillMaxWidth()
                    .height(AquaCoolingGeometry.bottomCardHeight)
                )
                CoolingManualFanCard(
                    modifier = Modifier
                        .fillMaxWidth()
                    .height(AquaCoolingGeometry.bottomCardHeight)
                )
            }
        }
    }
}

@Preview(widthDp = 512, heightDp = 768, backgroundColor = 0xFF00101C, showBackground = true)
@Composable
private fun DeviceCoolingCatalogScreenPreview() {
    DeviceCoolingCatalogScreen(
        state = DeviceCoolingRootUiState(
            title = stringResource(R.string.device_cooling_unknown_device),
            connectionVisualState = DeviceConnectionVisualState.ONLINE,
            contentEnabled = true
        )
    )
}

private const val FAN_GAUGE_WEIGHT = 0.96f
private const val FAN_MODE_WEIGHT = 1.02f
private const val POWER_WEIGHT = 0.9f
private const val STATUS_WEIGHT = 1.08f
private const val PROFILE_WEIGHT = 1.08f
private const val MANUAL_WEIGHT = 1f
private const val CONTENT_ENABLED_ALPHA = 1f
private const val CONTENT_DISABLED_ALPHA = 0.42f
