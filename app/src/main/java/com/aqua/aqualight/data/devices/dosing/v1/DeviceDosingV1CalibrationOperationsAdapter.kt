package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.isCommittedCalibrationTransitionFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Application calibration boundary backed exclusively by the central v1 state adapter. */
@Suppress("TooManyFunctions") // This class implements the complete application boundary verbatim.
internal class DeviceDosingV1CalibrationOperationsAdapter(
    private val adapter: DeviceDosingV1StateAdapter
) : DeviceDosingCalibrationOperations {

    override fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingCalibrationSnapshot?> =
        adapter.stateAccess.observeCalibration(deviceUid, slotId)

    override suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult =
        adapter.refreshCoordinator.refresh(deviceUid, slotId).toCalibrationResult()

    override suspend fun primeStart(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = adapter.mutationCoordinator.mutateRuntime(
        deviceUid = deviceUid,
        slotId = slotId,
        execute = { uid, channelKey, _, baseline ->
            requireCalibrationMutation(baseline.controls.calibrationEditable)
            adapter.repository.startPrime(uid, channelKey)
        },
        channel = DeviceDosingV1PrimeStartResult::channel
    ).toCalibrationResult { result -> result.durationMillis }

    override suspend fun primeStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = adapter.mutationCoordinator.mutateRuntime(
        deviceUid = deviceUid,
        slotId = slotId,
        execute = { uid, channelKey, _, _ -> adapter.repository.stopPrime(uid, channelKey) },
        channel = DeviceDosingV1SimpleStopResult::channel
    ).toCalibrationResult()

    override suspend fun start(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = adapter.mutationCoordinator.mutateRuntime(
        deviceUid = deviceUid,
        slotId = slotId,
        execute = { uid, channelKey, _, baseline ->
            requireCalibrationMutation(baseline.controls.calibrationEditable)
            adapter.repository.startCalibration(
                uid,
                DeviceDosingV1CalibrationStartRequest(
                    channelKey = channelKey,
                    durationMillis = constraints.calibrationRunDurationMs
                )
            )
        },
        channel = DeviceDosingV1CalibrationStartResult::channel
    ).toCalibrationResult { result -> result.durationMillis }

    override suspend fun finish(
        deviceUid: String,
        slotId: String,
        measuredMl: Double
    ): DeviceDosingCalibrationResult = adapter.mutationCoordinator.mutateRuntime(
        deviceUid = deviceUid,
        slotId = slotId,
        execute = { uid, channelKey, _, _ ->
            adapter.repository.finishCalibration(
                uid,
                DeviceDosingV1CalibrationFinishRequest(channelKey, measuredMl)
            )
        },
        channel = DeviceDosingV1CalibrationFinishResult::channel
    ).toCalibrationResult { result -> result.durationMillis }

    override suspend fun startVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = adapter.mutationCoordinator.mutateRuntime(
        deviceUid = deviceUid,
        slotId = slotId,
        execute = { uid, channelKey, _, _ ->
            adapter.repository.doseNow(
                uid,
                DeviceDosingV1DoseNowRequest(
                    channelKey = channelKey,
                    amount = DeviceDosingV1Amount.fromMilliliters(
                        DeviceDosingV1Contract.Limit.VERIFICATION_DOSE_ML
                    ),
                    usePendingCalibration = true
                )
            )
        },
        channel = DeviceDosingV1DoseNowResult::channel
    ).toCalibrationResult { result -> result.durationMillis }

    override suspend fun stopVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = adapter.mutationCoordinator.mutateRuntime(
        deviceUid = deviceUid,
        slotId = slotId,
        execute = { uid, channelKey, _, _ -> adapter.repository.stopDose(uid, channelKey) },
        channel = DeviceDosingV1SimpleStopResult::channel
    ).toCalibrationResult()

    override suspend fun confirm(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult {
        val previousSnapshot = adapter.stateAccess.observeCalibration(deviceUid, slotId).first()
        val result = adapter.mutationCoordinator.mutatePersisted(
            deviceUid = deviceUid,
            slotId = slotId,
            mutation = DeviceDosingV1PersistedMutation(
                execute = { uid, channelKey, _, baseline ->
                    // Calibration confirmation is a durable firmware transaction: it commits the
                    // coefficient, timestamp, display name and channel revision atomically.
                    requireCalibrationMutation(baseline.controls.calibrationEditable)
                    adapter.repository.confirmCalibration(
                        uid,
                        DeviceDosingV1CalibrationConfirmRequest(
                            channelKey = channelKey,
                            displayName = displayName
                        )
                    )
                },
                channel = DeviceDosingV1CalibrationConfirmResult::channel
            )
        )
        return result.toCalibrationConfirmationResult(
            stateAccess = adapter.stateAccess,
            deviceUid = deviceUid,
            slotId = slotId,
            previousSnapshot = previousSnapshot,
            expectedDisplayName = displayName
        )
    }

    override suspend fun cancel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = adapter.mutationCoordinator.mutateRuntime(
        deviceUid = deviceUid,
        slotId = slotId,
        execute = { uid, channelKey, _, _ ->
            adapter.repository.cancelCalibration(uid, channelKey)
        },
        channel = DeviceDosingV1CalibrationCancelResult::channel
    ).toCalibrationResult()
}

private fun DeviceDosingV1RefreshResult.toCalibrationResult(): DeviceDosingCalibrationResult =
    when (this) {
        is DeviceDosingV1RefreshResult.Success -> DeviceDosingCalibrationResult.Success(
            state.calibration
        )
        is DeviceDosingV1RefreshResult.Failed -> DeviceDosingCalibrationResult.Rejected(
            DeviceDosingCalibrationFailureMapper.map(outcome)
        )
        DeviceDosingV1RefreshResult.Malformed,
        DeviceDosingV1RefreshResult.RejectedStale -> DeviceDosingCalibrationResult.Rejected(
            DeviceDosingCalibrationFailure.INTERNAL
        )
    }

private fun <T> DeviceDosingV1MutationResult<T>.toCalibrationResult(
    operationDuration: (T) -> Long? = { null }
): DeviceDosingCalibrationResult = when (this) {
    is DeviceDosingV1MutationResult.Success -> DeviceDosingCalibrationResult.Success(
        snapshot = state.calibration,
        operationDurationMs = operationDuration(value)
    )
    // Replay-safe assignment reconciliation is not enabled for calibration runtime workflows.
    is DeviceDosingV1MutationResult.Reconciled -> DeviceDosingCalibrationResult.Rejected(
        DeviceDosingCalibrationFailure.INTERNAL
    )
    // Runtime calibration commands never have a durable-ACK-only completion state.
    is DeviceDosingV1MutationResult.Committed -> DeviceDosingCalibrationResult.Rejected(
        DeviceDosingCalibrationFailure.INTERNAL
    )
    is DeviceDosingV1MutationResult.Failed -> DeviceDosingCalibrationResult.Rejected(
        DeviceDosingCalibrationFailureMapper.map(outcome)
    )
    DeviceDosingV1MutationResult.Conflict -> DeviceDosingCalibrationResult.Rejected(
        DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH
    )
    is DeviceDosingV1MutationResult.LocallyRejected -> DeviceDosingCalibrationResult.Rejected(
        reason.toCalibrationFailure()
    )
    DeviceDosingV1MutationResult.Malformed,
    DeviceDosingV1MutationResult.RejectedStale -> DeviceDosingCalibrationResult.Rejected(
        DeviceDosingCalibrationFailure.INTERNAL
    )
}

private suspend fun DeviceDosingV1MutationResult<DeviceDosingV1CalibrationConfirmResult>
    .toCalibrationConfirmationResult(
        stateAccess: DeviceDosingV1StateAccess,
        deviceUid: String,
        slotId: String,
        previousSnapshot: DeviceDosingCalibrationSnapshot?,
        expectedDisplayName: String
    ): DeviceDosingCalibrationResult = when (this) {
    is DeviceDosingV1MutationResult.Success -> DeviceDosingCalibrationResult.Success(
        state.calibration
    )
    is DeviceDosingV1MutationResult.Committed -> {
        // Durable ACK is sufficient only when the single central state owner can prove the exact
        // pending-verification -> confirmed/idle transition from its committed presentation.
        val committedSnapshot = stateAccess.observeCalibration(deviceUid, slotId).first()
        if (
            committedSnapshot?.isCommittedCalibrationTransitionFrom(
                previous = previousSnapshot,
                expectedDisplayName = expectedDisplayName
            ) == true
        ) {
            DeviceDosingCalibrationResult.Success(committedSnapshot)
        } else {
            DeviceDosingCalibrationResult.Rejected(DeviceDosingCalibrationFailure.INTERNAL)
        }
    }
    is DeviceDosingV1MutationResult.Failed -> DeviceDosingCalibrationResult.Rejected(
        DeviceDosingCalibrationFailureMapper.map(outcome)
    )
    DeviceDosingV1MutationResult.Conflict -> DeviceDosingCalibrationResult.Rejected(
        DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH
    )
    is DeviceDosingV1MutationResult.LocallyRejected -> DeviceDosingCalibrationResult.Rejected(
        reason.toCalibrationFailure()
    )
    is DeviceDosingV1MutationResult.Reconciled,
    DeviceDosingV1MutationResult.Malformed,
    DeviceDosingV1MutationResult.RejectedStale -> DeviceDosingCalibrationResult.Rejected(
        DeviceDosingCalibrationFailure.INTERNAL
    )
}

private fun DeviceDosingChannelRejection.toCalibrationFailure(): DeviceDosingCalibrationFailure =
    when (this) {
        DeviceDosingChannelRejection.BUSY -> DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS
        DeviceDosingChannelRejection.CONFLICT ->
            DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH
        DeviceDosingChannelRejection.OUTPUT_STOP_UNCONFIRMED ->
            DeviceDosingCalibrationFailure.OUTPUT_STOP_UNCONFIRMED
        else -> DeviceDosingCalibrationFailure.INTERNAL
    }

private fun requireCalibrationMutation(condition: Boolean) {
    if (!condition) {
        throw LocalDosingMutationRejection(DeviceDosingChannelRejection.NOT_EDITABLE)
    }
}
