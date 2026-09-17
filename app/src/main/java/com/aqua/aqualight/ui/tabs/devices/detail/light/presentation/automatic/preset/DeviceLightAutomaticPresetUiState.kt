package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.preset

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightBuiltInPreset
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightPresetCatalog
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightPresetId

internal data class DeviceLightAutomaticPresetUiState(
    val presets: List<DeviceLightBuiltInPreset> = DeviceLightPresetCatalog.presets,
    val selectedPresetId: DeviceLightPresetId = DeviceLightPresetId.NATURAL_AQUARIUM
) {
    init {
        require(presets.any { preset -> preset.id == selectedPresetId })
    }
}

internal data class DeviceLightAutomaticPresetActions(
    val onPresetClick: (DeviceLightPresetId) -> Unit,
    val onCancelClick: () -> Unit,
    val onUseClick: (DeviceLightPresetId) -> Unit
)

internal object DeviceLightAutomaticPresetNavigation {
    const val RESULT_PRESET_ID = "device_light_automatic_preset_id"
}
