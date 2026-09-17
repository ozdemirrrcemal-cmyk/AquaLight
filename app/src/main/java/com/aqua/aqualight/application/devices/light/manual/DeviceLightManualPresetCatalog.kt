package com.aqua.aqualight.application.devices.light.manual

/** Frozen Android-owned Manual shortcuts from the pinned Light V1 firmware handoff. */
enum class DeviceLightManualPresetId {
    RED,
    GREEN,
    BLUE,
    FISH,
    SHRIMP,
    ALL
}

data class DeviceLightManualPreset(
    val id: DeviceLightManualPresetId,
    val scene: DeviceLightManualScene
)

object DeviceLightManualPresetCatalog {
    val presets: List<DeviceLightManualPreset> = listOf(
        preset(DeviceLightManualPresetId.RED, red = 85, green = 50, blue = 55, white = 35),
        preset(DeviceLightManualPresetId.GREEN, red = 60, green = 85, blue = 65, white = 40),
        preset(DeviceLightManualPresetId.BLUE, red = 50, green = 60, blue = 85, white = 35),
        preset(DeviceLightManualPresetId.FISH, red = 80, green = 45, blue = 70, white = 45),
        preset(DeviceLightManualPresetId.SHRIMP, red = 85, green = 70, blue = 65, white = 50),
        preset(DeviceLightManualPresetId.ALL, red = 70, green = 70, blue = 70, white = 70)
    )
}

private fun preset(
    id: DeviceLightManualPresetId,
    red: Int,
    green: Int,
    blue: Int,
    white: Int
) = DeviceLightManualPreset(
    id = id,
    scene = DeviceLightManualScene(
        linkedMapOf(
            DeviceLightManualChannel.RED to red,
            DeviceLightManualChannel.GREEN to green,
            DeviceLightManualChannel.BLUE to blue,
            DeviceLightManualChannel.WHITE to white
        )
    )
)
