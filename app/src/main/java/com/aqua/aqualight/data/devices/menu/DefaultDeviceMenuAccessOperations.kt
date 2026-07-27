package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.monitor.DeviceElapsedRealtimeClock
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.toOwnerDeviceFamily
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal interface DeviceMenuRuntimePort {
    fun currentDevice(deviceUid: DeviceUid): DeviceSnapshot?
    fun observeDevice(deviceUid: DeviceUid): Flow<DeviceSnapshot?>
    fun isLocalNetworkAvailable(): Boolean
    fun refreshVisibleDevices(localNetworkAvailable: Boolean)
    suspend fun refreshNow()
    fun runtimeConnectionStates(): Flow<AqlWsConnectionState>?
    fun currentRuntimeConnectionState(deviceUid: DeviceUid): AqlWsConnectionState?
    fun connectRuntime(deviceUid: DeviceUid): Boolean
    fun runtimeEvents(): Flow<AqlWsEvent>?
    suspend fun requestNetworkStatus(deviceUid: DeviceUid): String?
    fun recordControlProof(deviceUid: DeviceUid): DeviceSnapshot?
}

internal class RepositoryDeviceMenuRuntimePort(
    private val devicesRepository: DevicesRepository
) : DeviceMenuRuntimePort {
    override fun currentDevice(deviceUid: DeviceUid): DeviceSnapshot? =
        devicesRepository.currentDevice(deviceUid)

    override fun observeDevice(deviceUid: DeviceUid): Flow<DeviceSnapshot?> =
        devicesRepository.observeDevice(deviceUid)

    override fun isLocalNetworkAvailable(): Boolean =
        devicesRepository.isLocalNetworkAvailable()

    override fun refreshVisibleDevices(localNetworkAvailable: Boolean) {
        devicesRepository.refreshVisibleDevices(localNetworkAvailable = localNetworkAvailable)
    }

    override suspend fun refreshNow() {
        devicesRepository.refreshNow()
    }

    override fun runtimeConnectionStates(): Flow<AqlWsConnectionState>? =
        devicesRepository.runtimeConnectionStates()

    override fun currentRuntimeConnectionState(deviceUid: DeviceUid): AqlWsConnectionState? =
        devicesRepository.currentRuntimeConnectionState(deviceUid)

    override fun connectRuntime(deviceUid: DeviceUid): Boolean =
        devicesRepository.connectRuntime(deviceUid).isSuccess

    override fun runtimeEvents(): Flow<AqlWsEvent>? =
        devicesRepository.runtimeEvents()

    override suspend fun requestNetworkStatus(deviceUid: DeviceUid): String? =
        devicesRepository.commandClient(deviceUid)?.requestNetworkStatus()

    override fun recordControlProof(deviceUid: DeviceUid): DeviceSnapshot? =
        devicesRepository.recordControlProof(deviceUid)
}

