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
    DeviceLightHeroEmitterRegion(DeviceLightHeroEmitterChannel.RED, 0.523f, 0.350f, 0.070f, 0.145f),
    DeviceLightHeroEmitterRegion(DeviceLightHeroEmitterChannel.GREEN, 0.605f, 0.355f, 0.065f, 0.145f),
    DeviceLightHeroEmitterRegion(DeviceLightHeroEmitterChannel.BLUE, 0.704f, 0.380f, 0.073f, 0.150f),
    DeviceLightHeroEmitterRegion(DeviceLightHeroEmitterChannel.WHITE, 0.804f, 0.398f, 0.075f, 0.160f)
)
