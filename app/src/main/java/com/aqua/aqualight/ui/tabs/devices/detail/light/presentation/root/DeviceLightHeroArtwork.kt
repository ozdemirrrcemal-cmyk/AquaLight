package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.res.painterResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot

@Composable
internal fun DeviceLightHeroArtwork(
    channels: List<DeviceLightChannelOutputSnapshot>,
    outputActive: Boolean?,
    modifier: Modifier = Modifier
) {
    val target = remember(channels, outputActive) {
        resolveHeroLightFrame(
            effectivePercents = channels.associate { channel ->
                channel.key to channel.effectivePercent
            },
            outputActive = outputActive
        )
    }
    val animatedChannels = animateValueAsState(
        targetValue = target.channels,
        typeConverter = heroLightChannelsConverter,
        animationSpec = tween(OUTPUT_TRANSITION_MILLIS, easing = LinearEasing),
        label = "hero-effective-output"
    )
    val artwork = painterResource(R.drawable.device_light_hero_card)
    Box(
        modifier = modifier.drawWithCache {
            val renderer = HeroAquariumRenderer(size)
            onDrawBehind {
                // Read animation state in draw, not composition: text and layout stay still.
                renderer.draw(this, artwork, animatedChannels.value, target.profile)
            }
        }
    )
}

private val heroLightChannelsConverter = TwoWayConverter<HeroLightChannels, AnimationVector4D>(
    convertToVector = { channels ->
        AnimationVector4D(channels.red, channels.green, channels.blue, channels.white)
    },
    convertFromVector = { vector ->
        HeroLightChannels(vector.v1, vector.v2, vector.v3, vector.v4)
    }
)

private const val OUTPUT_TRANSITION_MILLIS = 320
