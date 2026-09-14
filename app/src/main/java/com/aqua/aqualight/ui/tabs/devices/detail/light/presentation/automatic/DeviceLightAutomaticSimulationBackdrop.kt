package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.aqua.aqualight.R

@Composable
internal fun DeviceLightAutomaticSimulationBackdrop(
    complete: Boolean,
    sceneColor: Color,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(visuals.colors.card.mediaSurface)
    ) {
        SimulationAquariumImage(complete, sceneColor)
        Box(
            Modifier
                .fillMaxSize()
                .background(simulationScrim(visuals))
        )
    }
}

@Composable
private fun SimulationAquariumImage(complete: Boolean, sceneColor: Color) {
    Image(
        painter = painterResource(R.drawable.device_light_hero_card),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(
            horizontalBias = DeviceLightAutomaticEditorGeometry.simulationImageHorizontalBias,
            verticalBias = CENTER_IMAGE_BIAS
        ),
        alpha = if (complete) {
            DeviceLightAutomaticEditorAlpha.imageActive
        } else {
            DeviceLightAutomaticEditorAlpha.imageInactive
        },
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(DeviceLightAutomaticEditorGeometry.simulationImageWidthFraction)
    )
    if (complete) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(DeviceLightAutomaticEditorGeometry.simulationImageWidthFraction)
                .background(
                    sceneColor.copy(alpha = DeviceLightAutomaticEditorAlpha.imageColorOverlay)
                )
        )
    }
}

private fun simulationScrim(visuals: DeviceLightAutomaticEditorVisuals): Brush =
    Brush.horizontalGradient(
        listOf(
            visuals.colors.card.mediaSurface.copy(
                alpha = DeviceLightAutomaticEditorAlpha.heroLeftScrim
            ),
            visuals.colors.card.mediaSurface.copy(
                alpha = DeviceLightAutomaticEditorAlpha.heroCenterScrim
            ),
            visuals.colors.card.mediaSurface.copy(
                alpha = DeviceLightAutomaticEditorAlpha.heroDialScrim
            ),
            visuals.colors.card.mediaSurface.copy(
                alpha = DeviceLightAutomaticEditorAlpha.heroEdgeScrim
            )
        )
    )

private const val CENTER_IMAGE_BIAS = 0f
