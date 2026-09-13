package com.aqua.aqualight.ui.common.light

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Icons shared by the Light dashboard menu surfaces. */
enum class AquaLightDashboardIconKind {
    MANUAL,
    PROGRAM,
    CURVE,
    ADAPTATION,
    SYSTEM,
    CHEVRON
}

@Composable
fun AquaLightDashboardIcon(
    kind: AquaLightDashboardIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = AquaLightDashboardGeometry.dashboardIconStrokeWidth
) {
    val path = lightDashboardIconPaths.getValue(kind)
    Canvas(modifier = modifier) {
        withTransform({
            scale(scaleX = size.width, scaleY = size.height, pivot = Offset.Zero)
        }) {
            drawPath(
                path = path,
                color = tint,
                style = Stroke(
                    width = strokeWidth.toPx() / size.minDimension,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private fun lightDashboardIconPath(pathData: String): Path =
    PathParser().parsePathString(pathData).toPath()

private val lightDashboardIconPaths = mapOf(
    AquaLightDashboardIconKind.MANUAL to lightDashboardIconPath(
        "M.20,.08 V.92 M.10,.34 H.30 V.52 H.10 Z " +
            "M.50,.08 V.92 M.40,.16 H.60 V.34 H.40 Z " +
            "M.80,.08 V.92 M.70,.58 H.90 V.76 H.70 Z"
    ),
    AquaLightDashboardIconKind.PROGRAM to lightDashboardIconPath(
        "M.17,.18 H.83 Q.92,.18 .92,.28 V.84 Q.92,.92 .83,.92 H.17 " +
            "Q.08,.92 .08,.84 V.28 Q.08,.18 .17,.18 Z M.08,.38 H.92 " +
            "M.28,.08 V.28 M.72,.08 V.28"
    ),
    AquaLightDashboardIconKind.CURVE to lightDashboardIconPath(
        "M.05,.70 C.19,.70 .20,.30 .36,.30 S.52,.70 .66,.70 S.79,.25 .95,.25"
    ),
    AquaLightDashboardIconKind.ADAPTATION to lightDashboardIconPath(
        "M.12,.72 C.13,.34 .43,.10 .88,.08 C.86,.52 .62,.82 .27,.82 " +
            "C.19,.82 .14,.78 .12,.72 Z M.13,.91 C.25,.62 .45,.43 .74,.25"
    ),
    AquaLightDashboardIconKind.SYSTEM to lightDashboardIconPath(
        "M.25,.20 H.75 Q.82,.20 .82,.27 V.73 Q.82,.80 .75,.80 H.25 " +
            "Q.18,.80 .18,.73 V.27 Q.18,.20 .25,.20 Z " +
            "M.36,.35 H.64 V.65 H.36 Z " +
            "M.30,.06 V.20 M.50,.06 V.20 M.70,.06 V.20 " +
            "M.30,.80 V.94 M.50,.80 V.94 M.70,.80 V.94 " +
            "M.04,.30 H.18 M.04,.50 H.18 M.04,.70 H.18 " +
            "M.82,.30 H.96 M.82,.50 H.96 M.82,.70 H.96"
    ),
    AquaLightDashboardIconKind.CHEVRON to lightDashboardIconPath(
        "M.32,.16 L.68,.50 .32,.84"
    )
)
