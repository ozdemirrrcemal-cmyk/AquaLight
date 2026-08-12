package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class DosingCalibrationWorkflow(
    private val operations: DeviceDosingCalibrationOperations,
    private val clock: DeviceDosingCalibrationClock,
    private val scope: CoroutineScope
) {
    private val session = DosingCalibrationWorkflowSession()
    private val mutableUiState = MutableStateFlow(DeviceDosingCalibrationUiState())
    private val eventChannel = Channel<DeviceDosingCalibrationEvent>(Channel.BUFFERED)

    val uiState: StateFlow<DeviceDosingCalibrationUiState> = mutableUiState.asStateFlow()
    val events: Flow<DeviceDosingCalibrationEvent> = eventChannel.receiveAsFlow()

    fun bind(route: DeviceDosingCalibrationRoute) {
        val normalized = route.normalized()
        if (!normalized.hasIdentity()) {
            mutableUiState.value = mutableUiState.value.copy(
                isLoading = false,
                error = DeviceDosingCalibrationError.UNAVAILABLE
            )
            return
        }
        if (session.route == normalized) return

        session.cancelTransientJobs()
        session.observeJob?.cancel()
        session.reset(normalized)
        mutableUiState.value = DeviceDosingCalibrationUiState(
            displayName = normalized.channelTitle,
            pumpCount = normalized.pumpCount,
            channelNumber = normalized.channelNumber,
            channelTitle = normalized.channelTitle
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
        session.countdownJob?.cancel()
        session.primeSafetyJob?.cancel()
        val route = session.route
        val snapshot = session.latestSnapshot
        val state = mutableUiState.value
        scope.launch {
            if (route != null) {
                cleanupActiveCalibration(route, state, snapshot)
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
            mutableUiState.value = mutableUiState.value.copy(isPumpActive = false)
            execute(DosingCalibrationOperation.PrimeStop, renderSuccess = false)
        }
    }

    private fun execute(
        operation: DosingCalibrationOperation,
        route: DeviceDosingCalibrationRoute? = session.route,
        renderSuccess: Boolean = true
    ) {
        val boundRoute = route ?: return
        if (operation == DosingCalibrationOperation.PrimeStart) {
            scope.launch {
                val result = performDosingCalibrationOperation(operations, boundRoute, operation)
                if (result is DeviceDosingCalibrationResult.Success && !session.primeRequested) {
                    operations.primeStop(boundRoute.deviceUid, boundRoute.slotId)
                } else {
                    handleResult(operation, result, renderSuccess)
                }
            }
            return
        }
        if (operation == DosingCalibrationOperation.PrimeStop) {
            scope.launch {
                val result = performDosingCalibrationOperation(operations, boundRoute, operation)
                handleResult(operation, result, renderSuccess)
            }
            return
        }

        session.actionJob?.cancel()
        session.actionJob = scope.launch {
            val result = performDosingCalibrationOperation(operations, boundRoute, operation)
            handleResult(operation, result, renderSuccess)
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
            DeviceDosingCalibrationResult.Unavailable -> renderFailure(
                DeviceDosingCalibrationError.UNAVAILABLE
            )
            DeviceDosingCalibrationResult.Failed -> renderFailure(
                DeviceDosingCalibrationError.CONNECTION
            )
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
            eventChannel.trySend(
                DeviceDosingCalibrationEvent.Completed(snapshot.toDetailTarget())
            )
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
            eventChannel.trySend(
                DeviceDosingCalibrationEvent.Completed(snapshot.toDetailTarget())
            )
            return
        }
        scheduleCountdown(presentation.countdown)
    }

    private fun scheduleCountdown(countdown: DosingCalibrationCountdown?) {
        session.countdownJob?.cancel()
        when (countdown) {
            is DosingCalibrationCountdown.CalibrationRun -> startCountdown(
                durationMs = countdown.durationMs,
                onComplete = {
                    mutableUiState.value = mutableUiState.value.copy(
                        isBusy = false,
                        isPumpActive = false,
                        remainingMs = 0L,
                        step = DeviceDosingCalibrationStep.MEASUREMENT
                    )
                }
            )
            is DosingCalibrationCountdown.Verification -> startCountdown(
                durationMs = countdown.durationMs,
                onComplete = { execute(DosingCalibrationOperation.Refresh) }
            )
            null -> Unit
        }
    }

    private fun startCountdown(durationMs: Long, onComplete: () -> Unit) {
        if (durationMs <= 0L) {
            onComplete()
            return
        }
        val startedAt = clock.elapsedRealtime()
        mutableUiState.value = mutableUiState.value.copy(
            isBusy = true,
            isPumpActive = true,
            remainingMs = durationMs
        )
        session.countdownJob = scope.launch {
            while (true) {
                val elapsed = clock.elapsedRealtime() - startedAt
                val remaining = (durationMs - elapsed).coerceAtLeast(0L)
                mutableUiState.value = mutableUiState.value.copy(remainingMs = remaining)
                if (remaining == 0L) break
                delay(COUNTDOWN_TICK_MS.coerceAtMost(remaining))
            }
            onComplete()
        }
    }

    private fun renderFailure(error: DeviceDosingCalibrationError) {
        mutableUiState.value = mutableUiState.value.copy(
            isLoading = false,
            isBusy = false,
            isPumpActive = false,
            error = error
        )
    }

    private suspend fun cleanupActiveCalibration(
        route: DeviceDosingCalibrationRoute,
        state: DeviceDosingCalibrationUiState,
        snapshot: DeviceDosingCalibrationSnapshot?
    ) {
        if (state.step == DeviceDosingCalibrationStep.PRIME &&
            (session.primeRequested || state.isPumpActive)
        ) {
            session.primeRequested = false
            operations.primeStop(route.deviceUid, route.slotId)
        }
        if (state.step == DeviceDosingCalibrationStep.VERIFICATION &&
            snapshot?.verificationDoseStarted == true &&
            !snapshot.verificationDoseComplete
        ) {
            operations.stopVerificationDose(route.deviceUid, route.slotId)
        }
        if (snapshot?.sessionPhase != null &&
            snapshot.sessionPhase != DeviceDosingCalibrationSessionPhase.IDLE
        ) {
            operations.cancel(route.deviceUid, route.slotId)
        }
    }

    private companion object {
        const val COUNTDOWN_TICK_MS = 100L
        const val PRIME_SAFETY_TIMEOUT_MS = 30_000L
    }
}

private fun DeviceDosingCalibrationSnapshot.matches(route: DeviceDosingCalibrationRoute): Boolean =
    deviceUid == route.deviceUid && slotId == route.slotId
