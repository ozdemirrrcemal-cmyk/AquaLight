package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import com.aqua.aqualight.ui.common.dosing.AquaDosingPumpGeometry

/**
 * Compact rendering of the black inner pump face used by the dosing device artwork.
 * The caller owns the footprint so card markers can preserve their existing size.
 */
@Composable
internal fun DosingPumpFaceIcon(
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .background(
                brush = DosingPumpPalette.pumpFace,
                shape = ICON_FACE_SHAPE
            )
            .border(
                width = ICON_EDGE_WIDTH,
                color = DosingPumpPalette.faceEdge,
                shape = ICON_FACE_SHAPE
            ),
        contentAlignment = Alignment.Center
    ) {
        val hubSize = maxWidth * HUB_SIZE_RATIO
        Box(
            modifier = Modifier
                .size(hubSize)
                .shadow(
                    elevation = HUB_SHADOW_ELEVATION,
                    shape = CircleShape,
                    clip = false
                )
                .background(
                    brush = DosingPumpPalette.hub,
                    shape = CircleShape
                )
                .border(
                    width = ICON_EDGE_WIDTH,
                    color = DosingPumpPalette.hubEdge,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.size(hubSize * INDICATOR_CANVAS_RATIO)
            ) {
                drawPumpIndicator(DosingPumpVisualState.IDLE)
            }
        }
    }
}

private const val HUB_SIZE_RATIO = 0.42f
private const val INDICATOR_CANVAS_RATIO = 0.82f
private val ICON_FACE_SHAPE = AquaDosingPumpGeometry.pumpFaceShape
private val HUB_SHADOW_ELEVATION = AquaDosingPumpGeometry.hubShadowElevation
private val ICON_EDGE_WIDTH = AquaDosingPumpGeometry.deviceEdgeWidth
