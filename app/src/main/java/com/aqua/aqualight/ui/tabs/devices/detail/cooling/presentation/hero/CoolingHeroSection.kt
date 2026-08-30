package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.hero

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.design.CoolingHeroGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.design.CoolingHeroMotion

@Immutable
internal data class CoolingHeroUiState(
    val fanSpeedFraction: Float? = null
)

@Composable
internal fun CoolingHeroSection(
    state: CoolingHeroUiState,
    modifier: Modifier = Modifier
) {
    val artwork = rememberCoolingHeroArtwork()
    val targetFanIntensity = state.fanSpeedFraction?.coerceIn(0f, 1f)
        ?: if (BuildConfig.DEBUG) {
            CoolingHeroMotion.debugTestFanFraction
        } else {
            0f
        }
    val fanIntensity by animateFloatAsState(
        targetValue = targetFanIntensity,
        animationSpec = tween(durationMillis = CoolingHeroMotion.intensityTransitionMillis),
        label = "cooling-hero-fan-intensity"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val resolvedWidth = minOf(maxWidth, CoolingHeroGeometry.maximumWidth)
        Box(
            modifier = Modifier
                .width(resolvedWidth)
                .aspectRatio(CoolingHeroGeometry.heroAspectRatio)
                .clip(CoolingHeroGeometry.heroShape)
        ) {
            Image(
                bitmap = artwork,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            CoolingWaterLayer(
                artwork = artwork,
                fanIntensity = fanIntensity,
                modifier = Modifier.fillMaxSize()
            )
            CoolingRotorLayer(
                artwork = artwork,
                fanIntensity = fanIntensity,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
