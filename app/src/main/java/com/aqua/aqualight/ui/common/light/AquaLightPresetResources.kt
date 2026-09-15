package com.aqua.aqualight.ui.common.light

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.preset.DeviceLightPresetId

@StringRes
fun DeviceLightPresetId.labelResource(): Int = when (this) {
    DeviceLightPresetId.NATURAL_AQUARIUM ->
        R.string.device_light_manual_preset_natural_aquarium
    DeviceLightPresetId.PLANTED_AQUARIUM ->
        R.string.device_light_manual_preset_planted_aquarium
    DeviceLightPresetId.RED_PLANTS -> R.string.device_light_manual_preset_red_plants
    DeviceLightPresetId.VIVID_COLORS -> R.string.device_light_manual_preset_vivid_colors
    DeviceLightPresetId.LOW_TECH -> R.string.device_light_manual_preset_low_tech
    DeviceLightPresetId.AQUASCAPE -> R.string.device_light_manual_preset_aquascape
    DeviceLightPresetId.NEW_SETUP -> R.string.device_light_preset_new_setup
    DeviceLightPresetId.SHADE_PLANTS -> R.string.device_light_preset_shade_plants
}
