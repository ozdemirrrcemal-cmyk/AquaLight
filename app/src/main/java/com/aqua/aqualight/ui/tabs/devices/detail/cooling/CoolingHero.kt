@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHeroGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHeroPalette
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Cooling's animated digital-twin hero.
 *
 * No bitmap is used here. The device, clamp, glass, cable and water are rendered as vector Canvas
 * primitives. Water motion is backed by a fixed-timestep 2D height-field solver; fan pressure is a
 * Gaussian force applied beneath the outlet and the outer grid absorbs outgoing waves instead of
 * reflecting them back into the scene.
 */
@Composable
internal fun CoolingHero(
    fanSpeed: Float,
    modifier: Modifier = Modifier
) {
    val speed = fanSpeed.coerceIn(0f, 1f)
    val water = remember { CoolingWaterHeightField(WATER_COLUMNS, WATER_ROWS) }
    var frameNanos by remember { mutableStateOf(0L) }

    LaunchedEffect(water, speed) {
        var previousFrameNanos = 0L
        var accumulatorSeconds = 0f

        while (isActive) {
            val currentFrameNanos = withFrameNanos { it }
            if (previousFrameNanos != 0L) {
                val elapsedSeconds = (
                    (currentFrameNanos - previousFrameNanos).toDouble() / NANOS_PER_SECOND
                    ).toFloat().coerceIn(0f, MAX_FRAME_DELTA_SECONDS)
                accumulatorSeconds += elapsedSeconds
                val timeSeconds = nanosToSeconds(currentFrameNanos)
                val pressurePhase = timeSeconds * (
                    WATER_PHASE_BASE_RADIANS_PER_SECOND +
                        speed * WATER_PHASE_SPEED_RADIANS_PER_SECOND
                    )

                while (accumulatorSeconds >= WATER_FIXED_STEP_SECONDS) {
                    water.step(
                        deltaSeconds = WATER_FIXED_STEP_SECONDS,
                        fanSpeed = speed,
                        pressurePhase = pressurePhase
                    )
                    accumulatorSeconds -= WATER_FIXED_STEP_SECONDS
                }
            }
            previousFrameNanos = currentFrameNanos
            frameNanos = currentFrameNanos
        }
    }

    val timeSeconds = nanosToSeconds(frameNanos)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(AquaCoolingHeroGeometry.aspectRatio)
            .heightIn(
                min = AquaCoolingHeroGeometry.minimumHeight,
                max = AquaCoolingHeroGeometry.maximumHeight
            )
            .clipToBounds()
            .clearAndSetSemantics { }
    ) {
        drawCoolingHeroScene(
            water = water,
            fanSpeed = speed,
            timeSeconds = timeSeconds
        )
    }
}

private fun DrawScope.drawCoolingHeroScene(
    water: CoolingWaterHeightField,
    fanSpeed: Float,
    timeSeconds: Float
) {
    drawHeroAmbientLight(fanSpeed)
    drawWaterPlane(water, fanSpeed)
    drawWaterReflections(water, fanSpeed)
    drawAquariumGlass(water, fanSpeed)
    drawFanReflection(fanSpeed)
    drawClamp()
    drawAirRefraction(fanSpeed, timeSeconds)
    drawFanBody(fanSpeed, timeSeconds)
    drawCableAndProbe()
}

private fun DrawScope.drawHeroAmbientLight(fanSpeed: Float) {
    val w = size.width
    val h = size.height
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                AquaCoolingHeroPalette.accent.copy(alpha = 0.055f + fanSpeed * 0.04f),
                Color.Transparent
            ),
            center = Offset(w * 0.54f, h * 0.34f),
            radius = w * 0.48f
        ),
        topLeft = Offset(w * 0.05f, -h * 0.02f),
        size = Size(w * 0.92f, h * 0.74f)
    )
}

