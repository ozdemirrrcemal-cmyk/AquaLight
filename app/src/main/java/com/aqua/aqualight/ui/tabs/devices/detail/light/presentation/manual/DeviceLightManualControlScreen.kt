package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightManualAlpha
import com.aqua.aqualight.ui.common.light.AquaLightManualColors
import com.aqua.aqualight.ui.common.light.AquaLightManualGeometry
import com.aqua.aqualight.ui.common.light.AquaLightManualTypographySpec
import com.aqua.aqualight.ui.common.light.aquaLightManualColors

@Composable
internal fun DeviceLightManualControlScreen(
    state: DeviceLightManualControlUiState,
    actions: DeviceLightManualControlActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightManualColors()
    val visuals = DeviceLightManualVisuals(
        colors = colors,
        typography = aquaDeviceCardTypography(colors.card)
    )
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color)),
        contentPadding = PaddingValues(
            start = AquaLightManualGeometry.screenHorizontalPadding,
            top = AquaLightManualGeometry.screenTopPadding,
            end = AquaLightManualGeometry.screenHorizontalPadding,
            bottom = AquaLightManualGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaLightManualGeometry.sectionGap)
    ) {
        item(key = "manual-controls") {
            ManualControlCard(state, actions, visuals)
        }
        state.protection?.let { protection ->
            item(key = "manual-protection") {
                ManualProtectionBanner(protection, visuals)
            }
        }
        item(key = "manual-library-actions") {
            ManualLibraryActions(state.contentEnabled, actions, visuals)
        }
        item(key = "manual-power-off") {
            ManualPowerOffAction(state.contentEnabled, actions.onPowerOffClick, visuals)
        }
        item(key = "manual-protection-information") {
            ManualProtectionInformation(visuals)
        }
    }
}

@Composable
private fun ManualControlCard(
    state: DeviceLightManualControlUiState,
    actions: DeviceLightManualControlActions,
    visuals: DeviceLightManualVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = AquaLightManualGeometry.controlContentHorizontalPadding,
            vertical = AquaLightManualGeometry.controlContentVerticalPadding
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            state.power?.let { power -> ManualPowerGauge(power, visuals) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (state.power == null) {
                            Modifier
                        } else {
                            Modifier.padding(top = AquaLightManualGeometry.powerToChannelsGap)
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(AquaLightManualGeometry.channelRowGap)
            ) {
                state.channels.forEach { channel ->
                    ManualChannelRow(
                        channel = channel,
                        enabled = state.contentEnabled,
                        actions = actions,
                        visuals = visuals
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualPowerGauge(
    power: DeviceLightManualPowerUiState,
    visuals: DeviceLightManualVisuals
) {
    val title = stringResource(R.string.device_light_manual_estimated_power)
    val value = stringResource(R.string.device_light_manual_power_value_format, power.watts)
    val description = stringResource(
        R.string.device_light_manual_power_description,
        title,
        value
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AquaLightManualGeometry.powerGaugeValueGap)
    ) {
        BasicText(
            text = title,
            style = visuals.typography.caption.copy(
                color = visuals.colors.card.secondaryText,
                textAlign = TextAlign.Center
            )
        )
        Box(
            modifier = Modifier
                .size(AquaLightManualGeometry.powerGaugeSize)
                .clearAndSetSemantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            ManualPowerGaugeArc(power.ratio, visuals.colors)
            BasicText(
                text = value,
                style = visuals.typography.title.copy(
                    color = visuals.colors.card.primaryText,
                    fontSize = AquaLightManualTypographySpec.powerValueFontSize,
                    lineHeight = AquaLightManualTypographySpec.powerValueLineHeight,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun ManualPowerGaugeArc(ratio: Float, colors: AquaLightManualColors) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = AquaLightManualGeometry.powerGaugeTrackWidth.toPx()
        drawArc(
            color = colors.card.mediaOutline.copy(alpha = AquaLightManualAlpha.gaugeTrack),
            startAngle = GAUGE_START_ANGLE,
            sweepAngle = FULL_ARC_DEGREES,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = colors.card.primaryText,
            startAngle = GAUGE_START_ANGLE,
            sweepAngle = FULL_ARC_DEGREES * ratio,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Immutable
internal data class DeviceLightManualVisuals(
    val colors: AquaLightManualColors,
    val typography: AquaDeviceCardTypography
)

private const val GAUGE_START_ANGLE = -90f
private const val FULL_ARC_DEGREES = 360f
