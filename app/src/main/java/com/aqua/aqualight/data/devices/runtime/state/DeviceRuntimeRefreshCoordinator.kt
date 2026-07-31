package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.json.JSONObject

internal class DeviceRuntimeRefreshCoordinator(
    private val stateStore: DeviceRuntimeStateStore,
    private val commandExecutor: DeviceRuntimeCommandExecutor,
    private val scopeProvider: () -> CoroutineScope?,
    private val commandTimeoutMillis: Long,
    private val refreshDebounceMillis: Long = DEFAULT_STATUS_REFRESH_DEBOUNCE_MS,
    private val jobs: DeviceRuntimeRefreshJobRegistry = DeviceRuntimeRefreshJobRegistry()
) {
    private val knownDevicesLock = Any()
    private val knownDeviceUids = mutableSetOf<DeviceUid>()
    private val bootstrappedGenerations = ConcurrentHashMap<DeviceUid, Long>()

    init {
        require(refreshDebounceMillis in MIN_REFRESH_DEBOUNCE_MS..MAX_REFRESH_DEBOUNCE_MS) {
            "refreshDebounceMillis is outside the runtime coordination range."
        }
    }

    fun reconcile(snapshots: Map<DeviceUid, DeviceSnapshot>) {
        val retired = synchronized(knownDevicesLock) {
            val removed = knownDeviceUids - snapshots.keys
            knownDeviceUids.clear()
            knownDeviceUids.addAll(snapshots.keys)
            removed
        }
        retired.forEach(::retire)
        snapshots.values.forEach(::maybeBootstrap)
    }

    fun maybeBootstrap(snapshot: DeviceSnapshot) {
        val authenticated = stateStore.current(snapshot.deviceUid).authenticated
        if (snapshot.hasValidatedRuntimeMetadata && authenticated) {
            scopeProvider()?.let { scope ->
                val generation = snapshot.runtimeMetadataGeneration
                val alreadyStarted = bootstrappedGenerations.put(
                    snapshot.deviceUid,
                    generation
                ) == generation
                if (!alreadyStarted) {
                    val targets = DeviceRuntimeRefreshCatalog.bootstrapTargets(snapshot)
                    stateStore.beginBootstrap(snapshot.deviceUid, generation, targets)
                    startBootstrap(scope, snapshot.deviceUid, targets)
                }
            }
        }
    }

    fun schedule(
        deviceUid: DeviceUid,
        targets: Set<DeviceRuntimeRefreshTarget>
    ) {
        if (targets.isNotEmpty()) {
            scopeProvider()?.let { scope ->
                startScheduledRefresh(scope, deviceUid, targets)
            }
        }
    }

    suspend fun refreshTargets(
        deviceUid: DeviceUid,
        targets: Set<DeviceRuntimeRefreshTarget>
    ): Map<DeviceRuntimeRefreshTarget, DeviceRuntimeCommandOutcome> {
        val outcomes = linkedMapOf<DeviceRuntimeRefreshTarget, DeviceRuntimeCommandOutcome>()
        for (target in targets) {
            outcomes[target] = refreshOne(deviceUid, target)
        }
        return outcomes
    }

    suspend fun refreshOne(
        deviceUid: DeviceUid,
        target: DeviceRuntimeRefreshTarget
    ): DeviceRuntimeCommandOutcome {
        val command = DeviceRuntimeRefreshCatalog.command(target)
        return commandExecutor.execute(
            DeviceRuntimeCommandRequest(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                data = JSONObject(),
                timeoutMillis = commandTimeoutMillis
            )
        )
    }

    fun onUnavailable(deviceUid: DeviceUid, reason: String) {
        bootstrappedGenerations.remove(deviceUid)
        jobs.cancelDevice(deviceUid)
        commandExecutor.cancelDevice(deviceUid, reason)
    }

    fun close() {
        jobs.cancelAll()
        bootstrappedGenerations.clear()
        synchronized(knownDevicesLock) {
            knownDeviceUids.clear()
        }
    }

    private fun startBootstrap(
        scope: CoroutineScope,
        deviceUid: DeviceUid,
        targets: Set<DeviceRuntimeRefreshTarget>
    ) {
        jobs.replaceBootstrap(deviceUid) {
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    refreshTargets(deviceUid, targets)
                } finally {
                    jobs.completeBootstrap(deviceUid, coroutineContext.job)
                }
            }
        }
    }

    private fun startScheduledRefresh(
        scope: CoroutineScope,
        deviceUid: DeviceUid,
        targets: Set<DeviceRuntimeRefreshTarget>
    ) {
        jobs.enqueueAndStartRefresh(deviceUid, targets) {
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    delay(refreshDebounceMillis)
                    val batch = jobs.takeTargets(deviceUid)
                    if (batch.isNotEmpty()) {
                        refreshTargets(deviceUid, batch)
                    }
                } finally {
                    val restart = jobs.completeRefresh(deviceUid, coroutineContext.job)
                    if (restart) {
                        startScheduledRefresh(scope, deviceUid, emptySet())
                    }
                }
            }
        }
    }

    private fun retire(deviceUid: DeviceUid) {
        bootstrappedGenerations.remove(deviceUid)
        jobs.cancelDevice(deviceUid)
        commandExecutor.cancelDevice(deviceUid, DEVICE_RETIRED_REASON)
        stateStore.retire(deviceUid)
    }

    companion object {
        const val DEFAULT_STATUS_REFRESH_DEBOUNCE_MS = 120L
        private const val MIN_REFRESH_DEBOUNCE_MS = 50L
        private const val MAX_REFRESH_DEBOUNCE_MS = 5_000L
        private const val DEVICE_RETIRED_REASON = "device retired"
    }
}
