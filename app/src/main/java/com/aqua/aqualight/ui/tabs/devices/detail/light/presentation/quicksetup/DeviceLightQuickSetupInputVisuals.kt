package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowColors

@Composable
internal fun WaterHeightIllustration() {
    val colors = aquaGuidedFlowColors()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightQuickSetupGeometry.measurementIllustrationHeight)
            .padding(18.dp)
    ) {
        val left = size.width * 0.18f
        val right = size.width * 0.72f
        val top = size.height * 0.12f
        val bottom = size.height * 0.88f
        drawRect(
            color = colors.outline,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            style = Stroke(width = 2.dp.toPx())
        )
        val waterTop = size.height * 0.30f
        drawRect(
            color = colors.accent.copy(alpha = 0.34f),
            topLeft = Offset(left + 2.dp.toPx(), waterTop),
            size = androidx.compose.ui.geometry.Size(
                right - left - 4.dp.toPx(),
                bottom - waterTop - 2.dp.toPx()
            )
        )
        val substrateTop = size.height * 0.78f
        drawRect(
            color = colors.textSecondary.copy(alpha = 0.32f),
            topLeft = Offset(left + 2.dp.toPx(), substrateTop),
            size = androidx.compose.ui.geometry.Size(
                right - left - 4.dp.toPx(),
                bottom - substrateTop - 2.dp.toPx()
            )
        )
        val arrowX = size.width * 0.84f
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX, waterTop),
            end = Offset(arrowX, substrateTop),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX - 8.dp.toPx(), waterTop),
            end = Offset(arrowX + 8.dp.toPx(), waterTop),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX - 8.dp.toPx(), substrateTop),
            end = Offset(arrowX + 8.dp.toPx(), substrateTop),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
internal fun FixtureHeightIllustration() {
    val colors = aquaGuidedFlowColors()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightQuickSetupGeometry.measurementIllustrationHeight)
            .padding(18.dp)
    ) {
        val tankLeft = size.width * 0.13f
        val tankRight = size.width * 0.87f
        val waterTop = size.height * 0.58f
        val tankBottom = size.height * 0.90f
        drawRect(
            color = colors.outline,
            topLeft = Offset(tankLeft, waterTop),
            size = androidx.compose.ui.geometry.Size(tankRight - tankLeft, tankBottom - waterTop),
            style = Stroke(width = 2.dp.toPx())
        )
        drawRect(
            color = colors.accent.copy(alpha = 0.30f),
            topLeft = Offset(tankLeft + 2.dp.toPx(), waterTop + 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(
                tankRight - tankLeft - 4.dp.toPx(),
                tankBottom - waterTop - 4.dp.toPx()
            )
        )
        val fixtureY = size.height * 0.22f
        drawLine(
            color = colors.textPrimary,
            start = Offset(size.width * 0.30f, fixtureY),
            end = Offset(size.width * 0.70f, fixtureY),
            strokeWidth = 8.dp.toPx(),
            cap = StrokeCap.Round
        )
        val arrowX = size.width * 0.78f
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX, fixtureY + 6.dp.toPx()),
            end = Offset(arrowX, waterTop),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX - 7.dp.toPx(), waterTop),
            end = Offset(arrowX + 7.dp.toPx(), waterTop),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
internal fun plantDemandText(demand: AquariumPlantLightDemand): String = stringResource(
    when (demand) {
        AquariumPlantLightDemand.LOW -> R.string.device_light_quick_setup_demand_low
        AquariumPlantLightDemand.MEDIUM -> R.string.device_light_quick_setup_demand_medium
        AquariumPlantLightDemand.HIGH -> R.string.device_light_quick_setup_demand_high
    }
)

@Composable
internal fun substrateText(semantic: AquariumSubstrateSemantic): String = stringResource(
    when (semantic) {
        AquariumSubstrateSemantic.NOT_APPLICABLE -> R.string.device_light_quick_setup_substrate_na
        AquariumSubstrateSemantic.UNKNOWN -> R.string.device_light_quick_setup_substrate_unknown
        AquariumSubstrateSemantic.INERT -> R.string.device_light_quick_setup_substrate_inert
        AquariumSubstrateSemantic.NUTRIENT_BASE ->
            R.string.device_light_quick_setup_substrate_nutrient
        AquariumSubstrateSemantic.ACTIVE_SOIL ->
            R.string.device_light_quick_setup_substrate_active_soil
        AquariumSubstrateSemantic.ADDITIVE -> R.string.device_light_quick_setup_substrate_additive
    }
)