private fun DrawScope.drawWaterPlane(
    water: CoolingWaterHeightField,
    fanSpeed: Float
) {
    val w = size.width
    val h = size.height
    val surfacePath = Path().apply {
        val first = waterScreenPoint(water, 0, 0, fanSpeed)
        moveTo(first.x, first.y)
        for (column in 1 until water.columns) {
            val point = waterScreenPoint(water, column, 0, fanSpeed)
            lineTo(point.x, point.y)
        }
        lineTo(w * 1.03f, h * 1.02f)
        lineTo(-w * 0.03f, h * 1.02f)
        close()
    }

    drawPath(
        path = surfacePath,
        brush = Brush.verticalGradient(
            0f to AquaCoolingHeroPalette.waterSurface.copy(alpha = 0.92f),
            0.34f to AquaCoolingHeroPalette.waterMid.copy(alpha = 0.96f),
            1f to AquaCoolingHeroPalette.waterDeep,
            startY = h * 0.54f,
            endY = h
        )
    )

    // A restrained bounced aquarium-light reflection keeps the surface photographic without
    // introducing fish/plants into the hero itself.
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                AquaCoolingHeroPalette.waterGreen.copy(alpha = 0.34f),
                Color.Transparent
            ),
            center = Offset(w * 0.08f, h * 0.74f),
            radius = w * 0.34f
        ),
        topLeft = Offset(-w * 0.18f, h * 0.57f),
        size = Size(w * 0.62f, h * 0.44f)
    )
}

