package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeDiagnosticRecorder
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Correlated facade for all authenticated Dosing actions and full-list config helpers. */
@Suppress("TooManyFunctions")
class DeviceDosingRuntimeRepository internal constructor(
    private val gateway: DeviceRuntimeCommandGateway,
    private val stateStore: DeviceDosingRuntimeStateStore,
    private val accessProvider: (DeviceUid) -> DeviceDosingRuntimeAccess
) {
    val states: StateFlow<Map<DeviceUid, DeviceDosingRuntimeState>> = stateStore.states

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceDosingStatus> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi) {
            return dosingUnsupported(
                deviceUid,
                DeviceDosingRuntimeContract.Action.STATUS_GET
            ).also(DeviceRuntimeDiagnosticRecorder::recordOutcome)
        }
        return gateway.execute(
            deviceUid,
            dosingJsonCommand(
                action = DeviceDosingRuntimeContract.Action.STATUS_GET,
                parser = { data ->
                    DeviceDosingStatusParser.parse(data).also { status ->
                        DeviceDosingCommandValidation.validateStatus(status, access)
                    }
                }
            )
        ).recordStatus(deviceUid, stateStore)
            .also(DeviceRuntimeDiagnosticRecorder::recordOutcome)
    }

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceDosingConfigApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingConfigApplyResult> {
        val access = accessProvider(deviceUid)
        if (configUnsupported(payload, access)) {
            return dosingUnsupported(deviceUid, DeviceDosingRuntimeContract.Action.CONFIG_APPLY)
        }
        val status = states.value[deviceUid]?.status
        return gateway.execute(
            deviceUid,
            dosingJsonCommand(
                action = DeviceDosingRuntimeContract.Action.CONFIG_APPLY,
                dataFactory = {
                    DeviceDosingCommandValidation.validateConfigRequest(payload, status, access)
                    payload.toJson()
                },
                parser = { data ->
                    DeviceDosingMutationParser.parseConfigApply(data).also { result ->
                        DeviceDosingCommandValidation.validateConfigResult(
                            payload,
                            result,
                            status,
                            access
                        )
                    }
                }
            )
        ).recordMutation(deviceUid, stateStore)
    }

    suspend fun primeStart(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingPumpCommandResult> = executePumpCommand(
        deviceUid = deviceUid,
        channelKey = channelKey,
        action = DeviceDosingRuntimeContract.Action.PRIME_START,
        supported = { access -> access.supportsPrime },
        parser = DeviceDosingMutationParser::parsePrimeStart
    )

    suspend fun primeStop(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingPumpCommandResult> = executePumpCommand(
        deviceUid = deviceUid,
        channelKey = channelKey,
        action = DeviceDosingRuntimeContract.Action.PRIME_STOP,
        supported = { access -> access.supportsPrime },
        parser = DeviceDosingMutationParser::parsePrimeStop
    )

    suspend fun calibrationStart(
        deviceUid: DeviceUid,
        payload: DeviceDosingCalibrationStartPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingCalibrationStartResult> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi || !access.supportsCalibrationWorkflow) {
            return dosingUnsupported(
                deviceUid,
                DeviceDosingRuntimeContract.Action.CALIBRATION_START
            )
        }
        val status = states.value[deviceUid]?.status
        return gateway.execute(
            deviceUid,
            dosingJsonCommand(
                action = DeviceDosingRuntimeContract.Action.CALIBRATION_START,
                dataFactory = {
                    DeviceDosingCommandValidation.validateCalibrationStartRequest(
                        payload.normalizedChannelKey,
                        status,
                        access
                    )
                    payload.toJson()
                },
                parser = { data ->
                    DeviceDosingMutationParser.parseCalibrationStart(data).also { result ->
                        DeviceDosingCommandValidation.validateCalibrationStartResult(
                            payload,
                            result
                        )
                    }
                }
            )
        ).recordMutation(deviceUid, stateStore)
    }

    suspend fun calibrationFinish(
        deviceUid: DeviceUid,
        payload: DeviceDosingCalibrationFinishPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingCalibrationFinishResult> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi || !access.supportsCalibrationWorkflow) {
            return dosingUnsupported(
                deviceUid,
                DeviceDosingRuntimeContract.Action.CALIBRATION_FINISH
            )
        }
        val status = states.value[deviceUid]?.status
        return gateway.execute(
            deviceUid,
            dosingJsonCommand(
                action = DeviceDosingRuntimeContract.Action.CALIBRATION_FINISH,
                dataFactory = {
                    DeviceDosingCommandValidation.validateCalibrationFinishRequest(
                        payload.normalizedChannelKey,
                        status,
                        access
                    )
                    payload.toJson()
                },
                parser = { data ->
                    DeviceDosingMutationParser.parseCalibrationFinish(data).also { result ->
                        DeviceDosingCommandValidation.validateCalibrationFinishResult(
                            payload,
                            result,
                            status,
                            access
                        )
                    }
                }
            )
        ).recordMutation(deviceUid, stateStore)
    }

    suspend fun calibrationConfirm(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingCalibrationConfirmResult> =
        executeCalibrationChannelCommand(
            deviceUid,
            channelKey,
            DeviceDosingRuntimeContract.Action.CALIBRATION_CONFIRM,
            DeviceDosingMutationParser::parseCalibrationConfirm
        )

    suspend fun calibrationCancel(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingCalibrationCancelResult> =
        executeCalibrationChannelCommand(
            deviceUid,
            channelKey,
            DeviceDosingRuntimeContract.Action.CALIBRATION_CANCEL,
            DeviceDosingMutationParser::parseCalibrationCancel
        )

    suspend fun doseNow(
        deviceUid: DeviceUid,
        payload: DeviceDosingDoseNowPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingDoseNowResult> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi || !access.supportsManualDose) {
            return dosingUnsupported(deviceUid, DeviceDosingRuntimeContract.Action.DOSE_NOW)
        }
        val status = states.value[deviceUid]?.status
        return gateway.execute(
            deviceUid,
            dosingJsonCommand(
                action = DeviceDosingRuntimeContract.Action.DOSE_NOW,
                dataFactory = {
                    DeviceDosingCommandValidation.validateDoseRequest(payload, status, access)
                    payload.toJson()
                },
                parser = { data ->
                    DeviceDosingMutationParser.parseDoseNow(data).also { result ->
                        DeviceDosingCommandValidation.validateDoseResult(
                            payload,
                            result,
                            status,
                            access
                        )
                    }
                }
            )
        ).recordMutation(deviceUid, stateStore)
    }

    suspend fun doseStop(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingPumpCommandResult> = executePumpCommand(
        deviceUid = deviceUid,
        channelKey = channelKey,
        action = DeviceDosingRuntimeContract.Action.DOSE_STOP,
        supported = { access -> access.supportsManualDose },
        parser = DeviceDosingMutationParser::parseDoseStop
    )

    suspend fun reservoirRefill(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingReservoirRefillResult> {
        val normalizedKey = DeviceDosingChannelKeyPayload(channelKey).normalizedChannelKey
        val access = accessProvider(deviceUid)
        if (!access.supportsApi || !access.supportsReservoirRefill) {
            return dosingUnsupported(
                deviceUid,
                DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL
            )
        }
        val status = states.value[deviceUid]?.status
        return gateway.execute(
            deviceUid,
            dosingJsonCommand(
                action = DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL,
                dataFactory = {
                    DeviceDosingCommandValidation.validateReservoirRequest(
                        normalizedKey,
                        status,
                        access
                    )
                    DeviceDosingChannelKeyPayload(normalizedKey).toJson()
                },
                parser = { data ->
                    DeviceDosingMutationParser.parseReservoirRefill(data).also { result ->
                        DeviceDosingCommandValidation.validateReservoirResult(
                            normalizedKey,
                            result,
                            status,
                            access
                        )
                    }
                }
            )
        ).recordMutation(deviceUid, stateStore)
    }

    suspend fun setChannelDisplayName(
        deviceUid: DeviceUid,
        channelKey: String,
        displayName: String,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceDosingConfigApplyResult> = applyConfig(
        deviceUid,
        DeviceDosingConfigApplyPayload(
            channels = listOf(DeviceDosingChannelConfig(channelKey, displayName = displayName)),
            save = save
        )
    )

    suspend fun setChannelRegime(
        deviceUid: DeviceUid,
        channelKey: String,
        regime: DeviceDosingRegime,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceDosingConfigApplyResult> = applyConfig(
        deviceUid,
        DeviceDosingConfigApplyPayload(
            channels = listOf(DeviceDosingChannelConfig(channelKey, regime = regime)),
            save = save
        )
    )

    suspend fun configurePump(
        deviceUid: DeviceUid,
        channelKey: String,
        dosing: DeviceDosingChannelDosingConfig,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceDosingConfigApplyResult> = applyConfig(
        deviceUid,
        DeviceDosingConfigApplyPayload(
            channels = listOf(DeviceDosingChannelConfig(channelKey, dosing = dosing)),
            save = save
        )
    )

    suspend fun createSchedule(
        deviceUid: DeviceUid,
        schedule: DeviceDosingScheduleConfig,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceDosingConfigApplyResult> = mutateSchedules(
        deviceUid,
        save
    ) { current ->
        require(current.size < DeviceDosingRuntimeContract.Limit.MAX_SCHEDULES) {
            "Dosing schedule capacity is full."
        }
        current + schedule
    }

    suspend fun updateSchedule(
        deviceUid: DeviceUid,
        scheduleIndex: Int,
        schedule: DeviceDosingScheduleConfig,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceDosingConfigApplyResult> = mutateSchedules(
        deviceUid,
        save
    ) { current ->
        require(scheduleIndex in current.indices) {
            "Unknown Dosing schedule index: $scheduleIndex"
        }
        current.toMutableList().apply { this[scheduleIndex] = schedule }.toList()
    }

    suspend fun deleteSchedule(
        deviceUid: DeviceUid,
        scheduleIndex: Int,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceDosingConfigApplyResult> = mutateSchedules(
        deviceUid,
        save
    ) { current ->
        require(scheduleIndex in current.indices) {
            "Unknown Dosing schedule index: $scheduleIndex"
        }
        current.filterIndexed { index, _ -> index != scheduleIndex }
    }

    private suspend fun executePumpCommand(
        deviceUid: DeviceUid,
        channelKey: String,
        action: String,
        supported: (DeviceDosingRuntimeAccess) -> Boolean,
        parser: (JSONObject) -> DeviceDosingPumpCommandResult
    ): DeviceRuntimeCommandOutcome<DeviceDosingPumpCommandResult> {
        val normalizedKey = DeviceDosingChannelKeyPayload(channelKey).normalizedChannelKey
        val access = accessProvider(deviceUid)
        if (!access.supportsApi || !supported(access)) {
            return dosingUnsupported(deviceUid, action)
        }
        val status = states.value[deviceUid]?.status
        return gateway.execute(
            deviceUid,
            dosingJsonCommand(
                action = action,
                dataFactory = {
                    DeviceDosingCommandValidation.validatePrimeRequest(
                        normalizedKey,
                        status,
                        access
                    )
                    DeviceDosingChannelKeyPayload(normalizedKey).toJson()
                },
                parser = { data ->
                    parser(data).also { result ->
                        DeviceDosingCommandValidation.validatePumpResult(
                            normalizedKey,
                            result,
                            status,
                            access
                        )
                    }
                }
            )
        ).recordMutation(deviceUid, stateStore)
    }

    private suspend fun <T : DeviceDosingMutationResult> executeCalibrationChannelCommand(
        deviceUid: DeviceUid,
        channelKey: String,
        action: String,
        parser: (JSONObject) -> T
    ): DeviceRuntimeCommandOutcome<T> {
        val normalizedKey = DeviceDosingChannelKeyPayload(channelKey).normalizedChannelKey
        val access = accessProvider(deviceUid)
        if (!access.supportsApi || !access.supportsCalibrationWorkflow) {
            return dosingUnsupported(deviceUid, action)
        }
        val status = states.value[deviceUid]?.status
        return gateway.execute(
            deviceUid,
            dosingJsonCommand(
                action = action,
                dataFactory = {
                    if (action == DeviceDosingRuntimeContract.Action.CALIBRATION_CONFIRM) {
                        DeviceDosingCommandValidation.validateCalibrationConfirmRequest(
                            normalizedKey,
                            status,
                            access
                        )
                    } else {
                        DeviceDosingCommandValidation.validateCalibrationRequest(
                            normalizedKey,
                            status,
                            access
                        )
                    }
                    DeviceDosingChannelKeyPayload(normalizedKey).toJson()
                },
                parser = { data ->
                    parser(data).also { result ->
                        val snapshot = when (result) {
                            is DeviceDosingCalibrationConfirmResult -> result.channel
                            is DeviceDosingCalibrationCancelResult -> result.channel
                            else -> error("Unexpected Dosing calibration result type.")
                        }
                        val resultKey = when (result) {
                            is DeviceDosingCalibrationConfirmResult -> result.channelKey
                            is DeviceDosingCalibrationCancelResult -> result.channelKey
                            else -> error("Unexpected Dosing calibration result type.")
                        }
                        DeviceDosingCommandValidation.validateCalibrationChannelResult(
                            normalizedKey,
                            resultKey,
                            snapshot,
                            status,
                            access
                        )
                    }
                }
            )
        ).recordMutation(deviceUid, stateStore)
    }

    private suspend fun mutateSchedules(
        deviceUid: DeviceUid,
        save: Boolean,
        transform: (List<DeviceDosingScheduleConfig>) -> List<DeviceDosingScheduleConfig>
    ): DeviceRuntimeCommandOutcome<DeviceDosingConfigApplyResult> {
        val baseline = when (val result = ensureConfigBaseline(deviceUid)) {
            is DosingConfigBaseline.Ready -> result.config
            is DosingConfigBaseline.Failed -> return result.outcome.asDosingFailure()
        }
        val current = baseline.schedules.map(DeviceDosingScheduleConfigSnapshot::toPayload)
        return applyConfig(
            deviceUid,
            DeviceDosingConfigApplyPayload(
                schedules = transform(current),
                save = save
            )
        )
    }

    private suspend fun ensureConfigBaseline(deviceUid: DeviceUid): DosingConfigBaseline =
        states.value[deviceUid]?.config?.let(DosingConfigBaseline::Ready) ?: when (
            val status = requestStatus(deviceUid)
        ) {
            is DeviceRuntimeCommandOutcome.Success -> DosingConfigBaseline.Ready(
                requireNotNull(states.value[deviceUid]?.config) {
                    "Successful Dosing status did not publish a config baseline."
                }
            )
            else -> DosingConfigBaseline.Failed(status)
        }
}

private sealed interface DosingConfigBaseline {
    data class Ready(val config: DeviceDosingConfigSnapshot) : DosingConfigBaseline
    data class Failed(val outcome: DeviceRuntimeCommandOutcome<*>) : DosingConfigBaseline
}

private fun configUnsupported(
    payload: DeviceDosingConfigApplyPayload,
    access: DeviceDosingRuntimeAccess
): Boolean {
    val requestsSchedules = payload.schedules != null
    val requestsDisplayName = payload.channels?.any { channel ->
        channel.displayName != null
    } == true
    val requestsCalibration = payload.channels?.any { channel ->
        channel.dosing?.let { dosing ->
            dosing.doseMsPerMl != null || dosing.lastCalibratedAt != null
        } == true
    } == true
    val requestsReservoir = payload.channels?.any { channel ->
        channel.dosing?.let { dosing ->
            dosing.reservoirTrackingEnabled != null || dosing.reservoirCapacityMl != null
        } == true
    } == true
    return !access.supportsApi ||
        requestsSchedules && !access.supportsSchedules ||
        requestsDisplayName && !access.supportsChannelDisplayName ||
        requestsCalibration && !access.supportsCalibrationWorkflow ||
        requestsReservoir && !access.supportsReservoirRefill
}

private fun <T> dosingJsonCommand(
    action: String,
    dataFactory: () -> JSONObject = ::JSONObject,
    parser: (JSONObject) -> T
): DeviceRuntimeJsonCommand<T> = DeviceRuntimeJsonCommand(
    module = DeviceDosingRuntimeContract.MODULE,
    action = action,
    dataFactory = dataFactory,
    successParser = parser
)

private fun dosingUnsupported(
    deviceUid: DeviceUid,
    action: String
): DeviceRuntimeCommandOutcome.UnsupportedByDevice =
    DeviceRuntimeCommandOutcome.UnsupportedByDevice(
        deviceUid = deviceUid,
        module = DeviceDosingRuntimeContract.MODULE,
        action = action
    )

private fun DeviceRuntimeCommandOutcome<DeviceDosingStatus>.recordStatus(
    deviceUid: DeviceUid,
    stateStore: DeviceDosingRuntimeStateStore
): DeviceRuntimeCommandOutcome<DeviceDosingStatus> = also { outcome ->
    if (outcome is DeviceRuntimeCommandOutcome.Success) {
        stateStore.recordStatus(deviceUid, outcome.value)
    }
}

private fun <T : DeviceDosingMutationResult> DeviceRuntimeCommandOutcome<T>.recordMutation(
    deviceUid: DeviceUid,
    stateStore: DeviceDosingRuntimeStateStore
): DeviceRuntimeCommandOutcome<T> = also { outcome ->
    if (outcome is DeviceRuntimeCommandOutcome.Success) {
        stateStore.recordMutation(deviceUid, outcome.value)
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> DeviceRuntimeCommandOutcome<*>.asDosingFailure(): DeviceRuntimeCommandOutcome<T> {
    check(this !is DeviceRuntimeCommandOutcome.Success<*>)
    return this as DeviceRuntimeCommandOutcome<T>
}
