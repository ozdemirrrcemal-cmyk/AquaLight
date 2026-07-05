package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.repository.DeviceDiscoveryRepository
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeRepository
import com.aqua.aqualight.data.devices.store.DeviceRegistryStore
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
        this.localNetworkAvailable.value = localNetworkAvailable
        discoveryRepository.reevaluatePresence(localNetworkAvailable = localNetworkAvailable)
        reevaluateRegistry(localNetworkAvailable = localNetworkAvailable)
    }

    fun refreshVisibleDevices(localNetworkAvailable: Boolean = currentLocalNetworkAvailable()) {
        this.localNetworkAvailable.value = localNetworkAvailable
        reevaluateNow(localNetworkAvailable = localNetworkAvailable)
        if (localNetworkAvailable) {
            probeRuntimeForVisibleDevices()
        }
    }

    private fun CoroutineScope.launchConnectivityWatcher(): Job? {
        val observer = connectivityObserver ?: return null
        return launch {
            observer.observeLocalNetworkAvailable()
                .distinctUntilChanged()
                .collect { available ->
                    localNetworkAvailable.value = available
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
            .filter { snapshot -> force || shouldProbeRuntime(snapshot, nowMillis) }
            .forEach { snapshot -> runtime.connect(snapshot) }
    }

    private fun shouldProbeRuntime(
        snapshot: DeviceSnapshot,
        nowMillis: Long
    ): Boolean {
        val state = snapshot.connectionState
        return when (state.onlineState) {
            DeviceOnlineState.AUTHENTICATED,
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING -> false

            DeviceOnlineState.CONNECTING_WS -> {
                val connectedAt = state.lastWsConnectedAtMillis
                connectedAt == null || nowMillis - connectedAt > CONNECTING_PROBE_GRACE_MS
            }

            DeviceOnlineState.ONLINE_LAN,
            DeviceOnlineState.STALE,
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.UNKNOWN,
            DeviceOnlineState.DISCOVERING,
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            DeviceOnlineState.AUTH_REQUIRED,
            DeviceOnlineState.ERROR -> true
        }
    }

    private fun currentLocalNetworkAvailable(): Boolean =
        connectivityObserver?.isLocalNetworkAvailable() ?: true

    private companion object {
        const val PRESENCE_REEVALUATE_INTERVAL_MS = 3_000L
        const val DISCOVERY_REFRESH_INTERVAL_MS = 5_000L
        const val CONNECTING_PROBE_GRACE_MS = 8_000L
    }
}
