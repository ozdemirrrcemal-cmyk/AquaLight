package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
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
        primeSafetyJob?.cancel()
        actionJob?.cancel()
        primeSafetyJob = null
        actionJob = null
        primeRequested = false
        this.route = route
        hasLocalProgress = false
        exiting = false
        completionEmitted = false
        latestSnapshot = null
    }
}
