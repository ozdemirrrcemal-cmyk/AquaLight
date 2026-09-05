package com.aqua.aqualight.ui.common.cooling

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

enum class AquaCoolingDashboardIconKind {
    WATER,
    ROOM,
    HUMIDITY,
    POWER,
    AUTOMATIC,
    MANUAL,
    PROGRAM,
    CHEVRON
}

/**
 * Draws the Cooling icon family from normalized vector paths.
 *
 * Path geometry is declarative data rather than executable coordinate arithmetic. This keeps the
 * icon contract independently reviewable while preserving a caller-controlled physical stroke.
 */
@Composable
fun AquaCoolingDashboardIcon(
    kind: AquaCoolingDashboardIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = AquaCoolingDashboardGeometry.dashboardIconStrokeWidth
) {
    val icon = coolingIconPaths.getValue(kind)
    Canvas(modifier = modifier) {
        withTransform({
            scale(scaleX = size.width, scaleY = size.height, pivot = Offset.Zero)
        }) {
            val style: DrawStyle = if (icon.filled) {
                Fill
            } else {
                Stroke(
                    width = strokeWidth.toPx() / size.minDimension,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            }
            drawPath(path = icon.path, color = tint, style = style)
        }
    }
}

private data class CoolingIconPath(
    val path: Path,
    val filled: Boolean = false
)

private fun coolingIconPath(pathData: String, filled: Boolean = false): CoolingIconPath =
    CoolingIconPath(
        path = PathParser().parsePathString(pathData).toPath(),
        filled = filled
    )

private val coolingIconPaths = mapOf(
    AquaCoolingDashboardIconKind.WATER to coolingIconPath(
        "M.50,.06 C.43,.23 .22,.47 .22,.65 C.22,.85 .34,.95 .50,.95 " +
            "C.67,.95 .78,.84 .78,.65 C.78,.47 .57,.23 .50,.06 Z"
    ),
    AquaCoolingDashboardIconKind.ROOM to coolingIconPath(
        "M.08,.46 L.50,.10 .92,.46 M.18,.39 L.18,.90 L.82,.90 L.82,.39 " +
            "M.43,.90 L.43,.61 .63,.61 .63,.90"
    ),
    AquaCoolingDashboardIconKind.HUMIDITY to coolingIconPath(
        "M.335,.06 C.288,.23 .147,.47 .147,.65 C.147,.85 .228,.95 .335,.95 " +
            "C.449,.95 .523,.84 .523,.65 C.523,.47 .382,.23 .335,.06 Z " +
            "M.79,.55 A.07,.07 0 1,1 .65,.55 A.07,.07 0 1,1 .79,.55 " +
            "M.95,.78 A.07,.07 0 1,1 .81,.78 A.07,.07 0 1,1 .95,.78 " +
            "M.86,.52 L.73,.82"
    ),
    AquaCoolingDashboardIconKind.POWER to coolingIconPath(
        pathData = "M.58,.03 L.20,.57 .47,.57 .39,.97 .82,.40 .54,.40 Z",
        filled = true
    ),
    AquaCoolingDashboardIconKind.AUTOMATIC to coolingIconPath(
        "M.86,.50 A.36,.36 0 1,1 .14,.50 A.36,.36 0 1,1 .86,.50 " +
            "M.37,.68 L.50,.31 .63,.68 M.42,.53 L.58,.53 " +
            "M.047,.289 A.50,.50 0 0,1 .561,.004"
    ),
    AquaCoolingDashboardIconKind.MANUAL to coolingIconPath(
        "M.34,.54 L.34,.22 C.34,.14 .45,.14 .45,.23 L.45,.12 " +
            "C.45,.04 .56,.04 .56,.13 L.56,.21 C.56,.13 .67,.13 .67,.22 " +
            "L.67,.32 C.67,.24 .78,.24 .78,.34 L.78,.61 " +
            "C.78,.82 .66,.94 .49,.94 C.36,.94 .29,.85 .23,.73 L.12,.51 " +
            "C.09,.43 .19,.37 .25,.44 Z"
    ),
    AquaCoolingDashboardIconKind.PROGRAM to coolingIconPath(
        "M.20,.18 H.80 Q.90,.18 .90,.28 V.78 Q.90,.88 .80,.88 H.20 " +
            "Q.10,.88 .10,.78 V.28 Q.10,.18 .20,.18 Z M.10,.38 H.90 " +
            "M.30,.08 V.28 M.69,.08 V.28 " +
            "M.85,.67 A.18,.18 0 1,1 .49,.67 A.18,.18 0 1,1 .85,.67 " +
            "M.67,.67 V.57 M.67,.67 H.76"
    ),
    AquaCoolingDashboardIconKind.CHEVRON to coolingIconPath(
        "M.32,.16 L.68,.50 .32,.84"
    )
)
