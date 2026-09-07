package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws a stable, perspective-projected rotor inside the approved cooling-device artwork.
 *
 * The source bitmap already contains a perspective-rendered fan. Re-transforming those pixels
 * introduced a second projection and moved photographed hub highlights outside the true axis.
 * This implementation replaces only the inner aperture while the fan is active: blades are built
 * around a zero-origin circle, projected once into the artwork's fixed ellipse, and the hub is
 * drawn after rotation as a stationary optical anchor. High output progressively trades sharp
 * blade edges for temporal blur, preventing multi-blade aliasing on both 30 Hz and 60 Hz displays.
 */
internal fun DrawScope.drawCoolingFanRotor(
    deviceImage: ImageBitmap,
    rotationPhase: Float,
    motionIntensity: Float,
    colors: AquaDeviceCardColors
) {
    val hasImageBounds = deviceImage.width > 0 && deviceImage.height > 0
    val hasCanvasBounds = size.width > 0f && size.height > 0f
    if (!hasImageBounds || !hasCanvasBounds) return

    val imageWidth = deviceImage.width.toFloat()
    val imageHeight = deviceImage.height.toFloat()
    val imageScale = min(size.width / imageWidth, size.height / imageHeight)
    val fittedWidth = imageWidth * imageScale
    val fittedHeight = imageHeight * imageScale
    val imageLeft = (size.width - fittedWidth) / HALF_DIVISOR
    val imageTop = (size.height - fittedHeight) / HALF_DIVISOR
    val geometry = CoolingFanRotorGeometry(
        centerX = imageLeft + fittedWidth * FAN_CENTER_X,
        centerY = imageTop + fittedHeight * FAN_CENTER_Y,
        radiusX = fittedWidth * FAN_RADIUS_X,
        radiusY = fittedHeight * FAN_RADIUS_Y
    )
    if (geometry.radiusX <= 0f || geometry.radiusY <= 0f) return

    val intensity = motionIntensity.coerceIn(NO_INTENSITY, FULL_INTENSITY)
    val rotationDegrees = rotationPhase * FULL_ROTATION_DEGREES
    val aperture = geometry.aperturePath()
    val projection = geometry.projectionMatrix()
    val blade = geometry.bladePath()
    configureRotorPaints(colors)

    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        val apertureCheckpoint = nativeCanvas.save()
        try {
            nativeCanvas.clipPath(aperture)
            nativeCanvas.drawPath(aperture, FAN_WELL_PAINT)

            val projectionCheckpoint = nativeCanvas.save()
            try {
                nativeCanvas.concat(projection)
                drawProjectedRotor(
                    canvas = nativeCanvas,
                    blade = blade,
                    geometry = geometry,
                    rotationDegrees = rotationDegrees,
                    intensity = intensity
                )
                drawStationaryHub(nativeCanvas, geometry)
            } finally {
                nativeCanvas.restoreToCount(projectionCheckpoint)
            }
        } finally {
            nativeCanvas.restoreToCount(apertureCheckpoint)
        }
    }
}

private fun drawProjectedRotor(
    canvas: android.graphics.Canvas,
    blade: Path,
    geometry: CoolingFanRotorGeometry,
    rotationDegrees: Float,
    intensity: Float
) {
    val blurDegrees = MAXIMUM_MOTION_BLUR_DEGREES * intensity
    ROTOR_BLADE_GHOST_PAINT.alpha = (MAXIMUM_GHOST_ALPHA * intensity).toInt()
    MOTION_BLUR_SAMPLE_OFFSETS.forEach { sampleOffset ->
        drawBladeSet(
            canvas = canvas,
            blade = blade,
            rotationDegrees = rotationDegrees + blurDegrees * sampleOffset,
            paint = ROTOR_BLADE_GHOST_PAINT
        )
    }

    ROTOR_BLADE_PAINT.alpha = (
        MAXIMUM_CRISP_BLADE_ALPHA -
            (MAXIMUM_CRISP_BLADE_ALPHA - MINIMUM_CRISP_BLADE_ALPHA) * intensity
        ).toInt()
    drawBladeSet(canvas, blade, rotationDegrees, ROTOR_BLADE_PAINT)
    drawRotationStreak(canvas, geometry, rotationDegrees, intensity)
}

private fun drawRotationStreak(
    canvas: android.graphics.Canvas,
    geometry: CoolingFanRotorGeometry,
    rotationDegrees: Float,
    intensity: Float
) {
    val radius = geometry.bladeRadius * ROTATION_STREAK_RADIUS_SCALE
    ROTOR_STREAK_PAINT.alpha = (MAXIMUM_ROTATION_STREAK_ALPHA * intensity).toInt()
    ROTOR_STREAK_PAINT.strokeWidth = geometry.bladeRadius * ROTATION_STREAK_WIDTH_SCALE
    val checkpoint = canvas.save()
    try {
        canvas.rotate(rotationDegrees, ORIGIN, ORIGIN)
        canvas.drawArc(
            RectF(-radius, -radius, radius, radius),
            ROTATION_STREAK_START_DEGREES,
            ROTATION_STREAK_SWEEP_DEGREES,
            false,
            ROTOR_STREAK_PAINT
        )
    } finally {
        canvas.restoreToCount(checkpoint)
    }
}