private fun DrawScope.drawWaterReflections(
    water: CoolingWaterHeightField,
    fanSpeed: Float
) {
    val w = size.width
    val h = size.height
    val liveAlpha = 0.22f + fanSpeed * 0.78f

    // The highlights are calculated from the physical field's local surface gradient. They are not
    // independent sine-wave decorations; specular strength follows the simulated wave normals.
    for (row in 1 until water.rows - 1 step 2) {
        for (column in 1 until water.columns - 2) {
            val slopeX = water.slopeX(column, row)
            val slopeY = water.slopeY(column, row)
            val energy = (abs(slopeX) * 3.1f + abs(slopeY) * 2.6f).coerceIn(0f, 1f)
            if (energy < SPECULAR_ENERGY_THRESHOLD) continue

            val start = waterScreenPoint(water, column, row, fanSpeed)
            val end = waterScreenPoint(water, column + 1, row, fanSpeed)
            val depth = row.toFloat() / (water.rows - 1).toFloat()
            val alpha = (
                SPECULAR_BASE_ALPHA + energy * SPECULAR_ENERGY_ALPHA
                ) * (0.60f + depth * 0.40f) * liveAlpha
            val color = if ((column + row) % 4 == 0) {
                AquaCoolingHeroPalette.waterSpecular.copy(alpha = alpha.coerceAtMost(0.58f))
            } else {
                AquaCoolingHeroPalette.waterCyan.copy(alpha = (alpha * 0.74f).coerceAtMost(0.42f))
            }
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = 0.72f + energy * 1.45f,
                cap = StrokeCap.Round
            )
        }
    }

    val farEdge = Path().apply {
        val first = waterScreenPoint(water, 0, 0, fanSpeed)
        moveTo(first.x, first.y)
        for (column in 1 until water.columns) {
            val point = waterScreenPoint(water, column, 0, fanSpeed)
            lineTo(point.x, point.y)
        }
    }
    drawPath(
        path = farEdge,
        color = AquaCoolingHeroPalette.waterCyan.copy(alpha = 0.33f + fanSpeed * 0.22f),
        style = Stroke(width = 1.6f, cap = StrokeCap.Round)
    )
    drawPath(
        path = farEdge,
        color = AquaCoolingHeroPalette.waterSpecular.copy(alpha = 0.20f),
        style = Stroke(width = 0.7f, cap = StrokeCap.Round)
    )

    // A few broader light tracks are still field-derived, but soften the dense micro speculars so
    // the result reads as moving water rather than a wire mesh.
    for (row in 4 until water.rows - 2 step 6) {
        val track = Path()
        var started = false
        for (column in 1 until water.columns - 1) {
            val energy = (
                abs(water.slopeX(column, row)) + abs(water.slopeY(column, row))
                ).coerceIn(0f, 1f)
            if (energy < BROAD_TRACK_THRESHOLD) {
                started = false
                continue
            }
            val point = waterScreenPoint(water, column, row, fanSpeed)
            if (!started) {
                track.moveTo(point.x, point.y)
                started = true
            } else {
                track.lineTo(point.x, point.y)
            }
        }
        drawPath(
            path = track,
            color = AquaCoolingHeroPalette.waterSpecular.copy(alpha = 0.09f + fanSpeed * 0.08f),
            style = Stroke(width = 1.8f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawAquariumGlass(
    water: CoolingWaterHeightField,
    fanSpeed: Float
) {
    val w = size.width
    val h = size.height
    val leftTop = waterScreenPoint(water, 0, 0, fanSpeed)
    val rightTop = waterScreenPoint(water, water.columns - 1, 0, fanSpeed)
    val glassDepth = h * 0.035f

    val glassFace = Path().apply {
        moveTo(leftTop.x, leftTop.y - glassDepth)
        lineTo(rightTop.x, rightTop.y - glassDepth)
        lineTo(rightTop.x, rightTop.y + glassDepth * 0.18f)
        lineTo(leftTop.x, leftTop.y + glassDepth * 0.18f)
        close()
    }
    drawPath(path = glassFace, color = AquaCoolingHeroPalette.glass)
    drawLine(
        color = AquaCoolingHeroPalette.glassEdge,
        start = Offset(leftTop.x, leftTop.y - glassDepth),
        end = Offset(rightTop.x, rightTop.y - glassDepth),
        strokeWidth = 2.0f
    )
    drawLine(
        color = AquaCoolingHeroPalette.glassHighlight.copy(alpha = 0.48f),
        start = Offset(leftTop.x, leftTop.y - glassDepth - 1.2f),
        end = Offset(rightTop.x, rightTop.y - glassDepth - 1.2f),
        strokeWidth = 0.75f
    )
}

private fun DrawScope.drawFanReflection(fanSpeed: Float) {
    val w = size.width
    val h = size.height
    val reflection = Path().apply {
        moveTo(w * 0.25f, h * 0.62f)
        cubicTo(w * 0.38f, h * 0.62f, w * 0.67f, h * 0.70f, w * 0.76f, h * 0.78f)
        cubicTo(w * 0.68f, h * 0.83f, w * 0.43f, h * 0.80f, w * 0.28f, h * 0.72f)
        close()
    }
    drawPath(
        path = reflection,
        brush = Brush.verticalGradient(
            colors = listOf(
                AquaCoolingHeroPalette.metalDark.copy(alpha = 0.20f),
                Color.Transparent
            ),
            startY = h * 0.61f,
            endY = h * 0.84f
        )
    )
    drawOval(
        color = AquaCoolingHeroPalette.accent.copy(alpha = 0.018f + fanSpeed * 0.025f),
        topLeft = Offset(w * 0.38f, h * 0.69f),
        size = Size(w * 0.29f, h * 0.08f)
    )
}

private fun DrawScope.drawClamp() {
    val w = size.width
    val h = size.height

    val clampShadow = Path().apply {
        moveTo(w * 0.565f, h * 0.405f)
        lineTo(w * 0.735f, h * 0.445f)
        lineTo(w * 0.718f, h * 0.655f)
        lineTo(w * 0.650f, h * 0.638f)
        lineTo(w * 0.657f, h * 0.527f)
        lineTo(w * 0.603f, h * 0.515f)
        lineTo(w * 0.596f, h * 0.635f)
        lineTo(w * 0.548f, h * 0.622f)
        close()
    }
    translate(0f, h * 0.012f) {
        drawPath(clampShadow, color = AquaCoolingHeroPalette.metalShadow.copy(alpha = 0.56f))
    }
    drawPath(
        path = clampShadow,
        brush = Brush.linearGradient(
            colors = listOf(
                AquaCoolingHeroPalette.metalLight,
                AquaCoolingHeroPalette.metalDark,
                AquaCoolingHeroPalette.metalDeep
            ),
            start = Offset(w * 0.55f, h * 0.40f),
            end = Offset(w * 0.73f, h * 0.66f)
        )
    )

    val innerCutout = Path().apply {
        moveTo(w * 0.615f, h * 0.493f)
        lineTo(w * 0.700f, h * 0.512f)
        lineTo(w * 0.695f, h * 0.587f)
        lineTo(w * 0.650f, h * 0.577f)
        lineTo(w * 0.653f, h * 0.528f)
        lineTo(w * 0.612f, h * 0.519f)
        close()
    }
    drawPath(innerCutout, color = AquaCoolingHeroPalette.metalDeep.copy(alpha = 0.96f))
    drawPath(
        innerCutout,
        color = AquaCoolingHeroPalette.metalHighlight.copy(alpha = 0.23f),
        style = Stroke(width = 0.85f)
    )
}

private fun DrawScope.drawAirRefraction(
    fanSpeed: Float,
    timeSeconds: Float
) {
    if (fanSpeed <= 0.04f) return
    val w = size.width
    val h = size.height
    val phase = timeSeconds * (1.4f + fanSpeed * 2.0f)

    repeat(3) { index ->
        val offset = sinF(phase + index * 1.7f) * w * 0.012f
        val path = Path().apply {
            moveTo(w * (0.34f + index * 0.085f), h * 0.47f)
            cubicTo(
                w * (0.31f + index * 0.080f) + offset,
                h * 0.52f,
                w * (0.29f + index * 0.075f) - offset,
                h * 0.56f,
                w * (0.30f + index * 0.070f),
                h * 0.60f
            )
        }
        drawPath(
            path = path,
            color = AquaCoolingHeroPalette.accentBright.copy(
                alpha = (0.018f + fanSpeed * 0.018f) * (1f - index * 0.18f)
            ),
            style = Stroke(width = 1.0f + index * 0.25f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawFanBody(
    fanSpeed: Float,
    timeSeconds: Float
) {
    val w = size.width
    val h = size.height
    val shellPath = createFanShellPath(w, h)

    translate(0f, h * 0.025f) {
        drawPath(
            path = shellPath,
            color = AquaCoolingHeroPalette.metalShadow.copy(alpha = 0.62f)
        )
    }

    drawPath(
        path = shellPath,
        brush = Brush.linearGradient(
            0f to AquaCoolingHeroPalette.metalLight,
            0.18f to AquaCoolingHeroPalette.metalMid,
            0.57f to AquaCoolingHeroPalette.metalDark,
            0.86f to AquaCoolingHeroPalette.metalMid,
            1f to AquaCoolingHeroPalette.metalDeep,
            start = Offset(w * 0.31f, h * 0.08f),
            end = Offset(w * 0.78f, h * 0.49f)
        )
    )
    drawPath(
        path = shellPath,
        color = AquaCoolingHeroPalette.metalHighlight.copy(alpha = 0.26f),
        style = Stroke(width = 1.15f)
    )

    val topHighlight = Path().apply {
        moveTo(w * 0.315f, h * 0.106f)
        cubicTo(w * 0.44f, h * 0.078f, w * 0.69f, h * 0.118f, w * 0.785f, h * 0.183f)
    }
    drawPath(
        path = topHighlight,
        color = AquaCoolingHeroPalette.metalHighlight.copy(alpha = 0.24f),
        style = Stroke(width = 1.05f, cap = StrokeCap.Round)
    )

    drawFrontVent(w, h)
    drawTopRotor(w, h, fanSpeed, timeSeconds)
}

private fun DrawScope.drawFrontVent(w: Float, h: Float) {
    val ventOuter = Path().apply {
        moveTo(w * 0.235f, h * 0.362f)
        cubicTo(w * 0.228f, h * 0.344f, w * 0.244f, h * 0.332f, w * 0.267f, h * 0.337f)
        lineTo(w * 0.692f, h * 0.427f)
        cubicTo(w * 0.715f, h * 0.432f, w * 0.722f, h * 0.451f, w * 0.706f, h * 0.467f)
        lineTo(w * 0.676f, h * 0.497f)
        cubicTo(w * 0.667f, h * 0.506f, w * 0.653f, h * 0.509f, w * 0.639f, h * 0.506f)
        lineTo(w * 0.273f, h * 0.428f)
        cubicTo(w * 0.250f, h * 0.423f, w * 0.238f, h * 0.405f, w * 0.238f, h * 0.384f)
        close()
    }
    drawPath(
        path = ventOuter,
        brush = Brush.linearGradient(
            colors = listOf(
                AquaCoolingHeroPalette.ventEdge,
                AquaCoolingHeroPalette.metalDeep,
                AquaCoolingHeroPalette.ventBlack
            ),
            start = Offset(w * 0.24f, h * 0.34f),
            end = Offset(w * 0.70f, h * 0.50f)
        )
    )

    val ventInner = Path().apply {
        moveTo(w * 0.258f, h * 0.365f)
        lineTo(w * 0.685f, h * 0.452f)
        lineTo(w * 0.659f, h * 0.486f)
        lineTo(w * 0.276f, h * 0.407f)
        close()
    }
    drawPath(ventInner, color = AquaCoolingHeroPalette.ventBlack)

    clipPath(ventInner) {
        repeat(5) { row ->
            val t = row / 4f
            val yLeft = h * (0.369f + t * 0.038f)
            val yRight = h * (0.456f + t * 0.030f)
            drawLine(
                color = AquaCoolingHeroPalette.ventEdge.copy(alpha = 0.52f),
                start = Offset(w * 0.255f, yLeft),
                end = Offset(w * 0.689f, yRight),
                strokeWidth = 1.05f
            )
        }
        repeat(10) { column ->
            val t = column / 9f
            val x = w * (0.278f + t * 0.382f)
            val topY = h * (0.371f + t * 0.079f)
            val bottomY = h * (0.407f + t * 0.079f)
            drawLine(
                color = AquaCoolingHeroPalette.ventCell.copy(alpha = 0.94f),
                start = Offset(x, topY),
                end = Offset(x, bottomY),
                strokeWidth = 2.25f
            )
            drawLine(
                color = AquaCoolingHeroPalette.ventEdge.copy(alpha = 0.20f),
                start = Offset(x + 1.0f, topY),
                end = Offset(x + 1.0f, bottomY),
                strokeWidth = 0.65f
            )
        }
    }
    drawPath(
        ventInner,
        color = AquaCoolingHeroPalette.metalHighlight.copy(alpha = 0.20f),
        style = Stroke(width = 0.85f)
    )
}

private fun DrawScope.drawTopRotor(
    w: Float,
    h: Float,
    fanSpeed: Float,
    timeSeconds: Float
) {
    val center = Offset(w * 0.535f, h * 0.245f)
    val rotorWidth = w * 0.345f
    val rotorHeight = h * 0.178f
    val outerTopLeft = Offset(center.x - rotorWidth / 2f, center.y - rotorHeight / 2f)
    val outerSize = Size(rotorWidth, rotorHeight)
    val glowAlpha = 0.055f + fanSpeed * 0.18f

    drawOval(
        color = AquaCoolingHeroPalette.accent.copy(alpha = glowAlpha * 0.36f),
        topLeft = Offset(outerTopLeft.x - 5f, outerTopLeft.y - 3f),
        size = Size(outerSize.width + 10f, outerSize.height + 6f),
        style = Stroke(width = 7.5f)
    )
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(
                AquaCoolingHeroPalette.metalHighlight,
                AquaCoolingHeroPalette.metalMid,
                AquaCoolingHeroPalette.metalDeep,
                AquaCoolingHeroPalette.metalLight
            ),
            start = outerTopLeft,
            end = Offset(outerTopLeft.x + outerSize.width, outerTopLeft.y + outerSize.height)
        ),
        topLeft = outerTopLeft,
        size = outerSize
    )

    val innerInsetX = w * 0.012f
    val innerInsetY = h * 0.009f
    val innerTopLeft = Offset(outerTopLeft.x + innerInsetX, outerTopLeft.y + innerInsetY)
    val innerSize = Size(rotorWidth - innerInsetX * 2f, rotorHeight - innerInsetY * 2f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                AquaCoolingHeroPalette.rotorMid,
                AquaCoolingHeroPalette.rotorBlack
            ),
            center = center,
            radius = rotorWidth * 0.48f
        ),
        topLeft = innerTopLeft,
        size = innerSize
    )

    val rotorAngle = (
        timeSeconds * (ROTOR_IDLE_DEGREES_PER_SECOND + fanSpeed * ROTOR_SPEED_DEGREES_PER_SECOND)
        ) % FULL_ROTATION_DEGREES
    val physicalRadius = rotorWidth * 0.43f
    val hubRadius = rotorWidth * 0.095f
    val bladePath = createRotorBladePath(physicalRadius, hubRadius)

    translate(center.x, center.y) {
        scale(scaleX = 1f, scaleY = ROTOR_PERSPECTIVE_SCALE_Y, pivot = Offset.Zero) {
            val trailCount = if (fanSpeed > 0.08f) ROTOR_TRAIL_COUNT else 1
            repeat(trailCount) { trail ->
                val trailAngle = rotorAngle - trail * (3.2f + fanSpeed * 5.4f)
                val trailAlpha = if (trail == 0) {
                    0.72f - fanSpeed * 0.24f
                } else {
                    (0.20f * fanSpeed) / trail.toFloat()
                }
                repeat(ROTOR_BLADE_COUNT) { bladeIndex ->
                    rotate(
                        degrees = trailAngle + bladeIndex * (FULL_ROTATION_DEGREES / ROTOR_BLADE_COUNT),
                        pivot = Offset.Zero
                    ) {
                        drawPath(
                            path = bladePath,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    AquaCoolingHeroPalette.rotorHighlight.copy(alpha = trailAlpha),
                                    AquaCoolingHeroPalette.rotorBlade.copy(alpha = trailAlpha),
                                    AquaCoolingHeroPalette.rotorBlack.copy(alpha = trailAlpha)
                                ),
                                start = Offset(hubRadius, -physicalRadius * 0.2f),
                                end = Offset(physicalRadius, physicalRadius * 0.2f)
                            )
                        )
                    }
                }
            }
        }
    }

    val hubSize = Size(rotorWidth * 0.145f, rotorHeight * 0.34f)
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(
                AquaCoolingHeroPalette.metalHighlight,
                AquaCoolingHeroPalette.metalMid,
                AquaCoolingHeroPalette.metalDeep
            ),
            start = Offset(center.x - hubSize.width / 2f, center.y - hubSize.height / 2f),
            end = Offset(center.x + hubSize.width / 2f, center.y + hubSize.height / 2f)
        ),
        topLeft = Offset(center.x - hubSize.width / 2f, center.y - hubSize.height / 2f),
        size = hubSize
    )

    drawOval(
        color = AquaCoolingHeroPalette.accent.copy(alpha = 0.62f + fanSpeed * 0.30f),
        topLeft = Offset(outerTopLeft.x + 1.0f, outerTopLeft.y + 0.7f),
        size = Size(outerSize.width - 2f, outerSize.height - 1.4f),
        style = Stroke(width = 1.55f + fanSpeed * 0.75f)
    )
    drawOval(
        color = AquaCoolingHeroPalette.accentBright.copy(alpha = 0.24f + fanSpeed * 0.28f),
        topLeft = Offset(outerTopLeft.x + 3.1f, outerTopLeft.y + 2.0f),
        size = Size(outerSize.width - 6.2f, outerSize.height - 4.0f),
        style = Stroke(width = 0.65f)
    )
}

