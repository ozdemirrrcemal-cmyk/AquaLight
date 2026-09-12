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
    internal val stateStore: DeviceLightRuntimeStateStore
) {
    constructor(gateway: DeviceRuntimeCommandGateway) : this(
        gateway = gateway,
        stateStore = DeviceLightRuntimeStateStore()
    )

    val states: StateFlow<Map<DeviceUid, DeviceLightStatus>> = stateStore.statuses

    fun currentStatus(deviceUid: DeviceUid): DeviceLightStatus? =
        stateStore.currentAuthoritativeStatus(deviceUid)

    internal fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ) = stateStore.beginGeneration(deviceUid, generation)

    internal fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) = stateStore.invalidate(deviceUid, generation)

    suspend fun requestStatus(deviceUid: DeviceUid): DeviceRuntimeCommandOutcome<DeviceLightStatus> {
        val outcome = gateway.execute(
            deviceUid,
            lightCommand(
                action = DeviceLightRuntimeContract.Action.STATUS_GET,
                parser = DeviceLightStatusParser::parse
            )
        )
        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            stateStore.recordStatus(deviceUid, outcome.generation, outcome.value)
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
        refreshStatus = true
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
        val status = stateStore.currentAuthoritativeStatus(deviceUid)
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
        val product = stateStore.currentAuthoritativeStatus(deviceUid)?.product
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
        val outcome = gateway.execute(
            deviceUid,
            lightCommand(
                action = command.action,
                dataFactory = command.dataFactory,
                parser = { data -> command.parser(data, product) }
            )
        )
        if (command.refreshStatus && outcome is DeviceRuntimeCommandOutcome.Success) {
            requestStatus(deviceUid)
        }
        return outcome
    }
}

internal data class DeviceLightProductCommand<T>(
    val action: String,
    val dataFactory: () -> JSONObject,
    val parser: (JSONObject, DeviceLightProduct) -> T,
    val refreshStatus: Boolean
)

internal fun DeviceLightRuntimeRepository.isAuthoritative(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration
): Boolean = stateStore.isStatusAuthoritative(deviceUid, generation)

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