private fun drawBladeSet(
    canvas: android.graphics.Canvas,
    blade: Path,
    rotationDegrees: Float,
    paint: Paint
) {
    val checkpoint = canvas.save()
    try {
        canvas.rotate(rotationDegrees, ORIGIN, ORIGIN)
        repeat(FAN_BLADE_COUNT) {
            canvas.drawPath(blade, paint)
            canvas.rotate(FAN_BLADE_ANGLE_DEGREES, ORIGIN, ORIGIN)
        }
    } finally {
        canvas.restoreToCount(checkpoint)
    }
}

private fun drawStationaryHub(
    canvas: android.graphics.Canvas,
    geometry: CoolingFanRotorGeometry
) {
    canvas.drawCircle(ORIGIN, ORIGIN, geometry.hubRadius * HUB_SHADOW_SCALE, HUB_SHADOW_PAINT)
    canvas.drawCircle(ORIGIN, ORIGIN, geometry.hubRadius, HUB_BODY_PAINT)
    val hubBounds = RectF(
        -geometry.hubRadius,
        -geometry.hubRadius,
        geometry.hubRadius,
        geometry.hubRadius
    )
    canvas.drawArc(
        hubBounds,
        HUB_HIGHLIGHT_START_DEGREES,
        HUB_HIGHLIGHT_SWEEP_DEGREES,
        false,
        HUB_HIGHLIGHT_PAINT
    )
    canvas.drawLine(
        -geometry.hubRadius * HUB_DIVIDER_LENGTH_SCALE,
        ORIGIN,
        geometry.hubRadius * HUB_DIVIDER_LENGTH_SCALE,
        ORIGIN,
        HUB_DIVIDER_PAINT
    )
}

private data class CoolingFanRotorGeometry(
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float
) {
    val bladeRadius: Float = radiusX * BLADE_RADIUS_SCALE
    val hubRadius: Float = radiusX * HUB_RADIUS_SCALE

    fun aperturePath(): Path {
        val clipRadiusX = radiusX * FAN_CLIP_SCALE
        val clipRadiusY = radiusY * FAN_CLIP_SCALE
        return Path().apply {
            addOval(
                RectF(
                    centerX - clipRadiusX,
                    centerY - clipRadiusY,
                    centerX + clipRadiusX,
                    centerY + clipRadiusY
                ),
                Path.Direction.CW
            )
            transform(
                Matrix().apply {
                    setRotate(FAN_PLANE_TILT_DEGREES, centerX, centerY)
                }
            )
        }
    }

    fun projectionMatrix(): Matrix {
        val planeCos = cos(FAN_PLANE_TILT_RADIANS.toDouble()).toFloat()
        val planeSin = sin(FAN_PLANE_TILT_RADIANS.toDouble()).toFloat()
        val ellipseRatio = radiusY / radiusX
        return Matrix().apply {
            setValues(
                floatArrayOf(
                    planeCos,
                    -planeSin * ellipseRatio,
                    centerX,
                    planeSin,
                    planeCos * ellipseRatio,
                    centerY,
                    ORIGIN,
                    ORIGIN,
                    UNIT_MATRIX_VALUE
                )
            )
        }
    }

    fun bladePath(): Path {
        val hub = hubRadius
        val blade = bladeRadius
        return Path().apply {
            moveTo(hub * BLADE_START_X, hub * BLADE_START_Y)
            cubicTo(
                blade * BLADE_CONTROL_ONE_X,
                blade * BLADE_CONTROL_ONE_Y,
                blade * BLADE_CONTROL_TWO_X,
                blade * BLADE_CONTROL_TWO_Y,
                blade * BLADE_TIP_X,
                blade * BLADE_TIP_Y
            )
            cubicTo(
                blade * BLADE_RETURN_ONE_X,
                blade * BLADE_RETURN_ONE_Y,
                blade * BLADE_RETURN_TWO_X,
                blade * BLADE_RETURN_TWO_Y,
                hub * BLADE_END_X,
                hub * BLADE_END_Y
            )
            close()
        }
    }
}

private fun configureRotorPaints(colors: AquaDeviceCardColors) {
    FAN_WELL_PAINT.color = colors.mediaSurface.toArgb()
    ROTOR_BLADE_PAINT.color = colors.secondaryText.toArgb()
    ROTOR_BLADE_GHOST_PAINT.color = colors.accent.toArgb()
    ROTOR_STREAK_PAINT.color = colors.accent.toArgb()
    HUB_SHADOW_PAINT.color = colors.surface.toArgb()
    HUB_BODY_PAINT.color = colors.secondaryText.toArgb()
    HUB_HIGHLIGHT_PAINT.color = colors.primaryText.toArgb()
    HUB_HIGHLIGHT_PAINT.alpha = HUB_HIGHLIGHT_ALPHA
    HUB_DIVIDER_PAINT.color = colors.mediaOutline.toArgb()
    HUB_DIVIDER_PAINT.alpha = HUB_DIVIDER_ALPHA
}

