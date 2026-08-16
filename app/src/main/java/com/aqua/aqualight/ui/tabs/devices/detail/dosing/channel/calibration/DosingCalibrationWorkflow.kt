package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.hasActiveCalibrationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class DosingCalibrationWorkflow(
    private val operations: DeviceDosingCalibrationOperations,
    clock: DeviceDosingCalibrationClock,
    private val scope: CoroutineScope
) {
    private val session = DosingCalibrationWorkflowSession()
    private val mutableUiState = MutableStateFlow(DeviceDosingCalibrationUiState())
    private val eventChannel = Channel<DeviceDosingCalibrationEvent>(Channel.BUFFERED)
    private val countdown = DosingCalibrationCountdownController(
        clock = clock,
        scope = scope,
        readState = { mutableUiState.value },
        writeState = { state -> mutableUiState.value = state }
    )

    val uiState: StateFlow<DeviceDosingCalibrationUiState> = mutableUiState.asStateFlow()
    val events: Flow<DeviceDosingCalibrationEvent> = eventChannel.receiveAsFlow()

    fun bind(route: DeviceDosingCalibrationRoute) {
        val normalized = route.normalized()
        if (!normalized.hasIdentity()) {
            mutableUiState.value = mutableUiState.value
                .updateProgress { progress -> progress.copy(isLoading = false) }
                .copy(error = DeviceDosingCalibrationError.OPERATION_FAILED)
            return
        }
        if (session.route?.matchesBinding(normalized) == true) return

        session.observeJob?.cancel()
        session.reset(normalized)
        mutableUiState.value = DeviceDosingCalibrationUiState(
            channel = DosingCalibrationChannelState(
                pumpCount = normalized.pumpCount,
                channelNumber = normalized.channelNumber
            )
        )
        session.observeJob = scope.launch {
            operations.observe(normalized.deviceUid, normalized.slotId).collect { snapshot ->
                val currentRoute = session.route
                when {
                    currentRoute == null -> Unit
                    snapshot == null -> markCalibrationSnapshotUnavailable(
                        session = session,
                        countdown = countdown,
                        mutableUiState = mutableUiState
                    )
                    snapshot.matches(currentRoute) -> applySnapshot(snapshot)
                }
            }
        }
        execute(DosingCalibrationOperation.Refresh)
    }

    fun onAction(action: DeviceDosingCalibrationAction) {
        val route = session.route ?: return
        val decision = reduceDosingCalibrationAction(
            state = mutableUiState.value,
            primeRequested = session.primeRequested,
            action = action,
            constraints = operations.constraints
        )
        mutableUiState.value = decision.state
        applyPrimeDirective(decision.primeDirective)
        if (decision.operation == DosingCalibrationOperation.ContinueFromPrime) {
            session.primeRequested = false
            session.primeSafetyJob?.cancel()
        }
        decision.operation?.let { operation -> execute(operation, route) }
    }

    fun requestExit() {
        if (session.exiting) return
        session.exiting = true
        countdown.cancel()
        session.primeSafetyJob?.cancel()
        val primeMayBeActive = session.primeRequested
        session.primeRequested = false
        val lastKnownSnapshot = session.latestSnapshot
        scope.launch {
            session.route?.let { route ->
                operations.exitSafely(
                    deviceUid = route.deviceUid,
                    slotId = route.slotId,
                    primeMayBeActive = primeMayBeActive,
                    lastKnownSnapshot = lastKnownSnapshot
                )
            }
            eventChannel.send(DeviceDosingCalibrationEvent.Exit)
        }
    }

    fun onHostStopped() {
        if (!session.primeRequested ||
            mutableUiState.value.step != DeviceDosingCalibrationStep.PRIME
        ) {
            return
        }
        val route = session.route ?: return
        session.primeSafetyJob?.cancel()
        session.primeRequested = false
        mutableUiState.value = mutableUiState.value.updateProgress { progress ->
            progress.copy(isPumpActive = false)
        }
        scope.launch {
            val result = operations.primeSafetyStop(route.deviceUid, route.slotId)
            handleResult(DosingCalibrationOperation.PrimeStop, result, renderSuccess = false)
        }
    }

    private fun applyPrimeDirective(directive: DosingCalibrationPrimeDirective?) {
        when (directive) {
            DosingCalibrationPrimeDirective.START -> startPrimeSafetyWindow()
            DosingCalibrationPrimeDirective.STOP -> {
                session.primeSafetyJob?.cancel()
                session.primeRequested = false
            }
            null -> Unit
        }
    }

    private fun startPrimeSafetyWindow() {
        val route = session.route ?: return
        session.primeRequested = true
        session.primeSafetyJob?.cancel()
        session.primeSafetyJob = scope.launch {
            val result = operations.awaitPrimeSafetyStop(route.deviceUid, route.slotId)
            if (!session.primeRequested) return@launch
            session.primeRequested = false
            mutableUiState.value = mutableUiState.value.updateProgress { progress ->
                progress.copy(isPumpActive = false)
            }
            handleResult(DosingCalibrationOperation.PrimeStop, result, renderSuccess = false)
        }
    }

    private fun execute(
        operation: DosingCalibrationOperation,
        route: DeviceDosingCalibrationRoute? = session.route,
        renderSuccess: Boolean = true
    ) {
        val boundRoute = route ?: return
        when (operation) {
            DosingCalibrationOperation.PrimeStart -> scope.launch {
                val result = performDosingCalibrationOperation(operations, boundRoute, operation)
                if (result !is DeviceDosingCalibrationResult.Success) {
                    session.primeSafetyJob?.cancel()
                    session.primeRequested = false
                    mutableUiState.value = mutableUiState.value.updateProgress { progress ->
                        progress.copy(isPumpActive = false)
                    }
                    handleResult(operation, result, renderSuccess)
                } else if (!session.primeRequested) {
                    operations.primeSafetyStop(boundRoute.deviceUid, boundRoute.slotId)
                }
            }
            DosingCalibrationOperation.PrimeStop -> scope.launch {
                val result = performDosingCalibrationOperation(operations, boundRoute, operation)
                handleResult(operation, result, renderSuccess = false)
            }
            else -> {
                session.actionJob?.cancel()
                session.actionJob = scope.launch {
                    val result = performDosingCalibrationOperation(operations, boundRoute, operation)
                    handleResult(operation, result, renderSuccess)
                }
            }
        }
    }

    private suspend fun handleResult(
        operation: DosingCalibrationOperation,
        result: DeviceDosingCalibrationResult,
        renderSuccess: Boolean
    ) {
        when (result) {
            is DeviceDosingCalibrationResult.Success -> {
                if (renderSuccess) applySuccess(operation, result.snapshot)
            }
            is DeviceDosingCalibrationResult.Rejected -> {
                mutableUiState.value = mutableUiState.value.withCalibrationFailure(
                    result.failure.toCalibrationError()
                )
            }
        }
    }

    private suspend fun applySuccess(
        operation: DosingCalibrationOperation,
        snapshot: DeviceDosingCalibrationSnapshot
    ) {
        session.latestSnapshot = snapshot
        val transition = dosingCalibrationSuccessTransition(
            operation = operation,
            current = mutableUiState.value,
            snapshot = snapshot
        )
        if (transition.markLocalProgress) session.hasLocalProgress = true
        transition.state?.let { state -> mutableUiState.value = state }
        if (transition.applySnapshot) applySnapshot(snapshot)
        if (transition.emitCompleted) {
            session.completionEmitted = true
            eventChannel.send(DeviceDosingCalibrationEvent.Completed(snapshot.toDetailTarget()))
        }
    }

    private fun applySnapshot(snapshot: DeviceDosingCalibrationSnapshot) {
        val route = session.route ?: return
        if (
            session.authoritativeSessionInterrupted &&
            !snapshot.hasActiveCalibrationSession
        ) {
            session.hasLocalProgress = false
        }
        session.authoritativeSessionInterrupted = false
        val interruptedPrime = session.primeRequested && mutableUiState.value.isLoading
        if (interruptedPrime) {
            session.primeRequested = false
            scope.launch {
                val result = operations.primeSafetyStop(route.deviceUid, route.slotId)
                handleResult(DosingCalibrationOperation.PrimeStop, result, renderSuccess = false)
            }
        }
        session.latestSnapshot = snapshot
        val presentation = reduceDosingCalibrationSnapshot(
            snapshot = snapshot,
            current = mutableUiState.value,
            hasLocalProgress = session.hasLocalProgress
        )
        mutableUiState.value = presentation.state
        if (snapshot.shouldAutoComplete(
                isRecalibration = route.recalibration,
                hasLocalProgress = session.hasLocalProgress,
                completionEmitted = session.completionEmitted
            )
        ) {
            session.completionEmitted = true
            eventChannel.trySend(DeviceDosingCalibrationEvent.Completed(snapshot.toDetailTarget()))
            return
        }
        countdown.schedule(
            countdown = presentation.countdown,
            onVerificationComplete = { execute(DosingCalibrationOperation.Refresh) }
        )
    }
}

