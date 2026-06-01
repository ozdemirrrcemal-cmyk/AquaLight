package com.aqua.aqualight.ui.tabs.devices.detail.light.model

import androidx.annotation.StringRes
import com.aqua.aqualight.R

enum class TemporaryLightSceneOption(
    @StringRes val titleRes: Int
) {
    PHOTO(
        titleRes = R.string.light_temp_scene_photo
    ),

    MAINTENANCE(
        titleRes = R.string.light_temp_scene_maintenance
    ),

    EVENING(
        titleRes = R.string.light_temp_scene_evening
    ),

    MOONLIGHT(
        titleRes = R.string.light_temp_scene_moonlight
    )
}