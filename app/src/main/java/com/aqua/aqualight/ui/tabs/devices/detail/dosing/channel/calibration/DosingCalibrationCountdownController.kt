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

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun schedule(
        countdown: DosingCalibrationCountdown?,
        onVerificationComplete: () -> Unit
    ) {
        when (countdown) {
            is DosingCalibrationCountdown.CalibrationRun -> {
                cancel()
                start(
                    durationMs = countdown.durationMs,
                    onComplete = {
                        writeState(
                            readState()
                                .updateProgress { progress ->
                                    progress.copy(
                                        isBusy = false,
                                        isPumpActive = false,
                                        remainingMs = 0L,
                                        step = DeviceDosingCalibrationStep.MEASUREMENT
                                    )
                                }
                        )
                    }
                )
            }
            is DosingCalibrationCountdown.Verification -> {
                cancel()
                start(
                    durationMs = countdown.durationMs,
                    onComplete = onVerificationComplete
                )
            }
            null -> Unit
        }
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

    private companion object {
        const val COUNTDOWN_TICK_MS = 100L
    }
}
