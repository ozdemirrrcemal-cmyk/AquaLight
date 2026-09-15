package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.control.DeviceLightHeroSnapshot
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.light.AquaLightHeroBounds
import com.aqua.aqualight.ui.common.light.AquaLightHeroColors
import com.aqua.aqualight.ui.common.light.AquaLightHeroGeometry
import com.aqua.aqualight.ui.common.light.AquaLightHeroTypography
import com.aqua.aqualight.ui.common.light.aquaLightHeroColors
import com.aqua.aqualight.ui.common.light.aquaLightHeroTypography

@Composable
internal fun DeviceLightHero(
    state: DeviceLightHeroSnapshot,
    modifier: Modifier = Modifier
) {
    val presentation = state.toHeroPresentation()
    val colors = aquaLightHeroColors()
    val typography = aquaLightHeroTypography(colors)
    val content = resolveHeroContent(presentation)

    AquaDeviceCardSurface(
        modifier = modifier
            .aspectRatio(AquaLightHeroGeometry.heroAspectRatio)
            .clearAndSetSemantics {
                contentDescription = content.accessibilityText
            },
        contentPadding = AquaDeviceCardGeometry.edgeToEdgeContentPadding
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.device_light_hero_card),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize()
            )
            val parentSize = DpSize(maxWidth, maxHeight)
            HeroPrimaryCopy(
                content = content,
                typography = typography,
                parentSize = parentSize
            )
            HeroHealthPill(
                text = content.health,
                tone = presentation.healthTone,
                colors = colors,
                typography = typography,
                parentSize = parentSize
            )
            HeroMetrics(
                content = content,
                typography = typography,
                parentSize = parentSize
            )
        }
    }
}

@Composable
private fun resolveHeroContent(
    presentation: DeviceLightHeroPresentation
): ResolvedDeviceLightHeroContent {
    val context = LocalContext.current
    val title = stringResource(presentation.titleRes)
    val modeOutput = stringResource(
        R.string.device_light_hero_mode_output_format,
        stringResource(presentation.modeRes),
        stringResource(presentation.outputRes)
    )
    val health = stringResource(presentation.healthRes)
    val unavailable = stringResource(R.string.device_light_hero_value_unavailable)
    val powerNumber = presentation.estimatedPowerWatts?.let { watts ->
        LocaleFormatter.formatDecimal(context, watts, maximumFractionDigits = 0)
    } ?: unavailable
    val colorTemperatureNumber = presentation.estimatedColorTemperatureKelvin?.let { kelvin ->
        LocaleFormatter.formatInteger(context, kelvin)
    } ?: unavailable
    val power = stringResource(R.string.device_light_hero_power_value_format, powerNumber)
    val colorTemperature = stringResource(
        R.string.device_light_hero_color_temperature_value_format,
        colorTemperatureNumber
    )
    return ResolvedDeviceLightHeroContent(
        title = title,
        modeOutput = modeOutput,
        health = health,
        estimated = stringResource(R.string.device_light_hero_estimated),
        power = power,
        colorTemperature = colorTemperature,
        accessibilityText = stringResource(
            R.string.device_light_hero_content_description,
            title,
            modeOutput,
            health,
            power,
            colorTemperature
        )
    )
}

@Composable
private fun HeroPrimaryCopy(
    content: ResolvedDeviceLightHeroContent,
    typography: AquaLightHeroTypography,
    parentSize: DpSize
) {
    HeroLabel(
        text = content.title,
        style = typography.title,
        bounds = AquaLightHeroGeometry.titleBounds,
        parentSize = parentSize
    )
    HeroLabel(
        text = content.modeOutput,
        style = typography.subtitle,
        bounds = AquaLightHeroGeometry.subtitleBounds,
        parentSize = parentSize
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
        text = content.estimated,
        style = typography.metricCaption,
        bounds = AquaLightHeroGeometry.estimatedBounds,
        parentSize = parentSize,
        textAlign = TextAlign.Center
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

@Composable
private fun HeroHealthPill(
    text: String,
    tone: DeviceLightHeroHealthTone,
    colors: AquaLightHeroColors,
    typography: AquaLightHeroTypography,
    parentSize: DpSize
) {
    val toneStyle = tone.toPillStyle(colors)
    Row(
        modifier = Modifier
            .placeInHero(AquaLightHeroGeometry.statusBounds, parentSize)
            .clip(CircleShape)
            .background(toneStyle.surface)
            .border(
                width = AquaLightHeroGeometry.heroOutlineWidth,
                color = toneStyle.accent,
                shape = CircleShape
            )
            .padding(horizontal = AquaLightHeroGeometry.statusHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AquaLightHeroGeometry.statusIconSize)
                .clip(CircleShape)
                .background(toneStyle.accent),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(toneStyle.iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconContent),
                modifier = Modifier.size(AquaLightHeroGeometry.statusIconGlyphSize)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = AquaLightHeroGeometry.statusContentGap)
                .weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicText(
                text = text,
                style = typography.status.copy(color = toneStyle.accent),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class DeviceLightHeroPillStyle(
    val accent: Color,
    val surface: Color,
    @DrawableRes val iconRes: Int
)

private data class ResolvedDeviceLightHeroContent(
    val title: String,
    val modeOutput: String,
    val health: String,
    val estimated: String,
    val power: String,
    val colorTemperature: String,
    val accessibilityText: String
)

private fun DeviceLightHeroHealthTone.toPillStyle(
    colors: AquaLightHeroColors
): DeviceLightHeroPillStyle = when (this) {
    DeviceLightHeroHealthTone.HEALTHY -> DeviceLightHeroPillStyle(
        accent = colors.healthyAccent,
        surface = colors.healthySurface,
        iconRes = R.drawable.ic_check_24
    )
    DeviceLightHeroHealthTone.ATTENTION -> DeviceLightHeroPillStyle(
        accent = colors.attentionAccent,
        surface = colors.neutralSurface,
        iconRes = R.drawable.ic_warning
    )
    DeviceLightHeroHealthTone.UNAVAILABLE -> DeviceLightHeroPillStyle(
        accent = colors.neutralAccent,
        surface = colors.neutralSurface,
        iconRes = R.drawable.ic_info
    )
}

private fun Modifier.placeInHero(
    bounds: AquaLightHeroBounds,
    parentSize: DpSize
): Modifier = absoluteOffset(
    x = parentSize.width * bounds.left,
    y = parentSize.height * bounds.top
).width(parentSize.width * bounds.width)
    .height(parentSize.height * bounds.height)
