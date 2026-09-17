package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightPresetCatalog
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightPresetId
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightPresetScene
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannel
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualProtectionKind as ApplicationProtectionKind
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualControlSpec
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.labelResource

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

internal typealias DeviceLightManualPresetId = DeviceLightPresetId

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
    val initialLoading: Boolean = false
) {
    val controlsEnabled: Boolean
        get() = contentEnabled

    /** Live slider/step commands remain non-blocking; only the first authoritative read blocks. */
    val showGlobalLoading: Boolean
        get() = initialLoading
}

internal fun deviceLightManualInitialState(deviceUid: String) = DeviceLightManualControlUiState(
    deviceUid = deviceUid,
    presets = builtInManualPresets(),
    initialLoading = true
)

internal fun DeviceLightManualSnapshot.mergeInto(
    state: DeviceLightManualControlUiState,
    replaceScene: Boolean
): DeviceLightManualControlUiState {
    val resolvedChannels = if (replaceScene) {
        scene.channels.map { (channel, percent) -> channel.toUiState(percent) }
    } else {
        state.channels
    }
    val powerState = estimatedPowerWatts?.let { watts ->
        estimatedPowerRatio?.let { ratio -> DeviceLightManualPowerUiState(watts, ratio) }
    }
    val matchingPreset = if (replaceScene) {
        state.presets.firstOrNull { preset ->
            resolvedChannels.all { channel -> preset.scene[channel.id] == channel.percent }
        }?.id
    } else {
        state.selectedPreset
    }
    return state.copy(
        deviceUid = deviceUid,
        connectionVisualState = DeviceConnectionVisualState.ONLINE,
        channels = resolvedChannels,
        power = powerState,
        protection = protection?.let { value ->
            DeviceLightManualProtectionUiState(
                kind = value.kind.toUiKind(),
                effectivePercent = value.effectivePercent
            )
        },
        selectedPreset = matchingPreset,
        contentEnabled = true,
        initialLoading = false
    )
}

private fun DeviceLightManualChannel.toUiState(percent: Int) =
    DeviceLightManualChannelUiState(
        id = toUiId(),
        labelRes = when (this) {
            DeviceLightManualChannel.RED -> R.string.device_light_live_output_red
            DeviceLightManualChannel.GREEN -> R.string.device_light_live_output_green
            DeviceLightManualChannel.BLUE -> R.string.device_light_live_output_blue
            DeviceLightManualChannel.WHITE -> R.string.device_light_live_output_white
        },
        percent = percent
    )

internal fun DeviceLightManualChannel.toUiId(): DeviceLightManualChannelId = when (this) {
    DeviceLightManualChannel.RED -> DeviceLightManualChannelId.RED
    DeviceLightManualChannel.GREEN -> DeviceLightManualChannelId.GREEN
    DeviceLightManualChannel.BLUE -> DeviceLightManualChannelId.BLUE
    DeviceLightManualChannel.WHITE -> DeviceLightManualChannelId.WHITE
}

internal fun DeviceLightManualChannelId.toApplicationChannel(): DeviceLightManualChannel =
    when (this) {
        DeviceLightManualChannelId.RED -> DeviceLightManualChannel.RED
        DeviceLightManualChannelId.GREEN -> DeviceLightManualChannel.GREEN
        DeviceLightManualChannelId.BLUE -> DeviceLightManualChannel.BLUE
        DeviceLightManualChannelId.WHITE -> DeviceLightManualChannel.WHITE
    }

private fun ApplicationProtectionKind.toUiKind(): DeviceLightManualProtectionKind = when (this) {
    ApplicationProtectionKind.POWER_LIMITED -> DeviceLightManualProtectionKind.POWER_LIMITED
    ApplicationProtectionKind.THERMAL_LIMITED -> DeviceLightManualProtectionKind.THERMAL_LIMITED
    ApplicationProtectionKind.THERMAL_SHUTDOWN -> DeviceLightManualProtectionKind.THERMAL_SHUTDOWN
}

private fun builtInManualPresets() = DeviceLightPresetCatalog.manualPresets.map { preset ->
    manualPreset(preset.id, preset.id.labelResource(), preset.scene)
}

private fun manualPreset(
    id: DeviceLightManualPresetId,
    @StringRes labelRes: Int,
    scene: DeviceLightPresetScene
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
    AquaLightManualControlSpec.minimumPercent..AquaLightManualControlSpec.maximumPercent
