package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Rotates the pixels of the approved cooling-device artwork inside the fan aperture.
 *
 * The fan plane is an oblique ellipse in screen space. Rotating that ellipse directly would rotate
 * the perspective itself and look like a flat sticker. Instead, the source pixels are transformed
 * with A * R * A^-1, where A is the projected fan-plane basis. The housing and illuminated ring
 * remain untouched while the original photographic rotor texture moves inside them.
 */
internal fun DrawScope.drawCoolingFanRotor(
    deviceImage: ImageBitmap,
    rotationPhase: Float
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

    val rotor = CoolingFanRotorGeometry(
        centerX = imageLeft + fittedWidth * FAN_CENTER_X,
        centerY = imageTop + fittedHeight * FAN_CENTER_Y,
        radiusX = fittedWidth * FAN_RADIUS_X,
        radiusY = fittedHeight * FAN_RADIUS_Y
    )
    if (rotor.radiusX <= 0f || rotor.radiusY <= 0f) return

    val destination = RectF(
        imageLeft,
        imageTop,
        imageLeft + fittedWidth,
        imageTop + fittedHeight
    )
    val sourceBitmap = deviceImage.asAndroidBitmap()
    val fanTransform = rotor.projectedRotationMatrix(rotationPhase)
    val fanClip = rotor.clipPath()

    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        val checkpoint = nativeCanvas.save()
        try {
            nativeCanvas.clipPath(fanClip)
            nativeCanvas.concat(fanTransform)
            nativeCanvas.drawBitmap(
                sourceBitmap,
                null,
                destination,
                FAN_TEXTURE_PAINT
            )
        } finally {
            nativeCanvas.restoreToCount(checkpoint)
        }
    }
}

private data class CoolingFanRotorGeometry(
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float
) {
    fun clipPath(): Path {
        val clipRadiusX = radiusX * FAN_CLIP_SCALE
        val clipRadiusY = radiusY * FAN_CLIP_SCALE
        val path = Path().apply {
            addOval(
                RectF(
                    centerX - clipRadiusX,
                    centerY - clipRadiusY,
                    centerX + clipRadiusX,
                    centerY + clipRadiusY
                ),
                Path.Direction.CW
            )
        }
        path.transform(
            Matrix().apply {
                setRotate(FAN_PLANE_TILT_DEGREES, centerX, centerY)
            }
        )
        return path
    }

    fun projectedRotationMatrix(rotationPhase: Float): Matrix {
        val rotation = rotationPhase * FULL_CIRCLE_RADIANS
        val rotationCos = cos(rotation.toDouble()).toFloat()
        val rotationSin = sin(rotation.toDouble()).toFloat()
        val planeCos = cos(FAN_PLANE_TILT_RADIANS.toDouble()).toFloat()
        val planeSin = sin(FAN_PLANE_TILT_RADIANS.toDouble()).toFloat()
        val ellipseRatio = radiusY / radiusX

        // B = D * R * D^-1, with D representing the elliptical projection scale.
        val b00 = rotationCos
        val b01 = -rotationSin / ellipseRatio
        val b10 = ellipseRatio * rotationSin
        val b11 = rotationCos

        // M = R(tilt) * B * R(-tilt).
        val tilted00 = planeCos * b00 - planeSin * b10
        val tilted01 = planeCos * b01 - planeSin * b11
        val tilted10 = planeSin * b00 + planeCos * b10
        val tilted11 = planeSin * b01 + planeCos * b11

        val m00 = tilted00 * planeCos - tilted01 * planeSin
        val m01 = tilted00 * planeSin + tilted01 * planeCos
        val m10 = tilted10 * planeCos - tilted11 * planeSin
        val m11 = tilted10 * planeSin + tilted11 * planeCos
        val translateX = centerX - m00 * centerX - m01 * centerY
        val translateY = centerY - m10 * centerX - m11 * centerY

        return Matrix().apply {
            setValues(
                floatArrayOf(
                    m00, m01, translateX,
                    m10, m11, translateY,
                    0f, 0f, 1f
                )
            )
        }
    }
}

private val FAN_TEXTURE_PAINT = Paint(
    Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG
)

private const val FAN_CENTER_X = 0.459f
// Measured from the source artwork's hub rather than the cyan ring's bounding box. The previous
// value sat roughly seven source pixels high and made the transformed rotor orbit inside the ring.
private const val FAN_CENTER_Y = 0.299f
private const val FAN_RADIUS_X = 0.178f
private const val FAN_RADIUS_Y = 0.099f
private const val FAN_PLANE_TILT_DEGREES = -6.1f
private const val FAN_PLANE_TILT_RADIANS = -0.10646508f
private const val FAN_CLIP_SCALE = 0.985f
private const val FULL_CIRCLE_RADIANS = 6.2831855f
private const val HALF_DIVISOR = 2f
