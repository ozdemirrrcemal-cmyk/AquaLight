package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

/**
 * Normalized emitter/glow regions of the existing device_light_hero_card artwork.
 * Coordinates scale with FillBounds, never with screen density, layout direction or text size.
 * These are masks over existing pixels, not newly drawn lamps or a replacement image.
 */
internal data class DeviceLightHeroEmitterRegion(
    val channel: DeviceLightHeroEmitterChannel,
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float
)

internal val deviceLightHeroEmitterRegions = listOf(
    DeviceLightHeroEmitterRegion(
        channel = DeviceLightHeroEmitterChannel.RED,
        centerX = 0.523f,
        centerY = 0.350f,
        radiusX = 0.070f,
        radiusY = 0.145f
    ),
    DeviceLightHeroEmitterRegion(
        channel = DeviceLightHeroEmitterChannel.GREEN,
        centerX = 0.605f,
        centerY = 0.355f,
        radiusX = 0.065f,
        radiusY = 0.145f
    ),
    DeviceLightHeroEmitterRegion(
        channel = DeviceLightHeroEmitterChannel.BLUE,
        centerX = 0.704f,
        centerY = 0.380f,
        radiusX = 0.073f,
        radiusY = 0.150f
    ),
    DeviceLightHeroEmitterRegion(
        channel = DeviceLightHeroEmitterChannel.WHITE,
        centerX = 0.804f,
        centerY = 0.398f,
        radiusX = 0.075f,
        radiusY = 0.160f
    )
)
