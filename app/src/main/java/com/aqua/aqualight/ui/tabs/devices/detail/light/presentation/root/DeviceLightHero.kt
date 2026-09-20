package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightHeroSnapshot
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightHeroGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightHeroColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightHeroTypography

@Composable
internal fun DeviceLightHero(
    state: DeviceLightHeroSnapshot,
    channels: List<DeviceLightChannelOutputSnapshot>,
    modifier: Modifier = Modifier
) {
    val lighting = resolveDeviceLightAquariumLighting(
        channels = channels,
        outputActive = state.outputActive
    )
    val powerText = resolveHeroPowerText(state)
    val colors = aquaLightHeroColors()
    val typography = aquaLightHeroTypography(colors)

    AquaDeviceCardSurface(
        modifier = modifier
            .aspectRatio(AquaLightHeroGeometry.heroAspectRatio)
            .clearAndSetSemantics {
                contentDescription = powerText
            },
        contentPadding = AquaDeviceCardGeometry.edgeToEdgeContentPadding
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DeviceLightDynamicAquarium(
                lighting = lighting,
                modifier = Modifier.fillMaxSize()
            )
            BasicText(
                text = powerText,
                style = typography.metricValue.copy(
                    color = colors.primaryText,
                    fontSize = HERO_POWER_FONT_SIZE,
                    lineHeight = HERO_POWER_LINE_HEIGHT,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.72f),
                        offset = Offset(0f, 1f),
                        blurRadius = 4f
                    )
                ),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = HERO_POWER_START_PADDING)
            )
        }
    }
}

@Composable
private fun resolveHeroPowerText(state: DeviceLightHeroSnapshot): String {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.device_light_hero_value_unavailable)
    val powerNumber = state.estimatedPowerWatts?.let { watts ->
        LocaleFormatter.formatDecimal(context, watts, maximumFractionDigits = 0)
    } ?: unavailable
    return stringResource(R.string.device_light_hero_power_value_format, powerNumber)
}

private val HERO_POWER_START_PADDING = 18.dp
private val HERO_POWER_FONT_SIZE = 15.sp
private val HERO_POWER_LINE_HEIGHT = 19.sp
