package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.hasActiveCalibrationSession
import com.aqua.aqualight.application.devices.dosing.isCommittedCalibrationTransitionFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DosingCalibrationWorkflow(
    private val operations: DeviceDosingCalibrationOperations,
    clock: DeviceDosingCalibrationClock,
    private val scope: CoroutineScope
) {
    private val session = DosingCalibrationWorkflowSession()
    private val operationMutex = Mutex()
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
        session.nameDraftInitialized = normalized.restoredDisplayNameDraft != null
        mutableUiState.value = DeviceDosingCalibrationUiState(
            channel = DosingCalibrationChannelState(
                pumpCount = normalized.pumpCount,
                channelNumber = normalized.channelNumber
            ),
            input = DosingCalibrationInputState(
                displayName = normalized.restoredDisplayNameDraft.orEmpty()
            )
        )
        session.observeJob = scope.launch {
            operations.observe(normalized.deviceUid, normalized.slotId).collect { snapshot ->
                val currentRoute = session.route
                when {
                    currentRoute == null -> Unit
                    snapshot == null && session.shouldSuppressTransientUnavailable() -> Unit
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
        if (session.exiting || session.completionEmitted) return
        val route = session.route ?: return
        val decision = reduceDosingCalibrationAction(
            state = mutableUiState.value,
            primeRequested = session.primeRequested,
            action = action,
            constraints = operations.constraints
        )
        mutableUiState.value = decision.state
        if (action is DeviceDosingCalibrationAction.DisplayNameChanged) {
            session.nameDraftInitialized = true
        }
        if (decision.markLocalProgress) session.hasLocalProgress = true
        session.applyPrimeDirective(decision.primeDirective, ::startPrimeSafetyWindow)
        if (decision.operation == DosingCalibrationOperation.ContinueFromPrime) {
            session.primeRequested = false
            session.primeSafetyJob?.cancel()
        }
        decision.operation?.let { operation -> execute(operation, route) }
    }

    fun requestExit() {
        if (session.exiting || session.completionEmitted) return
        val route = session.route ?: return
        session.exiting = true
        countdown.cancel()
        session.primeSafetyJob?.cancel()
        val primeMayBeActive = session.primeRequested
        session.primeRequested = false
        scope.launch {
            session.beginOperation()
            try {
                val result = operationMutex.withLock {
                    operations.exitSafely(
                        deviceUid = route.deviceUid,
                        slotId = route.slotId,
                        primeMayBeActive = primeMayBeActive,
                        lastKnownSnapshot = session.latestSnapshot
                    )
                }
                when (result) {
                    is DeviceDosingCalibrationResult.Success -> {
                        session.acceptedAuthoritativeSnapshot(result.snapshot)
                        session.completionEmitted = true
                        eventChannel.send(DeviceDosingCalibrationEvent.Exit)
                    }
                    is DeviceDosingCalibrationResult.Rejected -> {
                        session.operationRejected()
                        session.exiting = false
                        mutableUiState.value = mutableUiState.value.withCalibrationFailure(
                            error = result.failure.toCalibrationError(),
                            hasAuthoritativeSnapshot = session.latestSnapshot != null
                        )
                    }
                }
            } finally {
                session.endOperation()
            }
        }
    }

    fun onHostStopped() {
        val route = session.route?.takeIf {
            session.shouldStopPrimeOnHostStop(mutableUiState.value)
        } ?: return
        session.primeSafetyJob?.cancel()
        session.primeRequested = false
        mutableUiState.value = mutableUiState.value.updateProgress { progress ->
            progress.copy(isPumpActive = false)
        }
        scope.launch {
            session.beginOperation()
            try {
                operationMutex.withLock {
                    val result = operations.primeSafetyStop(route.deviceUid, route.slotId)
                    handleResult(
                        DosingCalibrationOperation.PrimeStop,
                        result,
                        renderSuccess = false
                    )
                }
            } finally {
                session.endOperation()
            }
        }
    }

    private fun startPrimeSafetyWindow() {
        val route = session.route ?: return
        session.primeRequested = true
        session.primeSafetyJob?.cancel()
        session.primeSafetyJob = scope.launch {
            operations.awaitPrimeSafetyStop()
            if (!session.primeRequested || session.exiting || session.completionEmitted) return@launch
            session.beginOperation()
            try {
                operationMutex.withLock {
                    session.primeRequested = false
                    mutableUiState.value = mutableUiState.value.updateProgress { progress ->
                        progress.copy(isPumpActive = false)
                    }
                    val result = operations.primeSafetyStop(route.deviceUid, route.slotId)
                    handleResult(
                        DosingCalibrationOperation.PrimeStop,
                        result,
                        renderSuccess = false
                    )
                }
            } finally {
                session.endOperation()
            }
        }
    }

    private fun execute(
        operation: DosingCalibrationOperation,
        route: DeviceDosingCalibrationRoute? = session.route,
        renderSuccess: Boolean = true,
        verificationDeadline: Boolean = false
    ) {
        if (session.exiting || session.completionEmitted) return
        val boundRoute = route ?: return
        val task: suspend () -> Unit = {
            operationMutex.withLock {
                val result = if (verificationDeadline) {
                    performVerificationDeadline(operations, boundRoute)
                } else {
                    performDosingCalibrationOperation(operations, boundRoute, operation)
                }
                if (operation == DosingCalibrationOperation.PrimeStart &&
                    result !is DeviceDosingCalibrationResult.Success
                ) {
                    session.primeSafetyJob?.cancel()
                    session.primeRequested = false
                    mutableUiState.value = mutableUiState.value.updateProgress { progress ->
                        progress.copy(isPumpActive = false)
                    }
                }
                handleResult(operation, result, renderSuccess)
            }
        }
        if (operation == DosingCalibrationOperation.PrimeStart ||
            operation == DosingCalibrationOperation.PrimeStop
        ) {
            scope.launchTrackedCalibrationOperation(session, task)
        } else {
            session.actionJob?.cancel()
            session.actionJob = scope.launchTrackedCalibrationOperation(session, task)
        }
    }

    private suspend fun handleResult(
        operation: DosingCalibrationOperation,
        result: DeviceDosingCalibrationResult,
        renderSuccess: Boolean
    ) {
        when (result) {
            is DeviceDosingCalibrationResult.Success -> {
                val previousSnapshot = session.latestSnapshot
                session.acceptedAuthoritativeSnapshot(result.snapshot)
                if (renderSuccess) {
                    applySuccess(
                        operation = operation,
                        snapshot = result.snapshot,
                        previousSnapshot = previousSnapshot
                    )
                }
            }
            is DeviceDosingCalibrationResult.Rejected -> {
                session.operationRejected()
                mutableUiState.value = mutableUiState.value.withCalibrationFailure(
                    error = result.failure.toCalibrationError(),
                    hasAuthoritativeSnapshot = session.latestSnapshot != null
                )
            }
        }
    }

    private suspend fun applySuccess(
        operation: DosingCalibrationOperation,
        snapshot: DeviceDosingCalibrationSnapshot,
        previousSnapshot: DeviceDosingCalibrationSnapshot?
    ) {
        val transition = dosingCalibrationSuccessTransition(
            operation = operation,
            current = mutableUiState.value
        )
        if (transition.markLocalProgress) session.hasLocalProgress = true
        transition.state?.let { state -> mutableUiState.value = state }
        if (transition.applySnapshot) applySnapshot(snapshot, previousSnapshot)
        if (transition.emitCompleted && !session.exiting && !session.completionEmitted) {
            session.completionEmitted = true
            eventChannel.send(DeviceDosingCalibrationEvent.Completed(snapshot.toDetailTarget()))
        }
    }

    private fun applySnapshot(
        snapshot: DeviceDosingCalibrationSnapshot,
        previousSnapshot: DeviceDosingCalibrationSnapshot? = session.latestSnapshot
    ) {
        val route = session.route ?: return
        DosingCalibrationSnapshotReconciliation.initializeNameDraftIfNeeded(
            session = session,
            mutableUiState = mutableUiState,
            route = route,
            snapshot = snapshot
        )
        DosingCalibrationSnapshotReconciliation.reconcileInterruptedSession(session, snapshot)
        stopInterruptedPrimeIfNeeded(route)
        session.acceptedAuthoritativeSnapshot(snapshot)
        val presentation = DosingCalibrationSnapshotReconciliation.renderAuthoritativeSnapshot(
            session = session,
            mutableUiState = mutableUiState,
            snapshot = snapshot
        )
        if (
            DosingCalibrationSnapshotReconciliation.shouldCompleteFromSnapshot(
                session = session,
                state = mutableUiState.value,
                route = route,
                snapshot = snapshot,
                previousSnapshot = previousSnapshot
            )
        ) {
            DosingCalibrationSnapshotReconciliation.completeFromSnapshot(
                session = session,
                eventChannel = eventChannel,
                snapshot = snapshot
            )
            return
        }
        DosingCalibrationSnapshotReconciliation.scheduleSnapshotCountdown(
            countdown = countdown,
            presentation = presentation,
            onComplete = { verificationDeadline ->
                execute(
                    DosingCalibrationOperation.Refresh,
                    verificationDeadline = verificationDeadline
                )
            }
        )
    }

    private fun stopInterruptedPrimeIfNeeded(route: DeviceDosingCalibrationRoute) {
        if (!session.primeRequested || !mutableUiState.value.isLoading) return
        session.primeRequested = false
        scope.launch {
            session.beginOperation()
            try {
                operationMutex.withLock {
                    val result = operations.primeSafetyStop(route.deviceUid, route.slotId)
                    handleResult(
                        DosingCalibrationOperation.PrimeStop,
                        result,
                        renderSuccess = false
                    )
                }
            } finally {
                session.endOperation()
            }
        }
    }
}

private fun DosingCalibrationWorkflowSession.applyPrimeDirective(
    directive: DosingCalibrationPrimeDirective?,
    startPrimeSafetyWindow: () -> Unit
) {
    when (directive) {
        DosingCalibrationPrimeDirective.START -> startPrimeSafetyWindow()
        DosingCalibrationPrimeDirective.STOP -> {
            primeSafetyJob?.cancel()
            primeRequested = false
        }
        null -> Unit
    }
}

private object DosingCalibrationSnapshotReconciliation {
    fun initializeNameDraftIfNeeded(
        session: DosingCalibrationWorkflowSession,
        mutableUiState: MutableStateFlow<DeviceDosingCalibrationUiState>,
        route: DeviceDosingCalibrationRoute,
        snapshot: DeviceDosingCalibrationSnapshot
    ) {
        if (session.nameDraftInitialized) return
        session.nameDraftInitialized = true
        if (route.recalibration) {
            mutableUiState.value = mutableUiState.value.updateInput { input ->
                input.copy(displayName = snapshot.channelTitle)
            }
        }
    }

    fun reconcileInterruptedSession(
        session: DosingCalibrationWorkflowSession,
        snapshot: DeviceDosingCalibrationSnapshot
    ) {
        if (session.authoritativeSessionInterrupted && !snapshot.hasActiveCalibrationSession) {
            session.hasLocalProgress = false
        }
        session.authoritativeSessionInterrupted = false
    }

    fun renderAuthoritativeSnapshot(
        session: DosingCalibrationWorkflowSession,
        mutableUiState: MutableStateFlow<DeviceDosingCalibrationUiState>,
        snapshot: DeviceDosingCalibrationSnapshot
    ): DosingCalibrationSnapshotPresentation {
        val presentation = reduceDosingCalibrationSnapshot(
            snapshot = snapshot,
            current = mutableUiState.value,
            hasLocalProgress = session.hasLocalProgress
        )
        mutableUiState.value = presentation.state
        return presentation
    }

    fun shouldCompleteFromSnapshot(
        session: DosingCalibrationWorkflowSession,
        state: DeviceDosingCalibrationUiState,
        route: DeviceDosingCalibrationRoute,
        snapshot: DeviceDosingCalibrationSnapshot,
        previousSnapshot: DeviceDosingCalibrationSnapshot?
    ): Boolean {
        val canComplete = !session.exiting && !session.completionEmitted
        val committedConfirmation = canComplete && isCommittedConfirmation(
            session = session,
            state = state,
            snapshot = snapshot,
            previousSnapshot = previousSnapshot
        )
        return committedConfirmation ||
            canComplete && snapshot.shouldAutoComplete(
                isRecalibration = route.recalibration,
                hasLocalProgress = session.hasLocalProgress,
                completionEmitted = session.completionEmitted
            )
    }

    fun isCommittedConfirmation(
        session: DosingCalibrationWorkflowSession,
        state: DeviceDosingCalibrationUiState,
        snapshot: DeviceDosingCalibrationSnapshot,
        previousSnapshot: DeviceDosingCalibrationSnapshot?
    ): Boolean {
        val confirmationReady =
            state.step == DeviceDosingCalibrationStep.CONFIRMATION && session.hasLocalProgress
        return confirmationReady && snapshot.isCommittedCalibrationTransitionFrom(
            previous = previousSnapshot,
            expectedDisplayName = state.displayName
        )
    }

    fun completeFromSnapshot(
        session: DosingCalibrationWorkflowSession,
        eventChannel: Channel<DeviceDosingCalibrationEvent>,
        snapshot: DeviceDosingCalibrationSnapshot
    ) {
        session.completionEmitted = true
        eventChannel.trySend(DeviceDosingCalibrationEvent.Completed(snapshot.toDetailTarget()))
    }

    fun scheduleSnapshotCountdown(
        countdown: DosingCalibrationCountdownController,
        presentation: DosingCalibrationSnapshotPresentation,
        onComplete: (verificationDeadline: Boolean) -> Unit
    ) {
        val verificationDeadline = presentation.countdown is DosingCalibrationCountdown.Verification
        countdown.schedule(
            countdown = presentation.countdown,
            onComplete = { onComplete(verificationDeadline) }
        )
    }
}

private suspend fun performVerificationDeadline(
    operations: DeviceDosingCalibrationOperations,
    route: DeviceDosingCalibrationRoute
): DeviceDosingCalibrationResult = operations.reconcileVerificationDeadline(
    deviceUid = route.deviceUid,
    slotId = route.slotId
)

private fun CoroutineScope.launchTrackedCalibrationOperation(
    session: DosingCalibrationWorkflowSession,
    block: suspend () -> Unit
) = launch {
    session.beginOperation()
    try {
        block()
    } finally {
        session.endOperation()
    }
}

private fun DosingCalibrationWorkflowSession.shouldStopPrimeOnHostStop(
    state: DeviceDosingCalibrationUiState
): Boolean = !exiting &&
    !completionEmitted &&
    primeRequested &&
    state.step == DeviceDosingCalibrationStep.PRIME

private fun DosingCalibrationWorkflowSession.shouldSuppressTransientUnavailable(): Boolean =
    hasInFlightOperation || suppressTransientUnavailable

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
}

private fun DeviceDosingCalibrationUiState.withCalibrationFailure(
    error: DeviceDosingCalibrationError,
    hasAuthoritativeSnapshot: Boolean
): DeviceDosingCalibrationUiState = updateProgress { progress ->
    progress.copy(
        isLoading = !hasAuthoritativeSnapshot,
        isBusy = false,
        isPumpActive = false
    )
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