private const val PAINT_FLAGS = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
private val FAN_WELL_PAINT = Paint(PAINT_FLAGS).apply {
    style = Paint.Style.FILL
}
private val ROTOR_BLADE_PAINT = Paint(PAINT_FLAGS).apply {
    style = Paint.Style.FILL
}
private val ROTOR_BLADE_GHOST_PAINT = Paint(PAINT_FLAGS).apply {
    style = Paint.Style.FILL
}
private val ROTOR_STREAK_PAINT = Paint(PAINT_FLAGS).apply {
    strokeCap = Paint.Cap.ROUND
    style = Paint.Style.STROKE
}
private val HUB_SHADOW_PAINT = Paint(PAINT_FLAGS).apply {
    style = Paint.Style.FILL
}
private val HUB_BODY_PAINT = Paint(PAINT_FLAGS).apply {
    style = Paint.Style.FILL
}
private val HUB_HIGHLIGHT_PAINT = Paint(PAINT_FLAGS).apply {
    alpha = HUB_HIGHLIGHT_ALPHA
    strokeWidth = HUB_HIGHLIGHT_STROKE
    strokeCap = Paint.Cap.ROUND
    style = Paint.Style.STROKE
}
private val HUB_DIVIDER_PAINT = Paint(PAINT_FLAGS).apply {
    alpha = HUB_DIVIDER_ALPHA
    strokeWidth = HUB_DIVIDER_STROKE
    strokeCap = Paint.Cap.ROUND
    style = Paint.Style.STROKE
}

// Calibrated against the artwork aperture; this center never changes with rotation.
private const val FAN_CENTER_X = 0.463f
private const val FAN_CENTER_Y = 0.278f
private const val FAN_RADIUS_X = 0.178f
private const val FAN_RADIUS_Y = 0.099f
private const val FAN_PLANE_TILT_DEGREES = -6.4f
private const val FAN_PLANE_TILT_RADIANS = -0.11170107f
private const val FAN_CLIP_SCALE = 0.97f
private const val BLADE_RADIUS_SCALE = 0.91f
private const val HUB_RADIUS_SCALE = 0.36f
private const val FAN_BLADE_COUNT = 7
private const val FAN_BLADE_ANGLE_DEGREES = FULL_ROTATION_DEGREES / FAN_BLADE_COUNT
private const val MAXIMUM_MOTION_BLUR_DEGREES = 25.7f
private const val MAXIMUM_GHOST_ALPHA = 44f
private const val MAXIMUM_CRISP_BLADE_ALPHA = 220f
private const val MINIMUM_CRISP_BLADE_ALPHA = 38f
private val MOTION_BLUR_SAMPLE_OFFSETS = floatArrayOf(-1f, -0.5f, 0.5f, 1f)
private const val ROTATION_STREAK_RADIUS_SCALE = 0.72f
private const val ROTATION_STREAK_WIDTH_SCALE = 0.13f
private const val ROTATION_STREAK_START_DEGREES = -24f
private const val ROTATION_STREAK_SWEEP_DEGREES = 42f
private const val MAXIMUM_ROTATION_STREAK_ALPHA = 58f

private const val BLADE_START_X = 0.76f
private const val BLADE_START_Y = -0.42f
private const val BLADE_CONTROL_ONE_X = 0.44f
private const val BLADE_CONTROL_ONE_Y = -0.48f
private const val BLADE_CONTROL_TWO_X = 0.90f
private const val BLADE_CONTROL_TWO_Y = -0.50f
private const val BLADE_TIP_X = 0.96f
private const val BLADE_TIP_Y = -0.16f
private const val BLADE_RETURN_ONE_X = 0.82f
private const val BLADE_RETURN_ONE_Y = 0.10f
private const val BLADE_RETURN_TWO_X = 0.43f
private const val BLADE_RETURN_TWO_Y = 0.31f
private const val BLADE_END_X = 0.54f
private const val BLADE_END_Y = 0.58f

private const val HUB_SHADOW_SCALE = 1.12f
private const val HUB_HIGHLIGHT_START_DEGREES = 205f
private const val HUB_HIGHLIGHT_SWEEP_DEGREES = 145f
private const val HUB_HIGHLIGHT_STROKE = 1.6f
private const val HUB_HIGHLIGHT_ALPHA = 190
private const val HUB_DIVIDER_LENGTH_SCALE = 0.78f
private const val HUB_DIVIDER_STROKE = 1.1f
private const val HUB_DIVIDER_ALPHA = 150
private const val FULL_ROTATION_DEGREES = 360f
private const val HALF_DIVISOR = 2f
private const val NO_INTENSITY = 0f
private const val FULL_INTENSITY = 1f
private const val ORIGIN = 0f
private const val UNIT_MATRIX_VALUE = 1f
