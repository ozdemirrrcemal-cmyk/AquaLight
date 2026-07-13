package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DeviceDiscoveryRepository
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeRepository
import com.aqua.aqualight.data.devices.store.DeviceRegistryStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-level live presence engine for all AquaLight device surfaces.
 *
 * UI screens must observe DevicesRepository snapshots. They must not run their own online/offline
 * engines. This monitor owns active LAN discovery probes, runtime WebSocket probes, local-network
 * availability, and time-based presence reevaluation for the shared registry.
 */
class DevicePresenceRuntimeMonitor(
    private val discoveryRepository: DeviceDiscoveryRepository,
    private val registryStore: DeviceRegistryStore,
    private val runtimeRepository: DeviceRuntimeRepository?,
    private val statusAggregator: DeviceStatusAggregator,
    private val connectivityObserver: DeviceConnectivityObserver? = null,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val started = AtomicBoolean(false)
    private val localNetworkAvailable = MutableStateFlow(currentLocalNetworkAvailable())
    private val lastRuntimeProbeAtMillis = ConcurrentHashMap<DeviceUid, Long>()

    fun start(scope: CoroutineScope): Job {
        if (!started.compareAndSet(false, true)) {
            return Job().also { it.complete() }
        }

        return scope.launch {
            val connectivityJob = launchConnectivityWatcher()
            val presenceJob = launchPresenceLoop()
            val discoveryJob = launchDiscoveryRefreshLoop()

            try {
                presenceJob.join()
            } finally {
                connectivityJob?.cancel()
                presenceJob.cancel()
                discoveryJob.cancel()
                started.set(false)
            }
        }
    }

    fun reevaluateNow(localNetworkAvailable: Boolean = this.localNetworkAvailable.value) {
        val transitionedToUnavailable =
            this.localNetworkAvailable.value && !localNetworkAvailable
        this.localNetworkAvailable.value = localNetworkAvailable
        if (transitionedToUnavailable) {
            lastRuntimeProbeAtMillis.clear()
            runtimeRepository?.disconnectForLocalNetworkLoss()
        }
        discoveryRepository.reevaluatePresence(localNetworkAvailable = localNetworkAvailable)
        reevaluateRegistry(localNetworkAvailable = localNetworkAvailable)
    }

    fun refreshVisibleDevices(localNetworkAvailable: Boolean = currentLocalNetworkAvailable()) {
        reevaluateNow(localNetworkAvailable = localNetworkAvailable)
        if (localNetworkAvailable) {
            probeRuntimeForVisibleDevices()
        }
    }

    fun isLocalNetworkAvailable(): Boolean = currentLocalNetworkAvailable()

    private fun CoroutineScope.launchConnectivityWatcher(): Job? {
        val observer = connectivityObserver ?: return null
        return launch {
            observer.observeLocalNetworkAvailable()
                .distinctUntilChanged()
                .collect { available ->
                    reevaluateNow(localNetworkAvailable = available)
                    if (available) {
                        runCatching { discoveryRepository.refreshNow() }
                        probeRuntimeForVisibleDevices(force = true)
                    }
                }
        }
    }

    private fun CoroutineScope.launchPresenceLoop(): Job = launch {
        while (isActive) {
            val available = localNetworkAvailable.value
            reevaluateNow(localNetworkAvailable = available)
            if (available) {
                probeRuntimeForVisibleDevices()
            }
            delay(PRESENCE_REEVALUATE_INTERVAL_MS)
        }
    }

    private fun CoroutineScope.launchDiscoveryRefreshLoop(): Job = launch {
        while (isActive) {
            if (localNetworkAvailable.value) {
                runCatching { discoveryRepository.refreshNow() }
                reevaluateNow(localNetworkAvailable = true)
            }
            delay(DISCOVERY_REFRESH_INTERVAL_MS)
        }
    }

    private fun reevaluateRegistry(localNetworkAvailable: Boolean) {
        val nowMillis = clockMillis()
        registryStore.currentDevices().forEach { snapshot ->
            registryStore.updateConnectionState(snapshot.deviceUid) { previous ->
                val resolved = statusAggregator.resolve(
                    state = previous,
                    nowMillis = nowMillis,
                    localNetworkAvailable = localNetworkAvailable
                )
                if (previous.onlineState == resolved) {
                    previous
                } else {
                    previous.copy(onlineState = resolved)
                }
            }
        }
    }

    private fun probeRuntimeForVisibleDevices(force: Boolean = false) {
        val runtime = runtimeRepository ?: return
        val nowMillis = clockMillis()
        registryStore.currentDevices()
            .asSequence()
            .filter { snapshot -> snapshot.endpoint.hasWebSocketEndpoint }
            .filter { snapshot -> force || shouldProbeRuntime(snapshot.connectionState.onlineState, snapshot.deviceUid, nowMillis) }
            .forEach { snapshot ->
                lastRuntimeProbeAtMillis[snapshot.deviceUid] = nowMillis
                runtime.connect(snapshot)
            }
    }

    private fun shouldProbeRuntime(
        state: DeviceOnlineState,
        deviceUid: DeviceUid,
        nowMillis: Long
    ): Boolean {
        val lastProbeAt = lastRuntimeProbeAtMillis[deviceUid]
        if (lastProbeAt != null && nowMillis - lastProbeAt < probeBackoffFor(state)) {
            return false
        }

        return when (state) {
            DeviceOnlineState.ONLINE_LAN,
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.STALE,
            DeviceOnlineState.UNKNOWN,
            DeviceOnlineState.DISCOVERING,
            DeviceOnlineState.ERROR -> true

            DeviceOnlineState.AUTHENTICATED,
            DeviceOnlineState.CONNECTING_WS,
            DeviceOnlineState.AUTH_REQUIRED,
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING,
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE -> false
        }
    }

    private fun probeBackoffFor(state: DeviceOnlineState): Long {
        return when (state) {
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.STALE,
            DeviceOnlineState.ERROR -> OFFLINE_PROBE_BACKOFF_MS
            else -> RUNTIME_PROBE_BACKOFF_MS
        }
    }

    private fun currentLocalNetworkAvailable(): Boolean =
        connectivityObserver?.isLocalNetworkAvailable() ?: true

    private companion object {
        const val PRESENCE_REEVALUATE_INTERVAL_MS = 3_000L
        const val DISCOVERY_REFRESH_INTERVAL_MS = 5_000L
        const val RUNTIME_PROBE_BACKOFF_MS = 25_000L
        const val OFFLINE_PROBE_BACKOFF_MS = 10_000L
    }
}
