package com.aqua.aqualight.data.devices.light.manual

import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannel
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualFailure
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMutationResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualProtection
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualProtectionKind
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualScene
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightOutputReason
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightScene
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import kotlin.math.roundToInt

internal suspend fun DeviceRuntimeCommandOutcome<*>.confirmManualMutation(
    uid: DeviceUid,
    runtime: DeviceLightRuntimeRepository,
    expectedScene: DeviceLightScene
): DeviceLightManualMutationResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> {
        val status = runtime.currentStatus(uid)
            ?.takeIf { current -> current.confirms(expectedScene) }
            ?: runtime.requestStatus(uid)
                .takeIf { refresh -> refresh is DeviceRuntimeCommandOutcome.Success }
                ?.let { runtime.currentStatus(uid) }
                ?.takeIf { current -> current.confirms(expectedScene) }
        status?.toManualSnapshot(uid)
            ?.let(DeviceLightManualMutationResult::Success)
            ?: manualMutationFailure(DeviceLightManualFailure.UNAVAILABLE)
    }
    else -> manualMutationFailure(toManualFailure())
}

private fun DeviceLightStatus.confirms(expectedScene: DeviceLightScene): Boolean =
    mode == DeviceLightMode.MANUAL && manual.scene == expectedScene

internal fun DeviceLightStatus.toManualSnapshot(uid: DeviceUid): DeviceLightManualSnapshot {
    require(manual.scene.product == product)
    val estimatedPower = power.estimatedFixturePowerW
        ?.takeIf { power.available && power.estimatedFixturePowerAvailable }
        ?.roundToInt()
    val estimatedRatio = power.ratio
        ?.takeIf { power.available && power.ratioAvailable }
        ?.toFloat()
        ?.coerceIn(POWER_RATIO_MIN, POWER_RATIO_MAX)
    return DeviceLightManualSnapshot(
        deviceUid = uid.value,
        productKey = product.wireValue,
        scene = DeviceLightManualScene(
            product.sceneFields.associate { field ->
                field.toManualChannel() to manual.scene.percents.getValue(field)
            }
        ),
        estimatedPowerWatts = estimatedPower,
        estimatedPowerRatio = estimatedRatio,
        protection = toManualProtection()
    )
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

internal fun DeviceRuntimeCommandOutcome<*>.toManualFailure(): DeviceLightManualFailure = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> DeviceLightManualFailure.NOT_CONNECTED
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> DeviceLightManualFailure.UNSUPPORTED
    is DeviceRuntimeCommandOutcome.FirmwareError -> DeviceLightManualFailure.REJECTED
    is DeviceRuntimeCommandOutcome.ProtocolError -> DeviceLightManualFailure.INVALID_DATA
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> DeviceLightManualFailure.UNAVAILABLE
    is DeviceRuntimeCommandOutcome.Success -> error("A successful outcome has no failure.")
}

internal fun manualMutationFailure(failure: DeviceLightManualFailure) =
    DeviceLightManualMutationResult.Failed(failure)

private const val RED_PERCENT_FIELD = "redPercent"
private const val GREEN_PERCENT_FIELD = "greenPercent"
private const val BLUE_PERCENT_FIELD = "bluePercent"
private const val WHITE_PERCENT_FIELD = "whitePercent"
private const val MIN_SCALE_PERMILLE = 0
private const val FULL_SCALE_PERMILLE = 1_000
private const val PERMILLE_PER_PERCENT = 10
private const val POWER_RATIO_MIN = 0f
private const val POWER_RATIO_MAX = 1f
