package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSnapshot
import kotlinx.coroutines.Job

internal class DosingCalibrationWorkflowSession {
    var route: DeviceDosingCalibrationRoute? = null
    var observeJob: Job? = null
    var primeSafetyJob: Job? = null
    var actionJob: Job? = null
    var hasLocalProgress: Boolean = false
    var primeRequested: Boolean = false
    var exiting: Boolean = false
    var completionEmitted: Boolean = false
    var latestSnapshot: DeviceDosingCalibrationSnapshot? = null

    fun reset(route: DeviceDosingCalibrationRoute) {
        this.route = route
        hasLocalProgress = false
        primeRequested = false
        exiting = false
        completionEmitted = false
        latestSnapshot = null
    }

    fun cancelTransientJobs() {
        primeSafetyJob?.cancel()
        actionJob?.cancel()
    }
}
