package com.aqua.aqualight.ui.common.devicevisual.dosing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * Compact representation of one physical Dose Pro pump head.
 *
 * This deliberately has its own small-size geometry instead of shrinking the operational renderer,
 * so 22–48 dp identity placements keep the metal frame, face and hub aligned and legible.
 */
@Composable
fun DosingPumpHeadMarker(
    modifier: Modifier = Modifier,
    visualState: DosingPumpVisualState? = null
) {
    BoxWithConstraints(
        modifier = modifier
            .shadow(
                elevation = MARKER_SHADOW_ELEVATION,
                shape = MARKER_OUTER_SHAPE,
                clip = false
            )
            .clip(MARKER_OUTER_SHAPE)
            .background(brush = DosingPumpPalette.pumpFrame)
            .padding(MARKER_FRAME_INSET),
        contentAlignment = Alignment.Center
    ) {
        val hubSize = maxWidth * MARKER_HUB_SIZE_RATIO
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = DosingPumpPalette.pumpFace,
                    shape = MARKER_FACE_SHAPE
                )
                .border(
                    width = MARKER_EDGE_WIDTH,
                    color = DosingPumpPalette.faceEdge,
                    shape = MARKER_FACE_SHAPE
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(hubSize)
                    .background(brush = DosingPumpPalette.hub, shape = CircleShape)
                    .border(
                        width = MARKER_EDGE_WIDTH,
                        color = DosingPumpPalette.hubEdge,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (visualState != null) {
                    Canvas(modifier = Modifier.size(hubSize * MARKER_INDICATOR_SIZE_RATIO)) {
                        drawPumpIndicator(visualState)
                    }
                }
            }
        }
    }
}

private const val MARKER_OUTER_CORNER_RADIUS_DP = 7
private const val MARKER_FACE_CORNER_RADIUS_DP = 5
private const val MARKER_FRAME_INSET_DP = 2
private const val MARKER_EDGE_WIDTH_DP = 1
private const val MARKER_SHADOW_ELEVATION_DP = 1
private const val MARKER_HUB_SIZE_RATIO = 0.44f
private const val MARKER_INDICATOR_SIZE_RATIO = 0.78f
private val MARKER_OUTER_SHAPE = RoundedCornerShape(MARKER_OUTER_CORNER_RADIUS_DP.dp)
private val MARKER_FACE_SHAPE = RoundedCornerShape(MARKER_FACE_CORNER_RADIUS_DP.dp)
private val MARKER_FRAME_INSET = MARKER_FRAME_INSET_DP.dp
private val MARKER_EDGE_WIDTH = MARKER_EDGE_WIDTH_DP.dp
private val MARKER_SHADOW_ELEVATION = MARKER_SHADOW_ELEVATION_DP.dp
