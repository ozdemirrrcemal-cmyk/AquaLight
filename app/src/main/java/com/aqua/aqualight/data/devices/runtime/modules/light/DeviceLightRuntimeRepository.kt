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

    suspend fun requestAutoPrograms(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightAutoPrograms> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.AUTO_PROGRAMS_GET,
        parser = DeviceLightMutationParser::parseAutoPrograms
    )

    suspend fun createAutoProgram(
        deviceUid: DeviceUid,
        payload: DeviceLightAutoProgramCreatePayload
    ): DeviceRuntimeCommandOutcome<DeviceLightAutoProgramMutationResult> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.AUTO_PROGRAM_CREATE,
        dataFactory = payload::toJson,
        parser = { data, product ->
            DeviceLightCommandValidation.requireProduct(payload.scene.product, product)
            DeviceLightMutationParser.parseAutoProgramMutation(data, product)
        },
        refreshStatus = true
    )

    suspend fun updateAutoProgram(
        deviceUid: DeviceUid,
        payload: DeviceLightAutoProgramUpdatePayload
    ): DeviceRuntimeCommandOutcome<DeviceLightAutoProgramMutationResult> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.AUTO_PROGRAM_UPDATE,
        dataFactory = payload::toJson,
        parser = { data, product ->
            DeviceLightCommandValidation.requireProduct(payload.scene.product, product)
            DeviceLightMutationParser.parseAutoProgramMutation(data, product)
        },
        refreshStatus = true
    )

    suspend fun setAutoProgramEnabled(
        deviceUid: DeviceUid,
        payload: DeviceLightAutoProgramEnabledSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightAutoProgramMutationResult> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.AUTO_PROGRAM_ENABLED_SET,
        dataFactory = payload::toJson,
        parser = DeviceLightMutationParser::parseAutoProgramMutation,
        refreshStatus = true
    )

    suspend fun deleteAutoProgram(
        deviceUid: DeviceUid,
        payload: DeviceLightAutoProgramDeletePayload
    ): DeviceRuntimeCommandOutcome<DeviceLightAutoProgramDeleteResult> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.AUTO_PROGRAM_DELETE,
        dataFactory = payload::toJson,
        parser = { data, _ -> DeviceLightMutationParser.parseAutoProgramDelete(data) },
        refreshStatus = true
    )

    suspend fun requestCustom(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightCustomDocument> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.CUSTOM_GET,
        parser = DeviceLightMutationParser::parseCustom
    )

    suspend fun installCustom(
        deviceUid: DeviceUid,
        payload: DeviceLightCustomInstallPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightCustomDocument> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.CUSTOM_INSTALL,
        dataFactory = payload::toJson,
        parser = { data, product ->
            DeviceLightCommandValidation.requireProduct(payload.points.first().scene.product, product)
            DeviceLightMutationParser.parseCustom(data, product)
        },
        refreshStatus = true
    )

    suspend fun requestAcclimationStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightAcclimationStatus> = acclimationCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.ACCLIMATION_STATUS_GET,
        parser = DeviceLightMutationParser::parseAcclimation
    )

    suspend fun startAcclimation(
        deviceUid: DeviceUid,
        payload: DeviceLightAcclimationStartPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightAcclimationStatus> = acclimationCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.ACCLIMATION_START,
        dataFactory = payload::toJson,
        parser = DeviceLightMutationParser::parseAcclimation,
        refreshStatus = true
    )

    suspend fun stopAcclimation(
        deviceUid: DeviceUid,
        payload: DeviceLightAcclimationStopPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightAcclimationStatus> = acclimationCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.ACCLIMATION_STOP,
        dataFactory = payload::toJson,
        parser = DeviceLightMutationParser::parseAcclimation,
        refreshStatus = true
    )

    suspend fun requestGraph(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightGraph> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.GRAPH_GET,
        parser = DeviceLightMutationParser::parseGraph
    )

    suspend fun setPreview(
        deviceUid: DeviceUid,
        payload: DeviceLightPreviewSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightPreviewResult> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.PREVIEW_SET,
        dataFactory = payload::toJson,
        parser = { data, product ->
            if (payload is DeviceLightPreviewSetPayload.Scene) {
                DeviceLightCommandValidation.requireProduct(payload.scene.product, product)
            }
            DeviceLightMutationParser.parsePreviewSet(data)
        },
        refreshStatus = true
    )

    suspend fun clearPreview(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightPreviewResult> = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.PREVIEW_CLEAR,
        parser = { data, _ -> DeviceLightMutationParser.parsePreviewClear(data) },
        refreshStatus = true
    )

    private suspend fun <T> acclimationCommand(
        deviceUid: DeviceUid,
        action: String,
        dataFactory: () -> JSONObject = ::JSONObject,
        parser: (JSONObject, DeviceLightProduct) -> T,
        refreshStatus: Boolean = false
    ): DeviceRuntimeCommandOutcome<T> {
        val status = stateStore.currentAuthoritativeStatus(deviceUid)
            ?: return unsupported(deviceUid, action)
        if (
            status.product != DeviceLightProduct.WRGB_PRO_ELITE ||
            !status.features.acclimation ||
            !status.acclimation.supported
        ) {
            return unsupported(deviceUid, action)
        }
        return executeProductCommand(deviceUid, action, dataFactory, parser, refreshStatus, status.product)
    }

    private suspend fun <T> productCommand(
        deviceUid: DeviceUid,
        action: String,
        dataFactory: () -> JSONObject = ::JSONObject,
        parser: (JSONObject, DeviceLightProduct) -> T,
        refreshStatus: Boolean = false
    ): DeviceRuntimeCommandOutcome<T> {
        val product = stateStore.currentAuthoritativeStatus(deviceUid)?.product
            ?: return unsupported(deviceUid, action)
        return executeProductCommand(deviceUid, action, dataFactory, parser, refreshStatus, product)
    }

    private suspend fun <T> executeProductCommand(
        deviceUid: DeviceUid,
        action: String,
        dataFactory: () -> JSONObject,
        parser: (JSONObject, DeviceLightProduct) -> T,
        refreshStatus: Boolean,
        product: DeviceLightProduct
    ): DeviceRuntimeCommandOutcome<T> {
        val outcome = gateway.execute(
            deviceUid,
            lightCommand(
                action = action,
                dataFactory = dataFactory,
                parser = { data -> parser(data, product) }
            )
        )
        if (refreshStatus && outcome is DeviceRuntimeCommandOutcome.Success) {
            requestStatus(deviceUid)
        }
        return outcome
    }
}

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
