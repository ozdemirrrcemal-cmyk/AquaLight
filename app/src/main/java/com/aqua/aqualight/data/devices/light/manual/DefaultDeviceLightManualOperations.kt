package com.aqua.aqualight.data.devices.light.manual

import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannel
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualFailure
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMutationResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualOperations
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualProtection
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualProtectionKind
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualReadResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualScene
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManualSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightOutputReason
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightScene
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stateless projection and serialized mutation adapter over the central Light runtime owner. */
internal class DefaultDeviceLightManualOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightManualOperations {

    private val mutationMutex = Mutex()

    override fun observe(deviceUid: String): Flow<DeviceLightManualReadResult> {
        val uid = deviceUid.toUidOrNull()
        val runtime = uid?.let { devicesRepository.runtimeModules()?.light }
        return if (uid == null) {
            flowOf(readFailure(DeviceLightManualFailure.INVALID_DATA))
        } else if (runtime == null) {
            flowOf(readFailure(DeviceLightManualFailure.UNAVAILABLE))
        } else {
            runtime.stateRevision.map {
                runtime.currentStatus(uid)
                    ?.toManualSnapshot(uid)
                    ?.let(DeviceLightManualReadResult::Available)
                    ?: readFailure(DeviceLightManualFailure.NOT_CONNECTED)
            }.distinctUntilChanged()
        }
    }

    override suspend fun setScene(
        deviceUid: String,
        scene: DeviceLightManualScene
    ): DeviceLightManualMutationResult = mutate(deviceUid) { uid, runtime, status ->
        val requestedScene = scene.toRuntimeScene(status.product)
        runtime.setManual(uid, DeviceLightManualSetPayload(requestedScene))
            .confirmManualMutation(uid, runtime, requestedScene)
    }

    override suspend fun turnOff(
        deviceUid: String
    ): DeviceLightManualMutationResult = mutate(deviceUid) { uid, runtime, _ ->
        when (val outcome = runtime.manualOff(uid)) {
            is DeviceRuntimeCommandOutcome.Success -> outcome.confirmManualMutation(
                uid = uid,
                runtime = runtime,
                expectedScene = outcome.value.scene
            )
            else -> mutationFailure(outcome.toManualFailure())
        }
    }

    private suspend fun mutate(
        deviceUid: String,
        command: suspend (
            DeviceUid,
            DeviceLightRuntimeRepository,
            DeviceLightStatus
        ) -> DeviceLightManualMutationResult
    ): DeviceLightManualMutationResult = mutationMutex.withLock {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        val status = if (uid == null) null else runtime?.currentStatus(uid)
        when {
            uid == null -> mutationFailure(DeviceLightManualFailure.INVALID_DATA)
            runtime == null -> mutationFailure(DeviceLightManualFailure.UNAVAILABLE)
            status == null -> mutationFailure(DeviceLightManualFailure.NOT_CONNECTED)
            else -> try {
                command(uid, runtime, status)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutationFailure(DeviceLightManualFailure.INVALID_DATA)
            }
        }
    }
}

private suspend fun DeviceRuntimeCommandOutcome<*>.confirmManualMutation(
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
            ?: mutationFailure(DeviceLightManualFailure.UNAVAILABLE)
    }
    else -> mutationFailure(toManualFailure())
}

private fun DeviceLightStatus.confirms(expectedScene: DeviceLightScene): Boolean =
    mode == DeviceLightMode.MANUAL && manual.scene == expectedScene

private fun DeviceLightStatus.toManualSnapshot(uid: DeviceUid): DeviceLightManualSnapshot {
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

private fun DeviceLightManualScene.toRuntimeScene(product: DeviceLightProduct): DeviceLightScene {
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

private fun DeviceRuntimeCommandOutcome<*>.toManualFailure(): DeviceLightManualFailure = when (this) {
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

private fun String.toUidOrNull(): DeviceUid? = trim().takeIf(String::isNotBlank)?.let(::DeviceUid)

private fun readFailure(failure: DeviceLightManualFailure) =
    DeviceLightManualReadResult.Failed(failure)

private fun mutationFailure(failure: DeviceLightManualFailure) =
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
