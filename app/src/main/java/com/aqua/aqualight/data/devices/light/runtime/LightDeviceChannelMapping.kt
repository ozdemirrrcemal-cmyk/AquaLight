package com.aqua.aqualight.data.devices.light.runtime

data class LightDeviceChannelMapping(
    val entries: List<Entry>
) {

    fun indexFor(
        semantic: LightChannelSemantic
    ): String? {
        return entries.firstOrNull { entry ->
            entry.semantic == semantic
        }?.lightIndex
    }

    fun hasAnyMappedChannel(): Boolean {
        return entries.any { entry ->
            entry.semantic != LightChannelSemantic.UNKNOWN
        }
    }

    data class Entry(
        val lightIndex: String,
        val gpioPwm: String,
        val pwmName: String,
        val pwmColor: Long,
        val semantic: LightChannelSemantic
    )
}