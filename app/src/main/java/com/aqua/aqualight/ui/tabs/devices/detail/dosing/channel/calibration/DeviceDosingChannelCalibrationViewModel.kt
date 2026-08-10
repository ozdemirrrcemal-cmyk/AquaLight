@file:Suppress(
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "LargeClass",
    "LongMethod",
    "LongParameterList",
    "ReturnCount",
    "TooManyFunctions"
)

package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceDosingChannelCalibrationViewModel(
    private val operations: DeviceDosingCalibrationOperations,
    private val clock: DeviceDosingCalibrationClock = SystemDeviceDosingCalibrationClock
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceDosingCalibrationUiState())
    val uiState: StateFlow<DeviceDosingCalibrationUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<DeviceDosingCalibrationEvent>(Channel.BUFFERED)
    val events: Flow<DeviceDosingCalibrationEvent> = eventChannel.receiveAsFlow()

    private var deviceUid = ""
    private var slotId = ""
    private var observeJob: Job? = null
    private var countdownJob: Job? = null
    private var primeSafetyJob: Job? = null
    private var actionJob: Job? = null
    private var hasLocalProgress = false
    private var primeRequested = false
    private var exiting = false
    private var completionEmitted = false
    private var latestSnapshot: DeviceDosingCalibrationSnapshot? = null

    fun bind(
        deviceUid: String,
        slotId: String,
        pumpCount: Int,
        channelNumber: Int,
        channelTitle: String
    ) {
        val normalizedUid = deviceUid.trim()
        val normalizedSlot = slotId.trim()
        if (normalizedUid.isBlank() || normalizedSlot.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = DeviceDosingCalibrationError.UNAVAILABLE
            )
            return
        }
        if (this.deviceUid == normalizedUid && this.slotId == normalizedSlot) return

        this.deviceUid = normalizedUid
        this.slotId = normalizedSlot
        hasLocalProgress = false
        exiting = false
        completionEmitted = false
        latestSnapshot = null
        _uiState.value = DeviceDosingCalibrationUiState(
            displayName = channelTitle,
            pumpCount = pumpCount,
            channelNumber = channelNumber,
            channelTitle = channelTitle
        )
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            operations.observe(normalizedUid, normalizedSlot).collect { snapshot ->
                if (snapshot != null && isStillBound(snapshot)) applySnapshot(snapshot)
            }
        }
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            handleResult(operations.refresh(normalizedUid, normalizedSlot))
        }
    }

    fun updateDisplayName(value: String) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(
            displayName = value.take(MAX_DISPLAY_NAME_CHARACTERS),
            error = null
        )
    }

    fun saveDisplayNameAndContinue() {
        val name = _uiState.value.displayName.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = DeviceDosingCalibrationError.INVALID_NAME
            )
            return
        }
        runAction {
            when (val result = operations.saveDisplayName(deviceUid, slotId, name)) {
                is DeviceDosingCalibrationResult.Success -> {
                    latestSnapshot = result.snapshot
                    hasLocalProgress = true
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isBusy = false,
                        step = DeviceDosingCalibrationStep.PRIME,
                        channelTitle = result.snapshot.channelTitle,
                        displayName = result.snapshot.channelTitle,
                        error = null
                    )
                }
                else -> handleResult(result)
            }
        }
    }

    fun primePressed() {
        if (_uiState.value.step != DeviceDosingCalibrationStep.PRIME || primeRequested) return
        primeRequested = true
        _uiState.value = _uiState.value.copy(isPumpActive = true, error = null)
        primeSafetyJob?.cancel()
        primeSafetyJob = viewModelScope.launch {
            delay(PRIME_SAFETY_TIMEOUT_MS)
            if (primeRequested) {
                primeRequested = false
                _uiState.value = _uiState.value.copy(isPumpActive = false)
                handleResult(operations.primeStop(deviceUid, slotId), renderSuccess = false)
            }
        }
        viewModelScope.launch {
            val result = operations.primeStart(deviceUid, slotId)
            if (result !is DeviceDosingCalibrationResult.Success) {
                primeRequested = false
                _uiState.value = _uiState.value.copy(isPumpActive = false)
                handleResult(result)
            } else if (!primeRequested) {
                operations.primeStop(deviceUid, slotId)
            }
        }
    }

    fun primeReleased() {
        if (!primeRequested && !_uiState.value.isPumpActive) return
        primeSafetyJob?.cancel()
        primeRequested = false
        _uiState.value = _uiState.value.copy(isPumpActive = false)
        viewModelScope.launch {
            handleResult(operations.primeStop(deviceUid, slotId), renderSuccess = false)
        }
    }

    fun continueFromPrime() {
        if (_uiState.value.isBusy) return
        primeRequested = false
        primeSafetyJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isBusy = true,
            isPumpActive = false,
            error = null
        )
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            when (val result = operations.primeStop(deviceUid, slotId)) {
                is DeviceDosingCalibrationResult.Success -> {
                    latestSnapshot = result.snapshot
                    hasLocalProgress = true
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        step = DeviceDosingCalibrationStep.CALIBRATION_RUN,
                        error = null
                    )
                }
                else -> handleResult(result)
            }
        }
    }

    fun startCalibration() = runAction {
        when (val result = operations.start(deviceUid, slotId)) {
            is DeviceDosingCalibrationResult.Success -> {
                hasLocalProgress = true
                applySnapshot(result.snapshot)
            }
            else -> handleResult(result)
        }
    }

    fun updateMeasuredMl(value: String) {
        if (_uiState.value.isBusy) return
        val filtered = value.filter { character ->
            character.isDigit() || character == '.' || character == ','
        }.take(MAX_MEASUREMENT_CHARACTERS)
        _uiState.value = _uiState.value.copy(measuredMl = filtered, error = null)
    }

    fun saveMeasurement() {
        val measuredMl = parsedMeasurement()
        if (measuredMl == null || measuredMl !in MIN_MEASURED_ML..MAX_MEASURED_ML) {
            _uiState.value = _uiState.value.copy(
                error = DeviceDosingCalibrationError.INVALID_MEASUREMENT
            )
            return
        }
        runAction {
            when (val result = operations.finish(deviceUid, slotId, measuredMl)) {
                is DeviceDosingCalibrationResult.Success -> {
                    hasLocalProgress = true
                    applySnapshot(result.snapshot)
                }
                else -> handleResult(result)
            }
        }
    }

    fun startVerificationDose() = runAction {
        when (val result = operations.startVerificationDose(deviceUid, slotId)) {
            is DeviceDosingCalibrationResult.Success -> {
                hasLocalProgress = true
                applySnapshot(result.snapshot)
            }
            else -> handleResult(result)
        }
    }

    fun acceptVerification() = runAction {
        when (val result = operations.confirm(deviceUid, slotId)) {
            is DeviceDosingCalibrationResult.Success -> {
                latestSnapshot = result.snapshot
                completionEmitted = true
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    isPumpActive = false,
                    error = null
                )
                eventChannel.send(
                    DeviceDosingCalibrationEvent.Completed(result.snapshot.toDetailTarget())
                )
            }
            else -> handleResult(result)
        }
    }

    fun rejectVerification() = runAction {
        when (val result = operations.cancel(deviceUid, slotId)) {
            is DeviceDosingCalibrationResult.Success -> {
                latestSnapshot = result.snapshot
                hasLocalProgress = true
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    isPumpActive = false,
                    remainingMs = 0L,
                    measuredMl = "",
                    step = DeviceDosingCalibrationStep.CALIBRATION_RUN,
                    error = null
                )
            }
            else -> handleResult(result)
        }
    }

    fun requestExit() {
        if (exiting) return
        exiting = true
        countdownJob?.cancel()
        primeSafetyJob?.cancel()
        val snapshot = latestSnapshot
        val step = _uiState.value.step
        viewModelScope.launch {
            if (step == DeviceDosingCalibrationStep.PRIME &&
                (primeRequested || _uiState.value.isPumpActive)
            ) {
                primeRequested = false
                operations.primeStop(deviceUid, slotId)
            }
            if (step == DeviceDosingCalibrationStep.VERIFICATION &&
                snapshot?.verificationDoseStarted == true &&
                !snapshot.verificationDoseComplete
            ) {
                operations.stopVerificationDose(deviceUid, slotId)
            }
            if (snapshot?.sessionPhase != null &&
                snapshot.sessionPhase != DeviceDosingCalibrationSessionPhase.IDLE
            ) {
                operations.cancel(deviceUid, slotId)
            }
            eventChannel.send(DeviceDosingCalibrationEvent.Exit)
        }
    }

    fun onHostStopped() {
        if (primeRequested && _uiState.value.step == DeviceDosingCalibrationStep.PRIME) {
            primeReleased()
        }
    }

    private fun runAction(block: suspend () -> Unit) {
        if (_uiState.value.isBusy || deviceUid.isBlank() || slotId.isBlank()) return
        actionJob?.cancel()
        _uiState.value = _uiState.value.copy(isBusy = true, error = null)
        actionJob = viewModelScope.launch { block() }
    }

    private fun applySnapshot(snapshot: DeviceDosingCalibrationSnapshot) {
        latestSnapshot = snapshot
        val current = _uiState.value
        val recoveryStep = when (snapshot.sessionPhase) {
            DeviceDosingCalibrationSessionPhase.IDLE ->
                if (hasLocalProgress) current.step else DeviceDosingCalibrationStep.NAME
            DeviceDosingCalibrationSessionPhase.RUNNING -> {
                if (runningRemainingMs(snapshot) > 0L) {
                    DeviceDosingCalibrationStep.CALIBRATION_RUN
                } else DeviceDosingCalibrationStep.MEASUREMENT
            }
            DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION -> when {
                snapshot.verificationDoseComplete -> DeviceDosingCalibrationStep.CONFIRMATION
                else -> DeviceDosingCalibrationStep.VERIFICATION
            }
        }
        val remainingMs = when {
            snapshot.sessionPhase == DeviceDosingCalibrationSessionPhase.RUNNING ->
                runningRemainingMs(snapshot)
            snapshot.verificationDoseStarted && !snapshot.verificationDoseComplete ->
                snapshot.verificationDoseRemainingMs
            else -> 0L
        }
        _uiState.value = current.copy(
            isLoading = false,
            isBusy = remainingMs > 0L,
            step = recoveryStep,
            displayName = snapshot.channelTitle,
            pumpCount = snapshot.pumpCount,
            channelNumber = snapshot.channelNumber,
            channelTitle = snapshot.channelTitle,
            isPumpActive = snapshot.manualActive,
            remainingMs = remainingMs,
            candidateDoseMsPerMl = snapshot.pendingDoseMsPerMl.takeIf { it > 0L },
            error = null
        )
        if (
            snapshot.sessionPhase == DeviceDosingCalibrationSessionPhase.IDLE &&
            snapshot.calibrated &&
            !hasLocalProgress &&
            !completionEmitted
        ) {
            completionEmitted = true
            eventChannel.trySend(
                DeviceDosingCalibrationEvent.Completed(snapshot.toDetailTarget())
            )
            return
        }
        if (snapshot.sessionPhase == DeviceDosingCalibrationSessionPhase.RUNNING &&
            remainingMs > 0L
        ) {
            startCountdown(remainingMs) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    isPumpActive = false,
                    remainingMs = 0L,
                    step = DeviceDosingCalibrationStep.MEASUREMENT
                )
            }
        } else if (
            snapshot.sessionPhase == DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION &&
            snapshot.verificationDoseStarted &&
            !snapshot.verificationDoseComplete &&
            snapshot.verificationDoseRemainingMs > 0L
        ) {
            startCountdown(snapshot.verificationDoseRemainingMs) {
                viewModelScope.launch { handleResult(operations.refresh(deviceUid, slotId)) }
            }
        }
    }

    private fun startCountdown(durationMs: Long, onComplete: () -> Unit) {
        countdownJob?.cancel()
        if (durationMs <= 0L) {
            onComplete()
            return
        }
        val startedAt = clock.elapsedRealtime()
        _uiState.value = _uiState.value.copy(
            isBusy = true,
            isPumpActive = true,
            remainingMs = durationMs
        )
        countdownJob = viewModelScope.launch {
            while (true) {
                val elapsed = clock.elapsedRealtime() - startedAt
                val remaining = (durationMs - elapsed).coerceAtLeast(0L)
                _uiState.value = _uiState.value.copy(remainingMs = remaining)
                if (remaining == 0L) break
                delay(COUNTDOWN_TICK_MS.coerceAtMost(remaining))
            }
            onComplete()
        }
    }

    private fun handleResult(
        result: DeviceDosingCalibrationResult,
        renderSuccess: Boolean = true
    ) {
        when (result) {
            is DeviceDosingCalibrationResult.Success -> {
                if (renderSuccess) applySnapshot(result.snapshot)
            }
            DeviceDosingCalibrationResult.Unavailable -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isBusy = false,
                    isPumpActive = false,
                    error = DeviceDosingCalibrationError.UNAVAILABLE
                )
            }
            DeviceDosingCalibrationResult.Failed -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isBusy = false,
                    isPumpActive = false,
                    error = DeviceDosingCalibrationError.CONNECTION
                )
            }
        }
    }

    private fun parsedMeasurement(): Double? = _uiState.value.measuredMl
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf(Double::isFinite)

    private fun runningRemainingMs(snapshot: DeviceDosingCalibrationSnapshot): Long {
        val elapsedMs = (snapshot.deviceUptimeMs - snapshot.startedAtUptimeMs)
            .and(UINT32_MASK)
        return (snapshot.durationMs - elapsedMs).coerceAtLeast(0L)
    }

    private fun isStillBound(snapshot: DeviceDosingCalibrationSnapshot): Boolean =
        snapshot.deviceUid == deviceUid && snapshot.slotId == slotId

    private fun DeviceDosingCalibrationSnapshot.toDetailTarget() =
        DeviceDosingChannelNavigationTarget(
            deviceUid = deviceUid,
            slotId = slotId,
            pumpCount = pumpCount,
            channelNumber = channelNumber,
            channelTitle = channelTitle,
            destination = DeviceDosingChannelDestination.DETAIL
        )

    private companion object {
        const val COUNTDOWN_TICK_MS = 100L
        const val PRIME_SAFETY_TIMEOUT_MS = 30_000L
        const val MAX_DISPLAY_NAME_CHARACTERS = 32
        const val MAX_MEASUREMENT_CHARACTERS = 8
        const val UINT32_MASK = 0xFFFF_FFFFL
        const val MIN_MEASURED_ML = 0.05
        const val MAX_MEASURED_ML = 1_000.0
    }
}

