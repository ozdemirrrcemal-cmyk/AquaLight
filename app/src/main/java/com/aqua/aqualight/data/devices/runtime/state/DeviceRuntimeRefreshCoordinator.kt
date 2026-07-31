package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Device/generation/target-scoped refresh deduplication owned by the runtime repository. */
class DeviceRuntimeRefreshCoordinator(
    private val scopeProvider: (DeviceUid) -> CoroutineScope?,
    private val generationProvider: (DeviceUid) -> DeviceRuntimeConnectionGeneration?,
    private val stateProvider: (DeviceUid) -> DeviceRuntimeState?,
    private val refreshAction:
        suspend (DeviceUid, DeviceRuntimeConnectionGeneration, DeviceRuntimeStateTarget) -> Unit,
    private val eventDebounceMillis: Long = DEFAULT_EVENT_DEBOUNCE_MILLIS
) {
    init {
        require(eventDebounceMillis >= 0L)
    }

    private data class RefreshKey(
        val deviceUid: DeviceUid,
        val generation: DeviceRuntimeConnectionGeneration,
        val target: DeviceRuntimeStateTarget
    )

    private val jobs = ConcurrentHashMap<RefreshKey, Job>()

    fun refreshBootstrap(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        support: DeviceRuntimeSupport
    ) {
        buildList {
            if (support.security) add(DeviceRuntimeStateTarget.SECURITY)
            if (support.network) add(DeviceRuntimeStateTarget.NETWORK)
            if (support.time) add(DeviceRuntimeStateTarget.TIME)
            if (support.firmware) add(DeviceRuntimeStateTarget.FIRMWARE)
            if (support.light) add(DeviceRuntimeStateTarget.LIGHT)
            if (support.lightTemperatureProtection) {
                add(DeviceRuntimeStateTarget.LIGHT_TEMPERATURE_PROTECTION)
            }
            if (support.timer) add(DeviceRuntimeStateTarget.TIMER)
            if (support.dosing) add(DeviceRuntimeStateTarget.DOSING)
            if (support.cooling) add(DeviceRuntimeStateTarget.COOLING)
        }.forEach { target ->
            schedule(deviceUid, generation, target, delayMillis = 0L)
        }
    }

    fun scheduleEventRefresh(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        target: DeviceRuntimeStateTarget
    ): Boolean = schedule(
        deviceUid = deviceUid,
        generation = generation,
        target = target,
        delayMillis = eventDebounceMillis
    )

    fun schedule(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        target: DeviceRuntimeStateTarget,
        delayMillis: Long = 0L
    ): Boolean {
        require(delayMillis >= 0L)
        if (generationProvider(deviceUid) != generation) return false
        if (!stateProvider(deviceUid).supports(target)) return false
        val scope = scopeProvider(deviceUid) ?: return false
        val key = RefreshKey(deviceUid, generation, target)

        val candidate = scope.launch {
            if (delayMillis > 0L) delay(delayMillis)
            if (generationProvider(deviceUid) != generation) return@launch
            if (!stateProvider(deviceUid).supports(target)) return@launch
            refreshAction(deviceUid, generation, target)
        }
        val existing = jobs.putIfAbsent(key, candidate)
        if (existing != null) {
            candidate.cancel()
            return false
        }
        candidate.invokeOnCompletion { jobs.remove(key, candidate) }
        return true
    }

    fun cancelGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ) {
        jobs.entries
            .filter { (key, _) -> key.deviceUid == deviceUid && key.generation == generation }
            .forEach { (key, job) ->
                if (jobs.remove(key, job)) job.cancel()
            }
    }

    fun cancelDevice(deviceUid: DeviceUid) {
        jobs.entries
            .filter { (key, _) -> key.deviceUid == deviceUid }
            .forEach { (key, job) ->
                if (jobs.remove(key, job)) job.cancel()
            }
    }

    fun clear() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
    }

    internal fun pendingCount(): Int = jobs.size

    private fun DeviceRuntimeState?.supports(target: DeviceRuntimeStateTarget): Boolean {
        val state = this ?: return false
        return when (target) {
            DeviceRuntimeStateTarget.METADATA -> state.authenticated
            DeviceRuntimeStateTarget.SECURITY -> state.support.security
            DeviceRuntimeStateTarget.NETWORK -> state.support.network
            DeviceRuntimeStateTarget.TIME -> state.support.time
            DeviceRuntimeStateTarget.LIGHT -> state.support.light
            DeviceRuntimeStateTarget.LIGHT_TEMPERATURE_PROTECTION ->
                state.support.lightTemperatureProtection
            DeviceRuntimeStateTarget.TIMER -> state.support.timer
            DeviceRuntimeStateTarget.DOSING -> state.support.dosing
            DeviceRuntimeStateTarget.COOLING -> state.support.cooling
            DeviceRuntimeStateTarget.FIRMWARE -> state.support.firmware
            DeviceRuntimeStateTarget.OTA -> state.support.ota
        }
    }

    companion object {
        private const val DEFAULT_EVENT_DEBOUNCE_MILLIS = 150L
    }
}