private fun DrawScope.drawCableAndProbe() {
    val w = size.width
    val h = size.height
    val cablePath = Path().apply {
        moveTo(w * 0.785f, h * 0.265f)
        cubicTo(w * 0.865f, h * 0.335f, w * 0.885f, h * 0.470f, w * 0.835f, h * 0.560f)
        cubicTo(w * 0.795f, h * 0.635f, w * 0.800f, h * 0.735f, w * 0.790f, h * 0.825f)
    }
    drawPath(
        path = cablePath,
        color = AquaCoolingHeroPalette.metalShadow.copy(alpha = 0.65f),
        style = Stroke(width = 5.8f, cap = StrokeCap.Round)
    )
    drawPath(
        path = cablePath,
        color = AquaCoolingHeroPalette.cable,
        style = Stroke(width = 3.7f, cap = StrokeCap.Round)
    )
    drawPath(
        path = cablePath,
        color = AquaCoolingHeroPalette.cableHighlight.copy(alpha = 0.38f),
        style = Stroke(width = 0.85f, cap = StrokeCap.Round)
    )

    val cupCenter = Offset(w * 0.790f, h * 0.826f)
    val cupRadius = w * 0.041f
    drawCircle(
        color = AquaCoolingHeroPalette.glass.copy(alpha = 0.42f),
        radius = cupRadius,
        center = cupCenter
    )
    drawCircle(
        color = AquaCoolingHeroPalette.glassHighlight.copy(alpha = 0.30f),
        radius = cupRadius,
        center = cupCenter,
        style = Stroke(width = 1.15f)
    )
    drawCircle(
        color = AquaCoolingHeroPalette.metalDeep.copy(alpha = 0.82f),
        radius = cupRadius * 0.42f,
        center = cupCenter
    )

    val sensorWidth = w * 0.022f
    val sensorHeight = h * 0.105f
    val sensorTopLeft = Offset(cupCenter.x - sensorWidth / 2f, cupCenter.y - h * 0.012f)
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                AquaCoolingHeroPalette.metalDeep,
                AquaCoolingHeroPalette.sensorMetal,
                AquaCoolingHeroPalette.metalDark
            ),
            startX = sensorTopLeft.x,
            endX = sensorTopLeft.x + sensorWidth
        ),
        topLeft = sensorTopLeft,
        size = Size(sensorWidth, sensorHeight),
        cornerRadius = CornerRadius(sensorWidth * 0.44f)
    )
    drawLine(
        color = AquaCoolingHeroPalette.metalHighlight.copy(alpha = 0.42f),
        start = Offset(sensorTopLeft.x + sensorWidth * 0.31f, sensorTopLeft.y + 3f),
        end = Offset(
            sensorTopLeft.x + sensorWidth * 0.31f,
            sensorTopLeft.y + sensorHeight - 3f
        ),
        strokeWidth = 0.7f
    )
}