internal class DefaultDeviceMenuAccessOperations(
    private val runtimePort: DeviceMenuRuntimePort,
    private val elapsedRealtimeMillis: () -> Long = DeviceElapsedRealtimeClock::nowMillis
) : DeviceMenuAccessOperations {

    private val deviceLocks = ConcurrentHashMap<DeviceUid, Mutex>()

    override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult {
        val requestedDeviceUid = deviceUid.trim()
        if (requestedDeviceUid.isBlank()) {
            return DeviceMenuAccessResult.Unavailable(
                title = "",
                reason = DeviceMenuUnavailableReason.INVALID_DEVICE_UID
            )
        }

        val typedDeviceUid = DeviceUid(requestedDeviceUid)
        val lock = deviceLocks.computeIfAbsent(typedDeviceUid) { Mutex() }
        return lock.withLock {
            resolveLocked(typedDeviceUid)
        }
    }

    private suspend fun resolveLocked(
        deviceUid: DeviceUid
    ): DeviceMenuAccessResult {
        val initialSnapshot = runtimePort.currentDevice(deviceUid)
            ?: return DeviceMenuAccessResult.Unavailable(
                title = "",
                reason = DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED
            )

        val localNetworkAvailable = runtimePort.isLocalNetworkAvailable()
        runtimePort.refreshVisibleDevices(localNetworkAvailable = localNetworkAvailable)
        if (!localNetworkAvailable) {
            return unavailable(
                snapshot = initialSnapshot,
                reason = DeviceMenuUnavailableReason.LOCAL_NETWORK_UNAVAILABLE
            )
        }

        fastFailureReason(initialSnapshot)?.let { reason ->
            return unavailable(
                snapshot = initialSnapshot,
                reason = reason
            )
        }

        if (initialSnapshot.hasRecentControlProof(elapsedRealtimeMillis())) {
            val currentRuntime = runtimePort.currentRuntimeConnectionState(deviceUid)
            if (
                DeviceMenuAuthenticationPolicy.isActiveAuthenticatedSession(
                    state = currentRuntime,
                    requestedDeviceUid = deviceUid
                )
            ) {
                return available(initialSnapshot)
            }
        }

        val gateStartedElapsedMillis = elapsedRealtimeMillis()
        val verifiedSnapshot = withTimeoutOrNull(MENU_ACCESS_BUDGET_MS) {
            runCatching { runtimePort.refreshNow() }

            val refreshedSnapshot = runtimePort.currentDevice(deviceUid) ?: initialSnapshot
            fastFailureReason(refreshedSnapshot)?.let { reason ->
                return@withTimeoutOrNull VerificationResult.Unavailable(reason)
            }

            if (refreshedSnapshot.endpoint.hasWebSocketEndpoint) {
                verifyRuntimeLiveSnapshot(
                    deviceUid = deviceUid,
                    fallbackSnapshot = refreshedSnapshot
                )
            } else {
                val lanSnapshot = verifyFreshLanSnapshot(
                    deviceUid = deviceUid,
                    fallbackSnapshot = refreshedSnapshot,
                    gateStartedElapsedMillis = gateStartedElapsedMillis
                )
                if (lanSnapshot == null) {
                    VerificationResult.Unavailable(
                        DeviceMenuUnavailableReason.VERIFICATION_TIMED_OUT
                    )
                } else {
                    VerificationResult.Available(lanSnapshot)
                }
            }
        } ?: VerificationResult.Unavailable(
            DeviceMenuUnavailableReason.VERIFICATION_TIMED_OUT
        )

        return when (verifiedSnapshot) {
            is VerificationResult.Available -> available(verifiedSnapshot.snapshot)
            is VerificationResult.Unavailable -> unavailable(
                snapshot = runtimePort.currentDevice(deviceUid) ?: initialSnapshot,
                reason = verifiedSnapshot.reason
            )
        }
    }

    private suspend fun verifyRuntimeLiveSnapshot(
        deviceUid: DeviceUid,
        fallbackSnapshot: DeviceSnapshot
    ): VerificationResult {
        return when (awaitAuthenticatedRuntime(deviceUid)) {
            AuthenticationOutcome.Authenticated -> {
                val proofReceived = requestFreshRuntimeProof(deviceUid) || run {
                    delay(RUNTIME_PROBE_RETRY_DELAY_MS)
                    requestFreshRuntimeProof(deviceUid)
                }

                if (!proofReceived) {
                    VerificationResult.Unavailable(
                        DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE
                    )
                } else {
                    val canonicalSnapshot = runtimePort.recordControlProof(deviceUid)
                        ?: runtimePort.currentDevice(deviceUid)
                        ?: fallbackSnapshot
                    VerificationResult.Available(canonicalSnapshot)
                }
            }

            AuthenticationOutcome.AuthRequired -> VerificationResult.Unavailable(
                DeviceMenuUnavailableReason.AUTHENTICATION_REQUIRED
            )

            AuthenticationOutcome.Failed -> VerificationResult.Unavailable(
                DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE
            )

            AuthenticationOutcome.TimedOut -> VerificationResult.Unavailable(
                DeviceMenuUnavailableReason.VERIFICATION_TIMED_OUT
            )
        }
    }

    private suspend fun awaitAuthenticatedRuntime(
        deviceUid: DeviceUid
    ): AuthenticationOutcome = coroutineScope {
        DeviceMenuAuthenticationPolicy.classify(
            state = runtimePort.currentRuntimeConnectionState(deviceUid),
            requestedDeviceUid = deviceUid
        )?.let { currentOutcome ->
            return@coroutineScope currentOutcome
        }

        val connectionStates = runtimePort.runtimeConnectionStates()
            ?: return@coroutineScope AuthenticationOutcome.Failed
        val authenticationSignal = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(AUTHENTICATION_TIMEOUT_MS) {
                connectionStates
                    .mapNotNull { state ->
                        DeviceMenuAuthenticationPolicy.classify(
                            state = state,
                            requestedDeviceUid = deviceUid
                        )
                    }
                    .first()
            } ?: AuthenticationOutcome.TimedOut
        }

        if (!runtimePort.connectRuntime(deviceUid)) {
            authenticationSignal.cancel()
            return@coroutineScope AuthenticationOutcome.Failed
        }

        DeviceMenuAuthenticationPolicy.classify(
            state = runtimePort.currentRuntimeConnectionState(deviceUid),
            requestedDeviceUid = deviceUid
        )?.let { currentOutcome ->
            authenticationSignal.cancel()
            return@coroutineScope currentOutcome
        }

        authenticationSignal.await()
    }

    private suspend fun requestFreshRuntimeProof(
        deviceUid: DeviceUid
    ): Boolean = coroutineScope {
        val runtimeEvents = runtimePort.runtimeEvents()
            ?: return@coroutineScope false
        val expectedRequestId = CompletableDeferred<String>()
        val proofSignal = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(RUNTIME_PROBE_TIMEOUT_MS) {
                runtimeEvents
                    .filter { event ->
                        DeviceMenuRuntimeProofPolicy.accepts(
                            event = event,
                            requestedDeviceUid = deviceUid,
                            expectedRequestId = expectedRequestId.await()
                        )
                    }
                    .first()
            }
        }

        val requestId = runtimePort.requestNetworkStatus(deviceUid)
        if (requestId.isNullOrBlank()) {
            expectedRequestId.cancel()
            proofSignal.cancel()
            return@coroutineScope false
        }

        expectedRequestId.complete(requestId)
        proofSignal.await() != null
    }

    private suspend fun verifyFreshLanSnapshot(
        deviceUid: DeviceUid,
        fallbackSnapshot: DeviceSnapshot,
        gateStartedElapsedMillis: Long
    ): DeviceSnapshot? {
        val currentSnapshot = runtimePort.currentDevice(deviceUid) ?: fallbackSnapshot
        if (currentSnapshot.hasFreshLanProof(gateStartedElapsedMillis)) {
            return currentSnapshot
        }

        val registryFreshLanFlow = runtimePort
            .observeDevice(deviceUid)
            .filterNotNull()
            .filter { snapshot -> snapshot.hasFreshLanProof(gateStartedElapsedMillis) }

        return withTimeoutOrNull(LAN_PROOF_TIMEOUT_MS) {
            registryFreshLanFlow.first()
        } ?: runtimePort.currentDevice(deviceUid)
            ?.takeIf { snapshot -> snapshot.hasFreshLanProof(gateStartedElapsedMillis) }
    }

    private fun fastFailureReason(
        snapshot: DeviceSnapshot
    ): DeviceMenuUnavailableReason? {
        return when (snapshot.connectionState.onlineState) {
            DeviceOnlineState.AUTH_REQUIRED -> {
                DeviceMenuUnavailableReason.AUTHENTICATION_REQUIRED
            }
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE -> {
                DeviceMenuUnavailableReason.LOCAL_NETWORK_UNAVAILABLE
            }
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.ERROR -> {
                DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE
            }
            else -> null
        }
    }

    private fun DeviceSnapshot.hasRecentControlProof(
        nowElapsedMillis: Long
    ): Boolean {
        val proofAt = connectionState.lastControlProofElapsedMillis ?: return false
        return (nowElapsedMillis - proofAt).coerceAtLeast(0L) <= MENU_PROOF_REUSE_MS
    }

    private fun DeviceSnapshot.hasFreshLanProof(
        gateStartedElapsedMillis: Long
    ): Boolean {
        val lastUdpSeenElapsedMillis = connectionState.lastUdpSeenElapsedMillis ?: return false
        return lastUdpSeenElapsedMillis + LAN_PROOF_CLOCK_GRACE_MS >= gateStartedElapsedMillis
    }

    private fun available(snapshot: DeviceSnapshot): DeviceMenuAccessResult.Available {
        return DeviceMenuAccessResult.Available(
            deviceUid = snapshot.deviceUid.value,
            title = snapshot.title.ifBlank { snapshot.deviceUid.value },
            family = snapshot.product.family.toOwnerDeviceFamily()
        )
    }

    private fun unavailable(
        snapshot: DeviceSnapshot,
        reason: DeviceMenuUnavailableReason
    ): DeviceMenuAccessResult.Unavailable {
        return DeviceMenuAccessResult.Unavailable(
            title = snapshot.title.ifBlank { snapshot.deviceUid.value },
            reason = reason
        )
    }

    private sealed interface VerificationResult {
        data class Available(val snapshot: DeviceSnapshot) : VerificationResult
        data class Unavailable(
            val reason: DeviceMenuUnavailableReason
        ) : VerificationResult
    }

    companion object {
        fun create(devicesRepository: DevicesRepository): DefaultDeviceMenuAccessOperations {
            return DefaultDeviceMenuAccessOperations(
                runtimePort = RepositoryDeviceMenuRuntimePort(devicesRepository)
            )
        }

        private const val MENU_ACCESS_BUDGET_MS = 2_500L
        private const val MENU_PROOF_REUSE_MS = 4_000L
        private const val AUTHENTICATION_TIMEOUT_MS = 1_200L
        private const val RUNTIME_PROBE_TIMEOUT_MS = 850L
        private const val RUNTIME_PROBE_RETRY_DELAY_MS = 100L
        private const val LAN_PROOF_TIMEOUT_MS = 1_800L
        private const val LAN_PROOF_CLOCK_GRACE_MS = 250L
    }
}

