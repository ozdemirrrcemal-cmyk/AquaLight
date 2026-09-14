package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.manual.DeviceLightBuiltInManualPreset
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualScenePercentages
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.common.light.AquaLightManualPreviewSpec

internal enum class DeviceLightManualChannelId {
    RED,
    GREEN,
    BLUE,
    WHITE
}

internal data class DeviceLightManualChannelUiState(
    val id: DeviceLightManualChannelId,
    @StringRes val labelRes: Int,
    val percent: Int
) {
    init {
        require(percent in PERCENT_RANGE)
    }
}

internal data class DeviceLightManualPowerUiState(
    val watts: Int,
    val ratio: Float
) {
    init {
        require(watts >= 0)
        require(ratio in 0f..1f)
    }
}

internal enum class DeviceLightManualPresetId {
    NATURAL_AQUARIUM,
    PLANTED_AQUARIUM,
    RED_PLANTS,
    VIVID_COLORS,
    LOW_TECH,
    AQUASCAPE
}

internal data class DeviceLightManualPresetUiState(
    val id: DeviceLightManualPresetId,
    @StringRes val labelRes: Int,
    val scene: Map<DeviceLightManualChannelId, Int>
) {
    init {
        require(scene.keys == DeviceLightManualChannelId.entries.toSet())
        require(scene.values.all { percent -> percent in PERCENT_RANGE })
    }
}

internal enum class DeviceLightManualProtectionKind {
    POWER_LIMITED,
    THERMAL_LIMITED,
    THERMAL_SHUTDOWN
}

/** Null protection means that the Manual protection banner must not be rendered. */
internal data class DeviceLightManualProtectionUiState(
    val kind: DeviceLightManualProtectionKind,
    val effectivePercent: Int? = null
) {
    init {
        require(effectivePercent == null || effectivePercent in PERCENT_RANGE)
        require(kind != DeviceLightManualProtectionKind.THERMAL_SHUTDOWN || effectivePercent == null)
    }
}

internal data class DeviceLightManualControlUiState(
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState? = null,
    val channels: List<DeviceLightManualChannelUiState> = emptyList(),
    val power: DeviceLightManualPowerUiState? = null,
    val presets: List<DeviceLightManualPresetUiState> = emptyList(),
    val selectedPreset: DeviceLightManualPresetId? = null,
    val protection: DeviceLightManualProtectionUiState? = null,
    val contentEnabled: Boolean = false,
    val showGlobalLoading: Boolean = false
)

/** Presentation-only fixture. Authoritative device data will replace this in integration work. */
internal fun deviceLightManualPreviewState(deviceUid: String) = DeviceLightManualControlUiState(
    deviceUid = deviceUid,
    connectionVisualState = DeviceConnectionVisualState.ONLINE,
    channels = listOf(
        DeviceLightManualChannelUiState(
            DeviceLightManualChannelId.RED,
            R.string.device_light_live_output_red,
            AquaLightManualPreviewSpec.redPercent
        ),
        DeviceLightManualChannelUiState(
            DeviceLightManualChannelId.GREEN,
            R.string.device_light_live_output_green,
            AquaLightManualPreviewSpec.greenPercent
        ),
        DeviceLightManualChannelUiState(
            DeviceLightManualChannelId.BLUE,
            R.string.device_light_live_output_blue,
            AquaLightManualPreviewSpec.bluePercent
        ),
        DeviceLightManualChannelUiState(
            DeviceLightManualChannelId.WHITE,
            R.string.device_light_live_output_white,
            AquaLightManualPreviewSpec.whitePercent
        )
    ),
    power = DeviceLightManualPowerUiState(
        watts = AquaLightManualPreviewSpec.estimatedPowerWatts,
        ratio = AquaLightManualPreviewSpec.estimatedPowerRatio
    ),
    presets = builtInManualPresets(),
    contentEnabled = true
)

private fun builtInManualPresets() = listOf(
    manualPreset(
        DeviceLightManualPresetId.NATURAL_AQUARIUM,
        R.string.device_light_manual_preset_natural_aquarium,
        DeviceLightBuiltInManualPreset.NATURAL_AQUARIUM.scene
    ),
    manualPreset(
        DeviceLightManualPresetId.PLANTED_AQUARIUM,
        R.string.device_light_manual_preset_planted_aquarium,
        DeviceLightBuiltInManualPreset.PLANTED_AQUARIUM.scene
    ),
    manualPreset(
        DeviceLightManualPresetId.RED_PLANTS,
        R.string.device_light_manual_preset_red_plants,
        DeviceLightBuiltInManualPreset.RED_PLANTS.scene
    ),
    manualPreset(
        DeviceLightManualPresetId.VIVID_COLORS,
        R.string.device_light_manual_preset_vivid_colors,
        DeviceLightBuiltInManualPreset.VIVID_COLORS.scene
    ),
    manualPreset(
        DeviceLightManualPresetId.LOW_TECH,
        R.string.device_light_manual_preset_low_tech,
        DeviceLightBuiltInManualPreset.LOW_TECH.scene
    ),
    manualPreset(
        DeviceLightManualPresetId.AQUASCAPE,
        R.string.device_light_manual_preset_aquascape,
        DeviceLightBuiltInManualPreset.AQUASCAPE.scene
    )
)

private fun manualPreset(
    id: DeviceLightManualPresetId,
    @StringRes labelRes: Int,
    scene: DeviceLightManualScenePercentages
) = DeviceLightManualPresetUiState(
    id = id,
    labelRes = labelRes,
    scene = mapOf(
        DeviceLightManualChannelId.RED to scene.red,
        DeviceLightManualChannelId.GREEN to scene.green,
        DeviceLightManualChannelId.BLUE to scene.blue,
        DeviceLightManualChannelId.WHITE to scene.white
    )
)

internal val PERCENT_RANGE =
    AquaLightManualPreviewSpec.minimumPercent..AquaLightManualPreviewSpec.maximumPercent
