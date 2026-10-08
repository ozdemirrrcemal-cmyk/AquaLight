package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** One product-neutral Light V1 data source for WRGB Pro Elite and RGB Pro Slim. */
class DeviceLightRuntimeRepository internal constructor(
    private val gateway: DeviceRuntimeCommandGateway,
    internal val stateOwner: DeviceLightRuntimeStateOwner,
    internal val operationGate: DeviceLightDeviceOperationGate = DeviceLightDeviceOperationGate()
) {
    val states: StateFlow<Map<DeviceUid, DeviceLightStatus>> = stateOwner.statuses
    val stateRevision: StateFlow<Long> = stateOwner.stateRevision

    fun currentStatus(deviceUid: DeviceUid): DeviceLightStatus? =
        stateOwner.currentStatus(deviceUid, DeviceLightStatusReadAuthority.AUTHORITATIVE)

    internal fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ) = stateOwner.beginGeneration(deviceUid, generation)

    internal fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) = stateOwner.invalidate(deviceUid, generation)

    suspend fun requestStatus(deviceUid: DeviceUid): DeviceRuntimeCommandOutcome<DeviceLightStatus> {
        val requestToken = stateOwner.beginStatusRequest(deviceUid)
        val outcome = gateway.execute(
            deviceUid,
            lightCommand(
                action = DeviceLightRuntimeContract.Action.STATUS_GET,
                parser = DeviceLightStatusParser::parse
            )
        )
        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            stateOwner.recordStatus(deviceUid, outcome.generation, outcome.value, requestToken)
        }
        return outcome
    }

    suspend fun setControl(
        deviceUid: DeviceUid,
        payload: DeviceLightControlSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightControlSetResult> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.CONTROL_SET,
        dataFactory = payload::toJson,
        parser = { data, _ -> DeviceLightMutationParser.parseControl(data) },
        refreshStatus = false
    )

    suspend fun setManual(
        deviceUid: DeviceUid,
        payload: DeviceLightManualSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightManualSetResult> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.MANUAL_SET,
        dataFactory = payload::toJson,
        parser = { data, product ->
            DeviceLightCommandValidation.requireProduct(payload.scene.product, product)
            DeviceLightMutationParser.parseManual(data, product)
        },
        refreshStatus = true
    )

    suspend fun manualOff(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightManualSetResult> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.MANUAL_OFF,
        parser = DeviceLightMutationParser::parseManual,
        refreshStatus = true
    )

    internal suspend fun <T> acclimationCommand(
        deviceUid: DeviceUid,
        action: String,
        dataFactory: () -> JSONObject = ::JSONObject,
        parser: (JSONObject, DeviceLightProduct) -> T,
        refreshStatus: Boolean = false
    ): DeviceRuntimeCommandOutcome<T> {
        val status = currentStatus(deviceUid)
        val supported = status != null &&
            status.product == DeviceLightProduct.WRGB_PRO_ELITE &&
            status.features.acclimation &&
            status.acclimation.supported
        return if (supported) {
            executeProductCommand(
                deviceUid = deviceUid,
                product = checkNotNull(status).product,
                command = DeviceLightProductCommand(action, dataFactory, parser, refreshStatus)
            )
        } else {
            unsupported(deviceUid, action)
        }
    }

    internal suspend fun <T> productCommand(
        deviceUid: DeviceUid,
        action: String,
        dataFactory: () -> JSONObject = ::JSONObject,
        parser: (JSONObject, DeviceLightProduct) -> T,
        refreshStatus: Boolean = false
    ): DeviceRuntimeCommandOutcome<T> {
        val product = currentStatus(deviceUid)?.product
            ?: return unsupported(deviceUid, action)
        return executeProductCommand(
            deviceUid = deviceUid,
            product = product,
            command = DeviceLightProductCommand(action, dataFactory, parser, refreshStatus)
        )
    }

    private suspend fun <T> executeProductCommand(
        deviceUid: DeviceUid,
        product: DeviceLightProduct,
        command: DeviceLightProductCommand<T>
    ): DeviceRuntimeCommandOutcome<T> {
        if (command.action.endsWith(".get")) {
            return executeProductCommandRaw(deviceUid, product, command)
        }
        // A queued user intent must not execute against a replacement connection generation.
        val expectedGeneration = currentConnectionGeneration(deviceUid)
        return operationGate.withMutation(deviceUid) {
            if (
                expectedGeneration != null &&
                isCurrentGeneration(deviceUid, expectedGeneration) &&
                currentStatus(deviceUid)?.product == product
            ) {
                executeProductCommandRaw(deviceUid, product, command)
            } else {
                unsupported(deviceUid, command.action)
            }
        }
    }

    private suspend fun <T> executeProductCommandRaw(
        deviceUid: DeviceUid,
        product: DeviceLightProduct,
        command: DeviceLightProductCommand<T>
    ): DeviceRuntimeCommandOutcome<T> {
        val outcome = gateway.execute(
            deviceUid,
            lightCommand(
                action = command.action,
                dataFactory = command.dataFactory,
                parser = { data -> command.parser(data, product) }
            )
        )
        if (command.refreshStatus && outcome is DeviceRuntimeCommandOutcome.Success) {
            val statusOutcome = requestStatus(deviceUid)
            if (statusOutcome is DeviceRuntimeCommandOutcome.Success) {
                requestGraph(deviceUid)
            }
        }
        return outcome
    }
}

internal fun DeviceLightRuntimeRepository.currentStatus(
    deviceUid: DeviceUid,
    authority: DeviceLightStatusReadAuthority
): DeviceLightStatus? = stateOwner.currentStatus(deviceUid, authority)

internal data class DeviceLightProductCommand<T>(
    val action: String,
    val dataFactory: () -> JSONObject,
    val parser: (JSONObject, DeviceLightProduct) -> T,
    val refreshStatus: Boolean
)

internal fun DeviceLightRuntimeRepository.isCurrentGeneration(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration
): Boolean = stateOwner.currentGeneration(deviceUid) == generation

internal fun DeviceLightRuntimeRepository.currentConnectionGeneration(
    deviceUid: DeviceUid
): DeviceRuntimeConnectionGeneration? = stateOwner.currentGeneration(deviceUid)

internal fun DeviceLightRuntimeRepository.isAuthoritative(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration
): Boolean = stateOwner.isAuthoritative(
    DeviceLightRuntimeProjection.STATUS,
    deviceUid,
    generation
)

internal fun DeviceLightRuntimeRepository.isGraphAuthoritative(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration
): Boolean = stateOwner.isAuthoritative(
    DeviceLightRuntimeProjection.GRAPH,
    deviceUid,
    generation
)

private fun <T> lightCommand(
    action: String,
    dataFactory: () -> JSONObject = ::JSONObject,
    parser: (JSONObject) -> T
): DeviceRuntimeJsonCommand<T> = DeviceRuntimeJsonCommand(
    module = DeviceLightRuntimeContract.MODULE,
    action = action,
    dataFactory = dataFactory,
    successParser = parser
)

private fun unsupported(
    deviceUid: DeviceUid,
    action: String
): DeviceRuntimeCommandOutcome.UnsupportedByDevice =
    DeviceRuntimeCommandOutcome.UnsupportedByDevice(
        deviceUid = deviceUid,
        module = DeviceLightRuntimeContract.MODULE,
        action = action
    )