private fun createFanShellPath(w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.292f, h * 0.103f)
    cubicTo(w * 0.315f, h * 0.080f, w * 0.352f, h * 0.073f, w * 0.389f, h * 0.079f)
    lineTo(w * 0.765f, h * 0.145f)
    cubicTo(w * 0.805f, h * 0.152f, w * 0.829f, h * 0.180f, w * 0.820f, h * 0.210f)
    lineTo(w * 0.742f, h * 0.441f)
    cubicTo(w * 0.731f, h * 0.477f, w * 0.702f, h * 0.500f, w * 0.666f, h * 0.505f)
    lineTo(w * 0.285f, h * 0.426f)
    cubicTo(w * 0.247f, h * 0.418f, w * 0.224f, h * 0.388f, w * 0.231f, h * 0.354f)
    lineTo(w * 0.274f, h * 0.139f)
    cubicTo(w * 0.278f, h * 0.122f, w * 0.283f, h * 0.111f, w * 0.292f, h * 0.103f)
    close()
}

private fun createRotorBladePath(radius: Float, hubRadius: Float): Path = Path().apply {
    moveTo(hubRadius * 0.50f, -hubRadius * 0.30f)
    cubicTo(
        radius * 0.26f,
        -radius * 0.31f,
        radius * 0.66f,
        -radius * 0.30f,
        radius * 0.79f,
        -radius * 0.07f
    )
    cubicTo(
        radius * 0.72f,
        radius * 0.14f,
        radius * 0.36f,
        radius * 0.24f,
        hubRadius * 0.42f,
        hubRadius * 0.33f
    )
    close()
}

