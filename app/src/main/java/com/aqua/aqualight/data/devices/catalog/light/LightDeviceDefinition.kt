package com.aqua.aqualight.data.devices.catalog.light

import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition

data class LightDeviceDefinition(
    val base: AquaDeviceDefinition,
    val channels: List<LightChannelDefinition>,
    val lightFeatures: Set<LightFeature>,

    val defaultSchedulePointCount: Int = 4,
    val maxSchedulePointCount: Int = 24,

    /**
     * true ise her kanal kendi zaman çizelgesini destekleyebilir.
     * false ise cihaz tek genel schedule mantığıyla çalışır.
     */
    val supportsIndependentChannelSchedule: Boolean = true
) {
    init {
        require(channels.isNotEmpty()) {
            "Light device must define at least one channel."
        }

        require(channels.map { it.id }.distinct().size == channels.size) {
            "Light channel ids must be unique."
        }

        require(channels.map { it.firmwareChannelIndex }.distinct().size == channels.size) {
            "Firmware channel indexes must be unique."
        }

        require(defaultSchedulePointCount > 0) {
            "defaultSchedulePointCount must be > 0."
        }

        require(maxSchedulePointCount >= defaultSchedulePointCount) {
            "maxSchedulePointCount must be >= defaultSchedulePointCount."
        }
    }

    fun supports(
        feature: LightFeature
    ): Boolean {
        return lightFeatures.contains(feature)
    }

    fun orderedChannels(): List<LightChannelDefinition> {
        return channels.sortedBy { channel ->
            channel.order
        }
    }
}