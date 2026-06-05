package com.aqua.aqualight.data.devices.light.runtime

data class LightDeviceChannelMapping(
    val entries: List<Entry>
) {

    fun indexFor(
        semantic: LightChannelSemantic
    ): String? {
        return pwmIndexFor(semantic)
    }

    fun pwmIndexFor(
        semantic: LightChannelSemantic
    ): String? {
        return entryFor(semantic)?.pwmIndex
    }

    fun lightIndexFor(
        semantic: LightChannelSemantic
    ): String? {
        return entryFor(semantic)?.lightIndex
    }

    fun entryFor(
        semantic: LightChannelSemantic
    ): Entry? {
        return entries.firstOrNull { entry ->
            entry.semantic == semantic
        }
    }

    fun rgbwEntries(): List<Entry> {
        return entries
            .filter { entry ->
                entry.semantic == LightChannelSemantic.RED ||
                    entry.semantic == LightChannelSemantic.GREEN ||
                    entry.semantic == LightChannelSemantic.BLUE ||
                    entry.semantic == LightChannelSemantic.WHITE
            }
            .sortedBy { entry ->
                when (entry.semantic) {
                    LightChannelSemantic.RED -> 0
                    LightChannelSemantic.GREEN -> 1
                    LightChannelSemantic.BLUE -> 2
                    LightChannelSemantic.WHITE -> 3
                    LightChannelSemantic.UNKNOWN -> 99
                }
            }
    }

    fun hasAnyMappedChannel(): Boolean {
        return entries.any { entry ->
            entry.semantic != LightChannelSemantic.UNKNOWN
        }
    }

    data class Entry(
        val pwmIndex: String,
        val lightIndex: String,
        val gpioPwm: String,
        val pwmName: String,
        val pwmColor: Long,
        val semantic: LightChannelSemantic
    )
}