private fun markCalibrationSnapshotUnavailable(
    session: DosingCalibrationWorkflowSession,
    countdown: DosingCalibrationCountdownController,
    mutableUiState: MutableStateFlow<DeviceDosingCalibrationUiState>
) {
    countdown.cancel()
    session.primeSafetyJob?.cancel()
    session.primeSafetyJob = null
    session.authoritativeSessionInterrupted =
        session.latestSnapshot?.hasActiveCalibrationSession == true
    mutableUiState.value = mutableUiState.value
        .updateProgress { progress ->
            progress.copy(
                isLoading = true,
                isBusy = false,
                isPumpActive = false,
                remainingMs = 0L,
                candidateDoseMsPerMl = null
            )
        }
        .copy(error = null)
}

private fun DeviceDosingCalibrationUiState.withCalibrationFailure(
    error: DeviceDosingCalibrationError
): DeviceDosingCalibrationUiState = updateProgress { progress ->
    progress.copy(isLoading = false, isBusy = false, isPumpActive = false)
}.copy(error = error)

internal fun DeviceDosingCalibrationFailure.toCalibrationError(): DeviceDosingCalibrationError =
    when (this) {
        DeviceDosingCalibrationFailure.CONNECTION -> DeviceDosingCalibrationError.CONNECTION
        DeviceDosingCalibrationFailure.STORAGE -> DeviceDosingCalibrationError.STORAGE
        DeviceDosingCalibrationFailure.HARDWARE -> DeviceDosingCalibrationError.HARDWARE
        DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS ->
            DeviceDosingCalibrationError.OPERATION_IN_PROGRESS
        DeviceDosingCalibrationFailure.DEVICE_TIME_NOT_READY ->
            DeviceDosingCalibrationError.DEVICE_TIME_NOT_READY
        DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH ->
            DeviceDosingCalibrationError.CALIBRATION_STATE_MISMATCH
        DeviceDosingCalibrationFailure.INVALID_MEASUREMENT ->
            DeviceDosingCalibrationError.INVALID_MEASUREMENT
        DeviceDosingCalibrationFailure.INTERNAL ->
            DeviceDosingCalibrationError.OPERATION_FAILED
    }

private fun DeviceDosingCalibrationSnapshot.matches(route: DeviceDosingCalibrationRoute): Boolean =
    deviceUid == route.deviceUid && slotId == route.slotId

private fun DeviceDosingCalibrationRoute.matchesBinding(other: DeviceDosingCalibrationRoute): Boolean =
    deviceUid == other.deviceUid &&
        slotId == other.slotId &&
        recalibration == other.recalibration
