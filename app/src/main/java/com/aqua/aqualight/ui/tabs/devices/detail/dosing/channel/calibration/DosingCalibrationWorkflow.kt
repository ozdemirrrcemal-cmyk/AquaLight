package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
                .copy(error = DeviceDosingCalibrationError.UNAVAILABLE)
            return
        }
        if (session.route?.matchesBinding(normalized) == true) return

        session.observeJob?.cancel()
        session.reset(normalized)
        mutableUiState.value = DeviceDosingCalibrationUiState(
            channel = DosingCalibrationChannelState(
                pumpCount = normalized.pumpCount,
                channelNumber = normalized.channelNumber,
                channelTitle = normalized.channelTitle
            ),
            input = DosingCalibrationInputState(displayName = normalized.channelTitle)
        )
        session.observeJob = scope.launch {
            operations.observe(normalized.deviceUid, normalized.slotId).collect { snapshot ->
                if (snapshot != null && snapshot.matches(normalized)) applySnapshot(snapshot)
            }
        }
        execute(DosingCalibrationOperation.Refresh)
    }

    fun onAction(action: DeviceDosingCalibrationAction) {
        val route = session.route ?: return
        val decision = reduceDosingCalibrationAction(
            state = mutableUiState.value,
            primeRequested = session.primeRequested,
            action = action
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
        val route = session.route
        val step = mutableUiState.value.step
        val snapshot = session.latestSnapshot
        scope.launch {
            if (route != null) {
                if (step == DeviceDosingCalibrationStep.PRIME &&
                    (session.primeRequested || mutableUiState.value.isPumpActive)
                ) {
                    session.primeRequested = false
                    operations.primeStop(route.deviceUid, route.slotId)
                }
                cleanupDosingCalibrationSession(
                    operations = operations,
                    route = route,
                    step = step,
                    snapshot = snapshot
                )
            }
            eventChannel.send(DeviceDosingCalibrationEvent.Exit)
        }
    }

    fun onHostStopped() {
        if (session.primeRequested && mutableUiState.value.step == DeviceDosingCalibrationStep.PRIME) {
            onAction(DeviceDosingCalibrationAction.PrimeReleased)
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
        session.primeRequested = true
        session.primeSafetyJob?.cancel()
        session.primeSafetyJob = scope.launch {
            delay(PRIME_SAFETY_TIMEOUT_MS)
            if (!session.primeRequested) return@launch
            session.primeRequested = false
            mutableUiState.value = mutableUiState.value.updateProgress { progress ->
                progress.copy(isPumpActive = false)
            }
            execute(DosingCalibrationOperation.PrimeStop, renderSuccess = false)
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
                    session.primeRequested = false
                    mutableUiState.value = mutableUiState.value.updateProgress { progress ->
                        progress.copy(isPumpActive = false)
                    }
                    handleResult(operation, result, renderSuccess)
                } else if (!session.primeRequested) {
                    operations.primeStop(boundRoute.deviceUid, boundRoute.slotId)
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

    private fun handleResult(
        operation: DosingCalibrationOperation,
        result: DeviceDosingCalibrationResult,
        renderSuccess: Boolean
    ) {
        when (result) {
            is DeviceDosingCalibrationResult.Success -> {
                if (renderSuccess) applySuccess(operation, result.snapshot)
            }
            DeviceDosingCalibrationResult.Unavailable -> {
                mutableUiState.value = mutableUiState.value.withCalibrationFailure(
                    DeviceDosingCalibrationError.UNAVAILABLE
                )
            }
            DeviceDosingCalibrationResult.Failed -> {
                mutableUiState.value = mutableUiState.value.withCalibrationFailure(
                    DeviceDosingCalibrationError.CONNECTION
                )
            }
        }
    }

    private fun applySuccess(
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
            eventChannel.trySend(DeviceDosingCalibrationEvent.Completed(snapshot.toDetailTarget()))
        }
    }

    private fun applySnapshot(snapshot: DeviceDosingCalibrationSnapshot) {
        session.latestSnapshot = snapshot
        val route = session.route ?: return
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

    private companion object {
        const val PRIME_SAFETY_TIMEOUT_MS = 30_000L
    }
}

private fun DeviceDosingCalibrationUiState.withCalibrationFailure(
    error: DeviceDosingCalibrationError
): DeviceDosingCalibrationUiState = updateProgress { progress ->
    progress.copy(isLoading = false, isBusy = false, isPumpActive = false)
}.copy(error = error)

private fun DeviceDosingCalibrationSnapshot.matches(route: DeviceDosingCalibrationRoute): Boolean =
    deviceUid == route.deviceUid && slotId == route.slotId

private fun DeviceDosingCalibrationRoute.matchesBinding(other: DeviceDosingCalibrationRoute): Boolean =
    deviceUid == other.deviceUid &&
        slotId == other.slotId &&
        recalibration == other.recalibration
