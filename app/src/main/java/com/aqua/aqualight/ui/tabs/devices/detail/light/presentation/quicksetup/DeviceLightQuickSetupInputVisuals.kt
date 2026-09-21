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
            .padding(DeviceLightQuickSetupGeometry.illustrationPadding)
    ) {
        val left = size.width * DeviceLightQuickSetupGeometry.waterTankLeftFraction
        val right = size.width * DeviceLightQuickSetupGeometry.waterTankRightFraction
        val top = size.height * DeviceLightQuickSetupGeometry.waterTankTopFraction
        val bottom = size.height * DeviceLightQuickSetupGeometry.waterTankBottomFraction
        val outlineStroke = DeviceLightQuickSetupGeometry.illustrationOutlineStroke.toPx()
        val borderInset = DeviceLightQuickSetupGeometry.illustrationBorderInset.toPx()
        val doubleBorderInset = DeviceLightQuickSetupGeometry.illustrationDoubleBorderInset.toPx()
        drawRect(
            color = colors.outline,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            style = Stroke(width = outlineStroke)
        )
        val waterTop = size.height * DeviceLightQuickSetupGeometry.waterSurfaceFraction
        drawRect(
            color = colors.accent.copy(alpha = DeviceLightQuickSetupGeometry.waterFillAlpha),
            topLeft = Offset(left + borderInset, waterTop),
            size = androidx.compose.ui.geometry.Size(
                right - left - doubleBorderInset,
                bottom - waterTop - borderInset
            )
        )
        val substrateTop = size.height * DeviceLightQuickSetupGeometry.substrateTopFraction
        drawRect(
            color = colors.textSecondary.copy(
                alpha = DeviceLightQuickSetupGeometry.substrateFillAlpha
            ),
            topLeft = Offset(left + borderInset, substrateTop),
            size = androidx.compose.ui.geometry.Size(
                right - left - doubleBorderInset,
                bottom - substrateTop - borderInset
            )
        )
        val arrowX = size.width * DeviceLightQuickSetupGeometry.waterArrowXFraction
        val halfArrow = DeviceLightQuickSetupGeometry.illustrationArrowHalfWidth.toPx()
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX, waterTop),
            end = Offset(arrowX, substrateTop),
            strokeWidth = outlineStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX - halfArrow, waterTop),
            end = Offset(arrowX + halfArrow, waterTop),
            strokeWidth = outlineStroke
        )
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX - halfArrow, substrateTop),
            end = Offset(arrowX + halfArrow, substrateTop),
            strokeWidth = outlineStroke
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
            .padding(DeviceLightQuickSetupGeometry.illustrationPadding)
    ) {
        val tankLeft = size.width * DeviceLightQuickSetupGeometry.fixtureTankLeftFraction
        val tankRight = size.width * DeviceLightQuickSetupGeometry.fixtureTankRightFraction
        val waterTop = size.height * DeviceLightQuickSetupGeometry.fixtureWaterTopFraction
        val tankBottom = size.height * DeviceLightQuickSetupGeometry.fixtureTankBottomFraction
        val outlineStroke = DeviceLightQuickSetupGeometry.illustrationOutlineStroke.toPx()
        val borderInset = DeviceLightQuickSetupGeometry.illustrationBorderInset.toPx()
        val doubleBorderInset = DeviceLightQuickSetupGeometry.illustrationDoubleBorderInset.toPx()
        drawRect(
            color = colors.outline,
            topLeft = Offset(tankLeft, waterTop),
            size = androidx.compose.ui.geometry.Size(
                tankRight - tankLeft,
                tankBottom - waterTop
            ),
            style = Stroke(width = outlineStroke)
        )
        drawRect(
            color = colors.accent.copy(alpha = DeviceLightQuickSetupGeometry.fixtureWaterAlpha),
            topLeft = Offset(tankLeft + borderInset, waterTop + borderInset),
            size = androidx.compose.ui.geometry.Size(
                tankRight - tankLeft - doubleBorderInset,
                tankBottom - waterTop - doubleBorderInset
            )
        )
        val fixtureY = size.height * DeviceLightQuickSetupGeometry.fixtureYFraction
        drawLine(
            color = colors.textPrimary,
            start = Offset(
                size.width * DeviceLightQuickSetupGeometry.fixtureStartFraction,
                fixtureY
            ),
            end = Offset(
                size.width * DeviceLightQuickSetupGeometry.fixtureEndFraction,
                fixtureY
            ),
            strokeWidth = DeviceLightQuickSetupGeometry.fixtureStrokeWidth.toPx(),
            cap = StrokeCap.Round
        )
        val arrowX = size.width * DeviceLightQuickSetupGeometry.fixtureArrowXFraction
        val arrowGap = DeviceLightQuickSetupGeometry.fixtureArrowGap.toPx()
        val halfArrow = DeviceLightQuickSetupGeometry.fixtureArrowHalfWidth.toPx()
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX, fixtureY + arrowGap),
            end = Offset(arrowX, waterTop),
            strokeWidth = outlineStroke
        )
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX - halfArrow, waterTop),
            end = Offset(arrowX + halfArrow, waterTop),
            strokeWidth = outlineStroke
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
