package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DeviceDiscoveryRepository
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeRepository
import com.aqua.aqualight.data.devices.repository.reconnectAfterNetworkRestore
import com.aqua.aqualight.data.devices.store.DeviceRegistryStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-level live presence engine for all AquaLight device surfaces.
 *
 * UI screens observe the shared registry and never run their own Online/Offline engines. This
 * monitor owns active discovery, authenticated application-liveness probes, foreground revalidation,
 * Android local-network generation changes and bounded device-scoped recovery.
 */
@Suppress("TooManyFunctions")
class DevicePresenceRuntimeMonitor(
    private val discoveryRepository: DeviceDiscoveryRepository,
    private val registryStore: DeviceRegistryStore,
    private val runtimeRepository: DeviceRuntimeRepository?,
    private val statusAggregator: DeviceStatusAggregator,
    private val connectivityObserver: DeviceConnectivityObserver? = null,
    private val elapsedRealtimeMillis: () -> Long = DeviceElapsedRealtimeClock::nowMillis
) {
    private val started = AtomicBoolean(false)
    private val appForeground = AtomicBoolean(true)
    private val localNetworkAvailable = MutableStateFlow(currentLocalNetworkAvailable())
    private val foregroundVerificationUntilMillis = AtomicLong(0L)
    private val observedNetworkGeneration = AtomicLong(
        connectivityObserver?.currentLocalNetworkGeneration() ?: 0L
    )
    private val lastRuntimeProbeAtMillis = ConcurrentHashMap<DeviceUid, Long>()
    private val livenessProbeCoordinator =
        DeviceAuthenticatedLivenessProbeCoordinator(runtimeRepository)
    private val foregroundRefreshLock = Any()

    @Volatile
    private var runtimeScope: CoroutineScope? = null

    @Volatile
    private var foregroundRefreshJob: Job? = null

    fun start(scope: CoroutineScope): Job {
        if (!started.compareAndSet(false, true)) {
            return Job().also { it.complete() }
        }

        runtimeScope = scope
        return scope.launch {
            val connectivityJob = launchConnectivityWatcher()
            val presenceJob = launchPresenceLoop()
            val discoveryJob = launchDiscoveryRefreshLoop()
            if (appForeground.get()) {
                scheduleForegroundRefresh()
            }

            try {
                presenceJob.join()
            } finally {
                connectivityJob?.cancel()
                presenceJob.cancel()
                discoveryJob.cancel()
                cancelForegroundRefresh()
                livenessProbeCoordinator.reset()
                runtimeScope = null
                started.set(false)
            }
        }
    }

    fun setAppForeground(isForeground: Boolean) {
        val changed = appForeground.getAndSet(isForeground) != isForeground
        if (!isForeground) {
            cancelForegroundRefresh()
            livenessProbeCoordinator.reset()
            return
        }

        beginForegroundVerification()
        if (changed) {
            lastRuntimeProbeAtMillis.clear()
            livenessProbeCoordinator.reset()
        }
        scheduleForegroundRefresh()
    }

    fun reevaluateNow(localNetworkAvailable: Boolean = this.localNetworkAvailable.value) {
        val transitionedToUnavailable =
            this.localNetworkAvailable.value && !localNetworkAvailable
        this.localNetworkAvailable.value = localNetworkAvailable

        if (transitionedToUnavailable) {
            foregroundVerificationUntilMillis.set(0L)
            lastRuntimeProbeAtMillis.clear()
            livenessProbeCoordinator.reset()
            invalidateRuntimeProofsForLocalNetworkLoss()
            runtimeRepository?.disconnectForLocalNetworkLoss()
        }

        discoveryRepository.reevaluatePresence(localNetworkAvailable = localNetworkAvailable)
        reevaluateRegistry(localNetworkAvailable = localNetworkAvailable)
    }

    fun refreshVisibleDevices(localNetworkAvailable: Boolean = currentLocalNetworkAvailable()) {
        if (!localNetworkAvailable) {
            reevaluateNow(localNetworkAvailable = false)
            return
        }

        beginForegroundVerification()
        this.localNetworkAvailable.value = true
        scheduleForegroundRefresh()
    }

    fun isLocalNetworkAvailable(): Boolean = currentLocalNetworkAvailable()

    fun visibleStateDuringForegroundVerification(
        previous: DeviceOnlineState,
        candidate: DeviceOnlineState
    ): DeviceOnlineState {
        if (
            previous != DeviceOnlineState.AUTHENTICATED ||
            candidate !in VERIFICATION_TRANSITION_STATES ||
            !isForegroundVerificationActive()
        ) {
            return candidate
        }
        return DeviceOnlineState.AUTHENTICATED
    }

    private fun scheduleForegroundRefresh() {
        val scope = runtimeScope ?: return
        synchronized(foregroundRefreshLock) {
            if (foregroundRefreshJob?.isActive == true) return

            val job = scope.launch {
                performForegroundRefresh()
            }
            foregroundRefreshJob = job
            job.invokeOnCompletion {
                synchronized(foregroundRefreshLock) {
                    if (foregroundRefreshJob === job) {
                        foregroundRefreshJob = null
                    }
                }
            }
        }
    }

    private fun cancelForegroundRefresh() {
        val job = synchronized(foregroundRefreshLock) {
            foregroundRefreshJob.also {
                foregroundRefreshJob = null
            }
        }
        job?.cancel()
    }

    private fun CoroutineScope.launchConnectivityWatcher(): Job? {
        val observer = connectivityObserver ?: return null
        return launch {
            var networkRecoveryJob: Job? = null
            try {
                observer.observeLocalNetworkPath().collect { path ->
                    networkRecoveryJob?.cancel()

                    val available = path != null
                    val nextGeneration = path?.generation ?: 0L
                    val previousGeneration = observedNetworkGeneration.getAndSet(nextGeneration)
                    val generationChanged = previousGeneration != nextGeneration
                    if (generationChanged) {
                        discoveryRepository.restartScanner(
                            localNetworkAvailable = available
                        )
                    }

                    val networkPathChanged = available &&
                        previousGeneration > 0L &&
                        previousGeneration != nextGeneration
                    if (networkPathChanged) {
                        handleLocalNetworkPathChanged()
                    }

                    reevaluateNow(localNetworkAvailable = available)
                    networkRecoveryJob = if (available && appForeground.get()) {
                        launch { recoverAfterLocalNetworkRestored() }
                    } else {
                        null
                    }
                }
            } finally {
                networkRecoveryJob?.cancel()
            }
        }
    }

    private fun handleLocalNetworkPathChanged() {
        beginForegroundVerification()
        lastRuntimeProbeAtMillis.clear()
        livenessProbeCoordinator.reset()
        invalidateRuntimeProofsForLocalNetworkChange()
        runtimeRepository?.disconnectForLocalNetworkLoss()
    }

    private suspend fun performForegroundRefresh() {
        if (appForeground.get()) {
            val available = currentLocalNetworkAvailable()
            localNetworkAvailable.value = available
            if (!available) {
                reevaluateNow(localNetworkAvailable = false)
            } else {
                beginForegroundVerification()
                probeRuntimeForVisibleDevices(force = true)
                sendAuthenticatedLivenessProbe(force = true)
                refreshForegroundDiscoverySafely()
                if (appForeground.get() && localNetworkAvailable.value) {
                    reevaluateNow(localNetworkAvailable = true)
                }
            }
        }
    }

    /**
     * Android can report Wi-Fi availability before the local route, DHCP lease and multicast path
     * are completely usable. Recovery waits for the LAN to settle and performs one clean,
     * device-scoped retry only for sessions that remain unavailable.
     */
    private suspend fun recoverAfterLocalNetworkRestored() {
        if (!localNetworkAvailable.value || !appForeground.get()) return

        beginForegroundVerification()
        refreshDiscoverySafely()
        delay(NETWORK_RECOVERY_SETTLE_MS)
        if (!localNetworkAvailable.value || !appForeground.get()) return

        reevaluateNow(localNetworkAvailable = true)
        probeRuntimeForVisibleDevices(force = true)
        sendAuthenticatedLivenessProbe(force = true)

        delay(NETWORK_RECOVERY_RETRY_DELAY_MS)
        if (
            !localNetworkAvailable.value ||
            !appForeground.get() ||
            !hasVisibleDevicesAwaitingAuthenticatedRecovery()
        ) {
            return
        }

        refreshDiscoverySafely()
        delay(NETWORK_RECOVERY_SETTLE_MS)
        if (!localNetworkAvailable.value || !appForeground.get()) return

        reevaluateNow(localNetworkAvailable = true)
        retryRuntimeForVisibleDevices()
    }

    private fun CoroutineScope.launchPresenceLoop(): Job = launch {
        while (isActive) {
            if (appForeground.get()) {
                val available = localNetworkAvailable.value
                reevaluateNow(localNetworkAvailable = available)
                if (available) {
                    probeRuntimeForVisibleDevices()
                    sendAuthenticatedLivenessProbe()
                }
                delay(PRESENCE_REEVALUATE_INTERVAL_MS)
            } else {
                delay(BACKGROUND_IDLE_INTERVAL_MS)
            }
        }
    }

    private fun CoroutineScope.launchDiscoveryRefreshLoop(): Job = launch {
        while (isActive) {
            if (appForeground.get() && localNetworkAvailable.value) {
                refreshDiscoverySafely()
                reevaluateNow(localNetworkAvailable = true)
                delay(DISCOVERY_REFRESH_INTERVAL_MS)
            } else {
                delay(BACKGROUND_IDLE_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshDiscoverySafely() {
        try {
            discoveryRepository.refreshNow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Presence reevaluation and bounded runtime recovery remain active.
        }
    }

    private suspend fun refreshForegroundDiscoverySafely() {
        try {
            discoveryRepository.refreshForegroundBurst()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // A later periodic refresh or authenticated application probe can still prove liveness.
        }
    }

    private fun hasVisibleDevicesAwaitingAuthenticatedRecovery(): Boolean {
        return registryStore.currentDevices().any { snapshot ->
            snapshot.endpoint.hasWebSocketEndpoint &&
                DeviceNetworkRecoveryPolicy.shouldRetry(snapshot.connectionState.onlineState)
        }
    }

    private fun retryRuntimeForVisibleDevices() {
        val runtime = runtimeRepository ?: return
        val nowMillis = elapsedRealtimeMillis()
        registryStore.currentDevices()
            .asSequence()
            .filter { snapshot -> snapshot.endpoint.hasWebSocketEndpoint }
            .filter { snapshot ->
                DeviceNetworkRecoveryPolicy.shouldRetry(snapshot.connectionState.onlineState)
            }
            .forEach { snapshot ->
                lastRuntimeProbeAtMillis[snapshot.deviceUid] = nowMillis
                runtime.reconnectAfterNetworkRestore(snapshot)
            }
    }

    private fun reevaluateRegistry(localNetworkAvailable: Boolean) {
        val nowMillis = elapsedRealtimeMillis()
        registryStore.currentDevices().forEach { snapshot ->
            registryStore.updateConnectionState(snapshot.deviceUid) { previous ->
                val resolved = statusAggregator.resolve(
                    state = previous,
                    nowElapsedMillis = nowMillis,
                    localNetworkAvailable = localNetworkAvailable
                )
                val visibleState = visibleStateDuringForegroundVerification(
                    previous = previous.onlineState,
                    candidate = resolved
                )
                if (previous.onlineState == visibleState) {
                    previous
                } else {
                    previous.copy(onlineState = visibleState)
                }
            }
        }
    }

    private fun invalidateRuntimeProofsForLocalNetworkLoss() {
        registryStore.currentDevices().forEach { snapshot ->
            registryStore.updateConnectionState(snapshot.deviceUid) { previous ->
                previous.copy(
                    onlineState = DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
                    lastWsConnectedAtMillis = null,
                    lastWsConnectedElapsedMillis = null,
                    lastAuthenticatedAtMillis = null,
                    lastAuthenticatedElapsedMillis = null,
                    lastRuntimeMessageAtMillis = null,
                    lastRuntimeMessageElapsedMillis = null,
                    lastControlProofAtMillis = null,
                    lastControlProofElapsedMillis = null
                )
            }
        }
    }

    private fun invalidateRuntimeProofsForLocalNetworkChange() {
        registryStore.currentDevices().forEach { snapshot ->
            registryStore.updateConnectionState(snapshot.deviceUid) { previous ->
                previous.copy(
                    onlineState = if (
                        appForeground.get() &&
                        previous.onlineState == DeviceOnlineState.AUTHENTICATED
                    ) {
                        DeviceOnlineState.AUTHENTICATED
                    } else {
                        DeviceOnlineState.UNKNOWN
                    },
                    lastWsConnectedAtMillis = null,
                    lastWsConnectedElapsedMillis = null,
                    lastAuthenticatedAtMillis = null,
                    lastAuthenticatedElapsedMillis = null,
                    lastRuntimeMessageAtMillis = null,
                    lastRuntimeMessageElapsedMillis = null,
                    lastControlProofAtMillis = null,
                    lastControlProofElapsedMillis = null
                )
            }
        }
    }

    private fun probeRuntimeForVisibleDevices(force: Boolean = false) {
        val runtime = runtimeRepository ?: return
        val nowMillis = elapsedRealtimeMillis()
        registryStore.currentDevices()
            .asSequence()
            .filter { snapshot -> snapshot.endpoint.hasWebSocketEndpoint }
            .filter { snapshot ->
                (
                    force &&
                        snapshot.connectionState.onlineState !in NON_RECONNECTABLE_STATES
                    ) || shouldProbeRuntime(
                    state = snapshot.connectionState.onlineState,
                    deviceUid = snapshot.deviceUid,
                    nowMillis = nowMillis
                )
            }
            .forEach { snapshot ->
                lastRuntimeProbeAtMillis[snapshot.deviceUid] = nowMillis
                runtime.connect(snapshot)
            }
    }

    private fun sendAuthenticatedLivenessProbe(force: Boolean = false) {
        val scope = runtimeScope ?: return
        val nowMillis = elapsedRealtimeMillis()
        registryStore.currentDevices().forEach { snapshot ->
            livenessProbeCoordinator.request(
                scope = scope,
                deviceUid = snapshot.deviceUid,
                nowMillis = nowMillis,
                force = force,
                minimumIntervalMillis = AUTHENTICATED_LIVENESS_PROBE_INTERVAL_MS,
                timeoutMillis = AUTHENTICATED_LIVENESS_PROBE_TIMEOUT_MS
            )
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

    private fun beginForegroundVerification() {
        if (!appForeground.get()) return
        foregroundVerificationUntilMillis.set(
            elapsedRealtimeMillis() + FOREGROUND_REVALIDATION_GRACE_MS
        )
    }

    private fun isForegroundVerificationActive(): Boolean {
        return appForeground.get() &&
            elapsedRealtimeMillis() < foregroundVerificationUntilMillis.get()
    }

    private fun currentLocalNetworkAvailable(): Boolean =
        connectivityObserver?.isLocalNetworkAvailable() ?: true

    private companion object {
        const val PRESENCE_REEVALUATE_INTERVAL_MS = 2_000L
        const val DISCOVERY_REFRESH_INTERVAL_MS = 5_000L
        const val BACKGROUND_IDLE_INTERVAL_MS = 15_000L
        const val RUNTIME_PROBE_BACKOFF_MS = 15_000L
        const val OFFLINE_PROBE_BACKOFF_MS = 7_500L
        const val AUTHENTICATED_LIVENESS_PROBE_INTERVAL_MS = 8_000L
        const val AUTHENTICATED_LIVENESS_PROBE_TIMEOUT_MS = 3_000L
        const val FOREGROUND_REVALIDATION_GRACE_MS = 3_000L
        const val NETWORK_RECOVERY_SETTLE_MS = 1_000L
        const val NETWORK_RECOVERY_RETRY_DELAY_MS = 6_000L

        val VERIFICATION_TRANSITION_STATES = setOf(
            DeviceOnlineState.UNKNOWN,
            DeviceOnlineState.DISCOVERING,
            DeviceOnlineState.ONLINE_LAN,
            DeviceOnlineState.CONNECTING_WS,
            DeviceOnlineState.STALE
        )

        val NON_RECONNECTABLE_STATES = setOf(
            DeviceOnlineState.AUTH_REQUIRED,
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING
        )
    }
}

internal object DeviceNetworkRecoveryPolicy {

    fun shouldRetry(state: DeviceOnlineState): Boolean {
        return when (state) {
            DeviceOnlineState.ONLINE_LAN,
            DeviceOnlineState.CONNECTING_WS,
            DeviceOnlineState.UNKNOWN,
            DeviceOnlineState.DISCOVERING,
            DeviceOnlineState.STALE,
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            DeviceOnlineState.ERROR -> true

            DeviceOnlineState.AUTHENTICATED,
            DeviceOnlineState.AUTH_REQUIRED,
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING -> false
        }
    }
}
