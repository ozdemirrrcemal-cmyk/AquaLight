package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class DosingCalibrationCountdownController(
    private val clock: DeviceDosingCalibrationClock,
    private val scope: CoroutineScope,
    private val readState: () -> DeviceDosingCalibrationUiState,
    private val writeState: (DeviceDosingCalibrationUiState) -> Unit
) {
    private var job: Job? = null
    private var expiredVerificationPollConsumed = false

    fun cancel() {
        cancelPendingJob()
        expiredVerificationPollConsumed = false
    }

    fun schedule(
        countdown: DosingCalibrationCountdown?,
        onComplete: () -> Unit
    ) {
        when (countdown) {
            is DosingCalibrationCountdown.CalibrationRun -> {
                cancel()
                start(countdown.durationMs, onComplete)
            }
            is DosingCalibrationCountdown.Verification -> {
                if (countdown.durationMs > 0L) {
                    cancel()
                    start(countdown.durationMs, onComplete)
                } else if (!expiredVerificationPollConsumed) {
                    cancelPendingJob()
                    expiredVerificationPollConsumed = true
                    scheduleAuthoritativePoll(onComplete)
                }
            }
            null -> cancel()
        }
    }

    private fun cancelPendingJob() {
        job?.cancel()
        job = null
    }

    private fun start(durationMs: Long, onComplete: () -> Unit) {
        if (durationMs <= 0L) {
            onComplete()
            return
        }
        val startedAt = clock.elapsedRealtime()
        writeState(
            readState().updateProgress { progress ->
                progress.copy(
                    isBusy = true,
                    isPumpActive = true,
                    remainingMs = durationMs
                )
            }
        )
        job = scope.launch {
            while (true) {
                val elapsed = clock.elapsedRealtime() - startedAt
                val remaining = (durationMs - elapsed).coerceAtLeast(0L)
                writeState(
                    readState().updateProgress { progress -> progress.copy(remainingMs = remaining) }
                )
                if (remaining == 0L) break
                delay(COUNTDOWN_TICK_MS.coerceAtMost(remaining))
            }
            onComplete()
        }
    }

    private fun scheduleAuthoritativePoll(onComplete: () -> Unit) {
        job = scope.launch {
            delay(AUTHORITATIVE_POLL_DELAY_MS)
            onComplete()
        }
    }

    private companion object {
        const val COUNTDOWN_TICK_MS = 100L
        const val AUTHORITATIVE_POLL_DELAY_MS = 250L
    }
}
