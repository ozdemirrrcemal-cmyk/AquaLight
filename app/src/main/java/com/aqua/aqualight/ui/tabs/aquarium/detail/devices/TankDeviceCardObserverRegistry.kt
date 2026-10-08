package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardState
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightCardState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Owns Tank Devices card-observer lifetimes and their presentation state containers.
 *
 * The ViewModel still owns orchestration; this registry keeps binding resets atomic and prevents
 * stale observer jobs from surviving a tank rebind.
 */
internal class TankDeviceCardObserverRegistry {
    val lightObserverJobs = mutableMapOf<String, Job>()
    val dosingObserverJobs = mutableMapOf<String, Job>()
    val coolingObserverJobs = mutableMapOf<String, Job>()

    val lightCardStates = MutableStateFlow<Map<String, DeviceLightCardState>>(emptyMap())
    val dosingCardStates = MutableStateFlow<Map<String, DeviceDosingCardState>>(emptyMap())
    val coolingCardStates = MutableStateFlow<Map<String, DeviceCoolingCardState>>(emptyMap())

    fun reset() {
        lightObserverJobs.cancelAndClear()
        dosingObserverJobs.cancelAndClear()
        coolingObserverJobs.cancelAndClear()
        lightCardStates.value = emptyMap()
        dosingCardStates.value = emptyMap()
        coolingCardStates.value = emptyMap()
    }
}

private fun MutableMap<String, Job>.cancelAndClear() {
    values.forEach(Job::cancel)
    clear()
}