private fun DrawScope.waterScreenPoint(
    water: CoolingWaterHeightField,
    column: Int,
    row: Int,
    fanSpeed: Float
): Offset {
    val normalizedX = column.toFloat() / (water.columns - 1).toFloat()
    val normalizedDepth = row.toFloat() / (water.rows - 1).toFloat()
    val farY = lerp(size.height * 0.555f, size.height * 0.685f, normalizedX)
    val baseY = lerp(farY, size.height * 1.015f, normalizedDepth)
    val perspectiveAmplitude = size.height * (
        0.009f + fanSpeed * 0.018f
        ) * (0.58f + normalizedDepth * 0.58f)
    val displacement = water.height(column, row) * perspectiveAmplitude
    val refractionX = water.slopeX(column, row) * size.width * 0.0018f
    return Offset(
        x = normalizedX * size.width + refractionX,
        y = baseY + displacement
    )
}

private class CoolingWaterHeightField(
    val columns: Int,
    val rows: Int
) {
    private val heights = FloatArray(columns * rows)
    private val velocities = FloatArray(columns * rows)
    private val nextVelocities = FloatArray(columns * rows)

    fun height(column: Int, row: Int): Float = heights[index(column, row)]

    fun slopeX(column: Int, row: Int): Float {
        val left = heights[index((column - 1).coerceAtLeast(0), row)]
        val right = heights[index((column + 1).coerceAtMost(columns - 1), row)]
        return (right - left) * 0.5f
    }

    fun slopeY(column: Int, row: Int): Float {
        val top = heights[index(column, (row - 1).coerceAtLeast(0))]
        val bottom = heights[index(column, (row + 1).coerceAtMost(rows - 1))]
        return (bottom - top) * 0.5f
    }

    fun step(
        deltaSeconds: Float,
        fanSpeed: Float,
        pressurePhase: Float
    ) {
        for (row in 1 until rows - 1) {
            for (column in 1 until columns - 1) {
                val index = index(column, row)
                val currentHeight = heights[index]
                val currentVelocity = velocities[index]
                val laplacian =
                    heights[index(column - 1, row)] +
                        heights[index(column + 1, row)] +
                        heights[index(column, row - 1)] +
                        heights[index(column, row + 1)] -
                        currentHeight * 4f

                val normalizedX = column.toFloat() / (columns - 1).toFloat()
                val normalizedY = row.toFloat() / (rows - 1).toFloat()
                val dx = (normalizedX - PRESSURE_CENTER_X) / PRESSURE_RADIUS_X
                val dy = (normalizedY - PRESSURE_CENTER_Y) / PRESSURE_RADIUS_Y
                val gaussian = exp(
                    (-(dx * dx + dy * dy) * PRESSURE_FALLOFF).toDouble()
                ).toFloat()
                val turbulence =
                    0.72f +
                        0.18f * sinF(pressurePhase * 1.8f + normalizedX * 8.0f) +
                        0.10f * sinF(pressurePhase * 3.3f - normalizedY * 11.0f)
                val pressure = -fanSpeed * gaussian * PRESSURE_FORCE * turbulence
                val acceleration =
                    laplacian * WAVE_PROPAGATION -
                        currentHeight * SURFACE_RESTORE -
                        currentVelocity * VELOCITY_DAMPING +
                        pressure

                val edgeDistance = min(
                    min(column, columns - 1 - column),
                    min(row, rows - 1 - row)
                ).toFloat()
                val edgeBlend = (edgeDistance / EDGE_ABSORBER_CELLS).coerceIn(0f, 1f)
                val absorber = lerp(EDGE_ABSORPTION, 1f, edgeBlend)
                nextVelocities[index] = (
                    currentVelocity + acceleration * deltaSeconds
                    ) * absorber
            }
        }

        for (row in 1 until rows - 1) {
            for (column in 1 until columns - 1) {
                val index = index(column, row)
                velocities[index] = nextVelocities[index]
                heights[index] = (
                    heights[index] + velocities[index] * deltaSeconds
                    ).coerceIn(-MAX_WATER_HEIGHT, MAX_WATER_HEIGHT)
            }
        }

        clearBoundary()
    }

    private fun clearBoundary() {
        for (column in 0 until columns) {
            clearCell(column, 0)
            clearCell(column, rows - 1)
        }
        for (row in 1 until rows - 1) {
            clearCell(0, row)
            clearCell(columns - 1, row)
        }
    }

    private fun clearCell(column: Int, row: Int) {
        val index = index(column, row)
        heights[index] = 0f
        velocities[index] = 0f
        nextVelocities[index] = 0f
    }

    private fun index(column: Int, row: Int): Int = row * columns + column
}

