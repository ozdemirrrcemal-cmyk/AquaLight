package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightHeroSnapshot
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightHeroBounds
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightHeroGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightHeroTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightHeroTypography

@Composable
internal fun DeviceLightHero(
    state: DeviceLightHeroSnapshot,
    modifier: Modifier = Modifier
) {
    val typography = aquaLightHeroTypography()
    val content = resolveHeroContent(state)

    AquaDeviceCardSurface(
        modifier = modifier.aspectRatio(AquaLightHeroGeometry.heroAspectRatio),
        contentPadding = AquaDeviceCardGeometry.edgeToEdgeContentPadding
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.device_light_hero_card),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize()
            )
            HeroMetrics(
                content = content,
                typography = typography,
                parentSize = DpSize(maxWidth, maxHeight)
            )
        }
    }
}

@Composable
private fun resolveHeroContent(
    state: DeviceLightHeroSnapshot
): ResolvedDeviceLightHeroContent {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.device_light_hero_value_unavailable)
    val powerNumber = state.estimatedPowerWatts?.let { watts ->
        LocaleFormatter.formatDecimal(context, watts, maximumFractionDigits = 0)
    } ?: unavailable
    val colorTemperatureNumber = state.estimatedColorTemperatureKelvin?.let { kelvin ->
        LocaleFormatter.formatInteger(context, kelvin)
    } ?: unavailable
    return ResolvedDeviceLightHeroContent(
        power = stringResource(R.string.device_light_hero_power_value_format, powerNumber),
        colorTemperature = stringResource(
            R.string.device_light_hero_color_temperature_value_format,
            colorTemperatureNumber
        )
    )
}

@Composable
private fun HeroMetrics(
    content: ResolvedDeviceLightHeroContent,
    typography: AquaLightHeroTypography,
    parentSize: DpSize
) {
    HeroLabel(
        text = content.power,
        style = typography.metricValue,
        bounds = AquaLightHeroGeometry.powerBounds,
        parentSize = parentSize,
        textAlign = TextAlign.Start
    )
    HeroLabel(
        text = content.colorTemperature,
        style = typography.metricValue,
        bounds = AquaLightHeroGeometry.colorTemperatureBounds,
        parentSize = parentSize,
        textAlign = TextAlign.Start
    )
}

@Composable
private fun HeroLabel(
    text: String,
    style: TextStyle,
    bounds: AquaLightHeroBounds,
    parentSize: DpSize,
    textAlign: TextAlign = TextAlign.Start
) {
    Box(
        modifier = Modifier.placeInHero(bounds, parentSize),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = style.copy(textAlign = textAlign),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class ResolvedDeviceLightHeroContent(
    val power: String,
    val colorTemperature: String
)

private fun Modifier.placeInHero(
    bounds: AquaLightHeroBounds,
    parentSize: DpSize
): Modifier = absoluteOffset(
    x = parentSize.width * bounds.left,
    y = parentSize.height * bounds.top
).width(parentSize.width * bounds.width)
    .height(parentSize.height * bounds.height)