internal enum class AuthenticationOutcome {
    Authenticated,
    AuthRequired,
    Failed,
    TimedOut
}

internal object DeviceMenuAuthenticationPolicy {

    fun isActiveAuthenticatedSession(
        state: AqlWsConnectionState?,
        requestedDeviceUid: DeviceUid
    ): Boolean {
        return classify(state, requestedDeviceUid) == AuthenticationOutcome.Authenticated
    }

    fun classify(
        state: AqlWsConnectionState?,
        requestedDeviceUid: DeviceUid
    ): AuthenticationOutcome? {
        return when (state) {
            is AqlWsConnectionState.Authenticated -> {
                if (state.deviceUid == requestedDeviceUid) {
                    AuthenticationOutcome.Authenticated
                } else {
                    null
                }
            }
            is AqlWsConnectionState.AuthRequired -> {
                if (state.deviceUid == requestedDeviceUid) {
                    AuthenticationOutcome.AuthRequired
                } else {
                    null
                }
            }
            is AqlWsConnectionState.Failed -> {
                if (state.deviceUid == requestedDeviceUid) {
                    AuthenticationOutcome.Failed
                } else {
                    null
                }
            }
            AqlWsConnectionState.Disconnected,
            is AqlWsConnectionState.Connecting,
            is AqlWsConnectionState.Connected,
            null -> null
        }
    }
}

internal object DeviceMenuRuntimeProofPolicy {
    fun accepts(
        event: AqlWsEvent,
        requestedDeviceUid: DeviceUid,
        expectedRequestId: String
    ): Boolean {
        if (expectedRequestId.isBlank() || event.deviceUid != requestedDeviceUid) {
            return false
        }

        val response = (event as? AqlWsEvent.Message)
            ?.parsed as? AqlWsIncomingMessage.Response
            ?: return false

        if (response.id != expectedRequestId || !response.ok) {
            return false
        }

        if (
            response.module.isNotBlank() &&
            response.module != AqlWsContract.MODULE_NETWORK
        ) {
            return false
        }

        if (
            response.action.isNotBlank() &&
            response.action != AqlWsContract.ACTION_NETWORK_STATUS_GET
        ) {
            return false
        }

        return true
    }
}
