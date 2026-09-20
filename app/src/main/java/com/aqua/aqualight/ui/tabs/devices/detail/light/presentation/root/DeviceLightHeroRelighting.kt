package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas

@Composable
internal fun rememberHeroIllumination(
    illumination: DeviceLightHeroIllumination,
    outputActive: Boolean
): State<DeviceLightHeroIllumination> {
    // Start at the first real sample (no bright flash/replayed sunrise after navigation).
    // New samples retarget one vector together; an explicit device-off cancels the fade at once.
    return animateValueAsState(
        targetValue = illumination,
        typeConverter = heroIlluminationConverter,
        animationSpec = if (outputActive) {
            tween(durationMillis = HERO_TRANSITION_MILLIS, easing = LinearEasing)
        } else {
            snap()
        },
        label = "DeviceLightHeroWRGB"
    )
}

internal fun Modifier.relightHero(
    illumination: State<DeviceLightHeroIllumination>,
    outputActive: Boolean
): Modifier = drawWithCache {
    val relighter = DeviceLightHeroRelighter(size.width, size.height)
    onDrawWithContent {
        // Read animation state only in drawing: no per-frame layout or image decoding.
        val current = if (outputActive) illumination.value else DeviceLightHeroIllumination.dark
        relighter.draw(drawContext.canvas.nativeCanvas, current) { drawContent() }
    }
}

private val heroIlluminationConverter = TwoWayConverter<DeviceLightHeroIllumination, AnimationVector4D>(
    convertToVector = { value -> AnimationVector4D(value.red, value.green, value.blue, value.white) },
    convertFromVector = { vector ->
        DeviceLightHeroIllumination(vector.v1, vector.v2, vector.v3, vector.v4)
    }
)

private const val HERO_TRANSITION_MILLIS = 350
