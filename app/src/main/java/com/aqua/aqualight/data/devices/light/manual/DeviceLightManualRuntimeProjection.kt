package com.aqua.aqualight.data.devices.light.manual

import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannel
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannelDescriptor
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMode
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualProtection
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualProtectionKind
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualScene
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightDisplayRgb
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightOutputReason
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightScene
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import kotlin.math.roundToInt

internal fun DeviceLightStatus.toManualSnapshot(
    uid: DeviceUid,
    firmwareWriteAuthoritative: Boolean = true
): DeviceLightManualSnapshot {
    require(manual.scene.product == product)
    val sortedChannels = channels.sortedBy { descriptor -> descriptor.order }
    val descriptors = sortedChannels.map { descriptor ->
        DeviceLightManualChannelDescriptor(
            channel = descriptor.percentField.toManualChannel(),
            key = descriptor.key,
            displayName = descriptor.displayName,
            displayColorRgb = descriptor.displayColorRgb,
            order = descriptor.order
        )
    }
    val estimatedLedPower = power.estimatedLedPowerW
        ?.takeIf { power.available }
    // Firmware power.ratio is fixture input power / fixture input limit, not an LED ratio.
    val estimatedLedPowerRatio = estimatedLedPower?.let { watts ->
        power.hardLedPowerLimitW
            ?.takeIf { limit -> power.available && limit > 0.0 }
            ?.let { limit -> (watts / limit).toFloat() }
            ?.coerceIn(POWER_RATIO_MIN, POWER_RATIO_MAX)
    }
    return DeviceLightManualSnapshot(
        deviceUid = uid.value,
        productKey = product.wireValue,
        activeMode = mode.toManualMode(),
        channelDescriptors = descriptors,
        scene = DeviceLightManualScene(
            sortedChannels.associate { descriptor ->
                descriptor.percentField.toManualChannel() to
                    manual.scene.percents.getValue(descriptor.percentField)
            }
        ),
        estimatedLedPowerWatts = estimatedLedPower?.roundToInt(),
        estimatedLedPowerRatio = estimatedLedPowerRatio,
        effectiveOutputDisplayColorRgb = color.displayRgb
            ?.takeIf { color.available }
            ?.toPackedRgb(),
        protection = toManualProtection(),
        firmwareWriteAuthoritative = firmwareWriteAuthoritative
    )
}

private fun DeviceLightMode.toManualMode(): DeviceLightManualMode = when (this) {
    DeviceLightMode.MANUAL -> DeviceLightManualMode.MANUAL
    DeviceLightMode.AUTO -> DeviceLightManualMode.AUTOMATIC
    DeviceLightMode.CUSTOM -> DeviceLightManualMode.CUSTOM
}

private fun DeviceLightStatus.toManualProtection(): DeviceLightManualProtection? = when {
    outputReason == DeviceLightOutputReason.THERMAL_SHUTDOWN -> DeviceLightManualProtection(
        DeviceLightManualProtectionKind.THERMAL_SHUTDOWN
    )
    scales.thermal < FULL_SCALE_PERMILLE -> DeviceLightManualProtection(
        kind = DeviceLightManualProtectionKind.THERMAL_LIMITED,
        effectivePercent = scales.thermal.toEffectivePercent()
    )
    power.limited || scales.powerLimit < FULL_SCALE_PERMILLE -> DeviceLightManualProtection(
        kind = DeviceLightManualProtectionKind.POWER_LIMITED,
        effectivePercent = minOf(power.limitScale, scales.powerLimit).toEffectivePercent()
    )
    else -> null
}

private fun Int.toEffectivePercent(): Int =
    coerceIn(MIN_SCALE_PERMILLE, FULL_SCALE_PERMILLE) / PERMILLE_PER_PERCENT

private fun DeviceLightDisplayRgb.toPackedRgb(): Int =
    (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue

internal fun DeviceLightManualScene.toRuntimeScene(product: DeviceLightProduct): DeviceLightScene {
    val expectedChannels = product.sceneFields.mapTo(linkedSetOf(), String::toManualChannel)
    require(channels.keys == expectedChannels)
    return DeviceLightScene(
        product = product,
        percents = product.sceneFields.associateWith { field ->
            channels.getValue(field.toManualChannel())
        }
    )
}

private fun String.toManualChannel(): DeviceLightManualChannel = when (this) {
    RED_PERCENT_FIELD -> DeviceLightManualChannel.RED
    GREEN_PERCENT_FIELD -> DeviceLightManualChannel.GREEN
    BLUE_PERCENT_FIELD -> DeviceLightManualChannel.BLUE
    WHITE_PERCENT_FIELD -> DeviceLightManualChannel.WHITE
    else -> error("Unsupported Light manual scene field: $this")
}

private const val RED_PERCENT_FIELD = "redPercent"
private const val GREEN_PERCENT_FIELD = "greenPercent"
private const val BLUE_PERCENT_FIELD = "bluePercent"
private const val WHITE_PERCENT_FIELD = "whitePercent"
private const val MIN_SCALE_PERMILLE = 0
private const val FULL_SCALE_PERMILLE = 1_000
private const val PERMILLE_PER_PERCENT = 10
private const val POWER_RATIO_MIN = 0f
private const val POWER_RATIO_MAX = 1f
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
