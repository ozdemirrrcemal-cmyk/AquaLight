package com.aqua.aqualight.ui.common.devicevisual.dosing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/** Geometry shared by the operational Dose Pro facade and reusable identity visuals. */
internal object DosingPumpVisualPrimitives {
    const val normalScale = 1f
    const val pressedScale = 0.965f
    const val hubSizeRatio = 0.42f
    const val indicatorCanvasRatio = 0.82f
    const val compactReferenceHeadSizeDp = 104f

    val deviceOuterShape = RoundedCornerShape(30.dp)
    val deviceInnerShape = RoundedCornerShape(24.dp)
    val deviceDeckShape = RoundedCornerShape(20.dp)
    val pumpOuterShape = RoundedCornerShape(20.dp)
    val pumpFaceShape = RoundedCornerShape(15.dp)

    val deviceShadowElevation = 18.dp
    val pumpShadowElevation = 8.dp
    val hubShadowElevation = 5.dp
    val edgeWidth = 1.dp
    val deviceOuterInset = 7.dp
    val deviceInnerInset = 7.dp
    val deviceDeckInset = 9.dp
    val pumpFrameInset = 7.dp
    val pumpSpacing = 8.dp
    val dosingPro2PumpHeadMaxSize = 104.dp
}

/** Exact shell/deck surfaces used by the existing Dosing operation visual. */
@Composable
internal fun DosingOperationalDeviceShell(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = DosingPumpVisualPrimitives.deviceShadowElevation,
                shape = DosingPumpVisualPrimitives.deviceOuterShape,
                clip = false
            )
            .background(
                brush = DosingPumpVisualPalette.outerShell,
                shape = DosingPumpVisualPrimitives.deviceOuterShape
            )
            .border(
                width = DosingPumpVisualPrimitives.edgeWidth,
                color = DosingPumpVisualPalette.outerEdge,
                shape = DosingPumpVisualPrimitives.deviceOuterShape
            )
            .padding(DosingPumpVisualPrimitives.deviceOuterInset)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = DosingPumpVisualPalette.innerShell,
                    shape = DosingPumpVisualPrimitives.deviceInnerShape
                )
                .border(
                    width = DosingPumpVisualPrimitives.edgeWidth,
                    color = DosingPumpVisualPalette.innerEdge,
                    shape = DosingPumpVisualPrimitives.deviceInnerShape
                )
                .padding(DosingPumpVisualPrimitives.deviceInnerInset),
            content = content
        )
    }
}
