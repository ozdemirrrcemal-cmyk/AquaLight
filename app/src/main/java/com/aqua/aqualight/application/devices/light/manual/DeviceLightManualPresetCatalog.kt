package com.aqua.aqualight.application.devices.light.manual

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightPresetCatalog
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightPresetId
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightPresetScene

typealias DeviceLightManualPresetId = DeviceLightPresetId

data class DeviceLightManualPreset(
    val id: DeviceLightManualPresetId,
    val scene: DeviceLightManualScene
)

object DeviceLightManualPresetCatalog {
    /** Android-owned shortcuts projected from the single central Light preset catalog. */
    val presets: List<DeviceLightManualPreset> = DeviceLightPresetCatalog.manualPresets.map { preset ->
        DeviceLightManualPreset(
            id = preset.id,
            scene = preset.scene.toManualScene()
        )
    }
}

private fun DeviceLightPresetScene.toManualScene() = DeviceLightManualScene(
    linkedMapOf(
        DeviceLightManualChannel.RED to red,
        DeviceLightManualChannel.GREEN to green,
        DeviceLightManualChannel.BLUE to blue,
        DeviceLightManualChannel.WHITE to white
    )
)
