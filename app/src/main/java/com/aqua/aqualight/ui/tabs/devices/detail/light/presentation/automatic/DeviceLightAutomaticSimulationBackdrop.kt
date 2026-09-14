package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    val aquarium = ImageBitmap.imageResource(R.drawable.device_light_hero_card)
    Image(
        painter = BitmapPainter(
            image = aquarium,
            srcOffset = IntOffset(AQUARIUM_CROP_LEFT_PX, AQUARIUM_CROP_TOP_PX),
            srcSize = IntSize(AQUARIUM_CROP_WIDTH_PX, AQUARIUM_CROP_HEIGHT_PX)
        ),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = if (complete) {
            DeviceLightAutomaticEditorAlpha.imageActive
        } else {
            DeviceLightAutomaticEditorAlpha.imageInactive
        },
        modifier = Modifier.fillMaxSize()
    )
    if (complete) {
        Box(
            Modifier
                .fillMaxSize()
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

private const val AQUARIUM_CROP_LEFT_PX = 620
private const val AQUARIUM_CROP_TOP_PX = 100
private const val AQUARIUM_CROP_WIDTH_PX = 990
private const val AQUARIUM_CROP_HEIGHT_PX = 650