enum class DeviceDosingCalibrationStep {
    NAME,
    PRIME,
    CALIBRATION_RUN,
    MEASUREMENT,
    VERIFICATION,
    CONFIRMATION
}

enum class DeviceDosingCalibrationError {
    INVALID_NAME,
    INVALID_MEASUREMENT,
    CONNECTION,
    UNAVAILABLE
}

data class DeviceDosingCalibrationUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val step: DeviceDosingCalibrationStep = DeviceDosingCalibrationStep.NAME,
    val displayName: String = "",
    val measuredMl: String = "",
    val pumpCount: Int = 0,
    val channelNumber: Int = 0,
    val channelTitle: String = "",
    val isPumpActive: Boolean = false,
    val remainingMs: Long = 0L,
    val candidateDoseMsPerMl: Long? = null,
    val error: DeviceDosingCalibrationError? = null
)

sealed interface DeviceDosingCalibrationEvent {
    data object Exit : DeviceDosingCalibrationEvent
    data class Completed(
        val target: DeviceDosingChannelNavigationTarget
    ) : DeviceDosingCalibrationEvent
}

fun interface DeviceDosingCalibrationClock {
    fun elapsedRealtime(): Long
}

private object SystemDeviceDosingCalibrationClock : DeviceDosingCalibrationClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
