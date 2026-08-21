package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import kotlinx.coroutines.flow.Flow

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
                    durationMillis = DeviceDosingV1Contract.Limit.DEFAULT_CALIBRATION_DURATION_MS
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
    ): DeviceDosingCalibrationResult = adapter.mutationCoordinator.mutateRuntime(
        deviceUid = deviceUid,
        slotId = slotId,
        execute = { uid, channelKey, _, baseline ->
            // Final identity persistence belongs to the calibration transaction itself. Standalone
            // display-name editing remains blocked while calibration is open, but that generic
            // config guard must never block the firmware-owned pending-verification confirmation.
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
    ).toCalibrationResult()

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
    // Replay-safe assignment reconciliation is not enabled for calibration workflows.
    is DeviceDosingV1MutationResult.Reconciled -> DeviceDosingCalibrationResult.Rejected(
        DeviceDosingCalibrationFailure.INTERNAL
    )
    // Calibration commands are runtime mutations and cannot legitimately produce Committed.
    // Keep this defensive path fail-closed if that contract ever changes unexpectedly.
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

private fun DeviceDosingChannelRejection.toCalibrationFailure(): DeviceDosingCalibrationFailure =
    when (this) {
        DeviceDosingChannelRejection.BUSY -> DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS
        DeviceDosingChannelRejection.CONFLICT ->
            DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH
        else -> DeviceDosingCalibrationFailure.INTERNAL
    }

private fun requireCalibrationMutation(condition: Boolean) {
    if (!condition) {
        throw LocalDosingMutationRejection(DeviceDosingChannelRejection.NOT_EDITABLE)
    }
}
