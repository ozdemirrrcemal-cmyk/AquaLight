package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.hero

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.design.CoolingHeroGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.design.CoolingHeroMotion

/**
 * GPU water refraction over the approved hero artwork.
 *
 * The artwork remains the visual source of truth. API 33+ devices continuously resample that same
 * bitmap through AGSL, with a smooth quadrilateral mask and localized fan-impact displacement.
 * Older devices intentionally keep the reviewed static artwork instead of falling back to a
 * visibly segmented/sliced approximation.
 */
@Composable
internal fun CoolingWaterLayer(
    artwork: ImageBitmap,
    fanIntensity: Float,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        CoolingWaterRuntimeShaderLayer(
            artwork = artwork,
            fanIntensity = fanIntensity,
            modifier = modifier
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun CoolingWaterRuntimeShaderLayer(
    artwork: ImageBitmap,
    fanIntensity: Float,
    modifier: Modifier
) {
    val transition = rememberInfiniteTransition(label = "cooling-water-runtime-shader")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = CoolingHeroMotion.waterLoopDurationMillis,
                easing = LinearEasing
            )
        ),
        label = "cooling-water-runtime-shader-phase"
    )
    val bitmap = remember(artwork) { artwork.asAndroidBitmap() }
    val bitmapShader = remember(bitmap) {
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    val runtimeShader = remember(bitmapShader) {
        RuntimeShader(WATER_AGSL).apply {
            setInputShader(INPUT_SHADER, bitmapShader)
        }
    }
    val bitmapMatrix = remember { Matrix() }
    val paint = remember(runtimeShader) {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            shader = runtimeShader
        }
    }

    Canvas(modifier = modifier) {
        bitmapMatrix.reset()
        bitmapMatrix.setScale(
            size.width / bitmap.width.toFloat(),
            size.height / bitmap.height.toFloat()
        )
        bitmapShader.setLocalMatrix(bitmapMatrix)

        runtimeShader.setFloatUniform(RESOLUTION_UNIFORM, size.width, size.height)
        runtimeShader.setFloatUniform(PHASE_UNIFORM, phase)
        runtimeShader.setFloatUniform(FAN_INTENSITY_UNIFORM, fanIntensity.coerceIn(0f, 1f))
        runtimeShader.setFloatUniform(
            WATER_LEFT_RIGHT_UNIFORM,
            CoolingHeroGeometry.waterLeftRatio,
            CoolingHeroGeometry.waterRightRatio
        )
        runtimeShader.setFloatUniform(
            WATER_BACK_UNIFORM,
            CoolingHeroGeometry.waterBackLeftYRatio,
            CoolingHeroGeometry.waterBackRightYRatio
        )
        runtimeShader.setFloatUniform(
            WATER_FRONT_UNIFORM,
            CoolingHeroGeometry.waterFrontLeftYRatio,
            CoolingHeroGeometry.waterFrontRightYRatio
        )
        runtimeShader.setFloatUniform(
            IMPACT_UNIFORM,
            CoolingHeroGeometry.waterImpactXRatio,
            CoolingHeroGeometry.waterImpactYRatio
        )
        runtimeShader.setFloatUniform(
            DISPLACEMENT_UNIFORM,
            CoolingHeroMotion.waterAmbientDisplacementPx,
            CoolingHeroMotion.waterFanDisplacementPx,
            CoolingHeroMotion.waterImpactDisplacementPx
        )

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(
                0f,
                0f,
                size.width,
                size.height,
                paint
            )
        }
    }
}

private const val INPUT_SHADER = "image"
private const val RESOLUTION_UNIFORM = "resolution"
private const val PHASE_UNIFORM = "phase"
private const val FAN_INTENSITY_UNIFORM = "fanIntensity"
private const val WATER_LEFT_RIGHT_UNIFORM = "waterLeftRight"
private const val WATER_BACK_UNIFORM = "waterBack"
private const val WATER_FRONT_UNIFORM = "waterFront"
private const val IMPACT_UNIFORM = "impact"
private const val DISPLACEMENT_UNIFORM = "displacementPx"
private const val TWO_PI = 6.2831855f

private const val WATER_AGSL = """
uniform shader image;
uniform float2 resolution;
uniform float phase;
uniform float fanIntensity;
uniform float2 waterLeftRight;
uniform float2 waterBack;
uniform float2 waterFront;
uniform float2 impact;
uniform float3 displacementPx;

half4 main(float2 p) {
    float2 uv = p / resolution;
    float waterWidth = max(0.001, waterLeftRight.y - waterLeftRight.x);
    float xProgress = clamp((uv.x - waterLeftRight.x) / waterWidth, 0.0, 1.0);
    float topY = mix(waterBack.x, waterBack.y, xProgress);
    float bottomY = mix(waterFront.x, waterFront.y, xProgress);

    float xMask = smoothstep(waterLeftRight.x, waterLeftRight.x + 0.008, uv.x) *
        (1.0 - smoothstep(waterLeftRight.y - 0.008, waterLeftRight.y, uv.x));
    float yMask = smoothstep(topY, topY + 0.012, uv.y) *
        (1.0 - smoothstep(bottomY - 0.025, bottomY, uv.y));
    float mask = xMask * yMask;

    half4 base = image.eval(p);
    if (mask <= 0.001) {
        return base;
    }

    float localImpact = exp(-distance(uv, impact) * 8.8) * fanIntensity;
    float primary = sin(uv.x * 63.0 + phase * 1.08 + sin(uv.y * 34.0 - phase * 0.57) * 0.70);
    float secondary = sin((uv.x * 1.72 + uv.y * 2.18) * 43.0 - phase * 1.47);
    float tertiary = sin((uv.x * 3.10 - uv.y * 1.12) * 29.0 + phase * 0.82);

    float amplitude = displacementPx.x +
        displacementPx.y * fanIntensity +
        displacementPx.z * localImpact;
    float2 displacement = float2(
        (secondary * 0.62 + tertiary * 0.38) * amplitude,
        (primary * 0.74 + secondary * 0.26) * amplitude
    );

    half4 warped = image.eval(p + displacement);
    float sparkleWave = 0.5 + 0.5 * sin(
        uv.x * 91.0 - uv.y * 47.0 + phase * 1.85 + primary * 0.85
    );
    float shimmer = pow(max(sparkleWave, 0.0), 9.0) *
        (0.010 + fanIntensity * 0.020 + localImpact * 0.032);
    half4 lit = warped + half4(shimmer * 0.48, shimmer * 0.80, shimmer, 0.0);

    return mix(base, lit, mask * 0.92);
}
"""