private fun nanosToSeconds(nanos: Long): Float =
    (nanos.toDouble() / NANOS_PER_SECOND).toFloat()

private fun sinF(value: Float): Float = sin(value.toDouble()).toFloat()

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private const val WATER_COLUMNS = 56
private const val WATER_ROWS = 24
private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val MAX_FRAME_DELTA_SECONDS = 0.05f
private const val WATER_FIXED_STEP_SECONDS = 1f / 120f
private const val WATER_PHASE_BASE_RADIANS_PER_SECOND = 6.0f
private const val WATER_PHASE_SPEED_RADIANS_PER_SECOND = 21.0f

private const val PRESSURE_CENTER_X = 0.385f
private const val PRESSURE_CENTER_Y = 0.115f
private const val PRESSURE_RADIUS_X = 0.155f
private const val PRESSURE_RADIUS_Y = 0.115f
private const val PRESSURE_FALLOFF = 1.75f
private const val PRESSURE_FORCE = 5.2f
private const val WAVE_PROPAGATION = 118f
private const val SURFACE_RESTORE = 17f
private const val VELOCITY_DAMPING = 4.6f
private const val EDGE_ABSORBER_CELLS = 5f
private const val EDGE_ABSORPTION = 0.82f
private const val MAX_WATER_HEIGHT = 0.72f

private const val SPECULAR_ENERGY_THRESHOLD = 0.010f
private const val SPECULAR_BASE_ALPHA = 0.035f
private const val SPECULAR_ENERGY_ALPHA = 0.78f
private const val BROAD_TRACK_THRESHOLD = 0.020f

private const val ROTOR_BLADE_COUNT = 5
private const val ROTOR_TRAIL_COUNT = 4
private const val ROTOR_PERSPECTIVE_SCALE_Y = 0.58f
private const val ROTOR_IDLE_DEGREES_PER_SECOND = 18f
private const val ROTOR_SPEED_DEGREES_PER_SECOND = 980f
private const val FULL_ROTATION_DEGREES = 360f
