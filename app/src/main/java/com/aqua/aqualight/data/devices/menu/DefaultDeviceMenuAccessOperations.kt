package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.monitor.DeviceElapsedRealtimeClock
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.repository.recordControlFailure
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
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
import kotlinx.coroutines.withTimeoutOrNull

@Suppress("TooManyFunctions")
internal interface DeviceMenuRuntimePort {
    fun currentDevice(deviceUid: DeviceUid): DeviceSnapshot?
    fun observeDevice(deviceUid: DeviceUid): Flow<DeviceSnapshot?>
    fun isLocalNetworkAvailable(): Boolean
    fun refreshVisibleDevices(localNetworkAvailable: Boolean)
    suspend fun refreshNow()
    fun runtimeConnectionStates(): Flow<AqlWsConnectionState>?
    fun currentRuntimeConnectionState(deviceUid: DeviceUid): AqlWsConnectionState?
    fun connectRuntime(deviceUid: DeviceUid): Boolean

    /** Returns true only after a current-generation proof is committed to the registry. */
    suspend fun proveCurrentLiveness(deviceUid: DeviceUid): Boolean

    fun recordControlFailure(deviceUid: DeviceUid): DeviceSnapshot? = null
}

@Suppress("TooManyFunctions")
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

    override suspend fun proveCurrentLiveness(deviceUid: DeviceUid): Boolean {
        val outcome = devicesRepository.runtimeModules()?.network?.requestStatus(deviceUid)
        val success = outcome as? DeviceRuntimeCommandOutcome.Success<*> ?: return false
        return devicesRepository.recordControlProofIfCurrentGeneration(
            deviceUid = deviceUid,
            generation = success.generation
        ) != null
    }

    override fun recordControlFailure(deviceUid: DeviceUid): DeviceSnapshot? =
        devicesRepository.recordControlFailure(deviceUid)
}

@Suppress("TooManyFunctions")
internal class DefaultDeviceMenuAccessOperations(
    private val runtimePort: DeviceMenuRuntimePort,
    private val elapsedRealtimeMillis: () -> Long = DeviceElapsedRealtimeClock::nowMillis
) : DeviceMenuAccessOperations {

    private val inFlight = ConcurrentHashMap<
        DeviceUid,
        CompletableDeferred<DeviceMenuAccessResult>
    >()

    override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult {
        val requestedDeviceUid = deviceUid.trim()
        return if (requestedDeviceUid.isBlank()) {
            DeviceMenuAccessResult.Unavailable(
                title = "",
                reason = DeviceMenuUnavailableReason.INVALID_DEVICE_UID
            )
        } else {
            resolveSerialized(DeviceUid(requestedDeviceUid))
        }
    }

    private suspend fun resolveSerialized(
        deviceUid: DeviceUid
    ): DeviceMenuAccessResult {
        val leader = CompletableDeferred<DeviceMenuAccessResult>()
        val existing = inFlight.putIfAbsent(deviceUid, leader)

        return if (existing != null) {
            existing.await()
        } else {
            try {
                val outcome = runCatching { resolveSingle(deviceUid) }
                outcome.fold(
                    onSuccess = { result ->
                        leader.complete(result)
                        result
                    },
                    onFailure = { error ->
                        leader.completeExceptionally(error)
                        throw error
                    }
                )
            } finally {
                inFlight.remove(deviceUid, leader)
            }
        }
    }

    private suspend fun resolveSingle(
        deviceUid: DeviceUid
    ): DeviceMenuAccessResult {
        return when (val preparation = prepareAccess(deviceUid)) {
            is AccessPreparation.Immediate -> preparation.result
            is AccessPreparation.Verify -> verifyPreparedAccess(
                deviceUid = deviceUid,
                initialSnapshot = preparation.snapshot
            )
        }
    }

    private fun prepareAccess(deviceUid: DeviceUid): AccessPreparation {
        val initialSnapshot = runtimePort.currentDevice(deviceUid)
        return if (initialSnapshot == null) {
            AccessPreparation.Immediate(
                DeviceMenuAccessResult.Unavailable(
                    title = "",
                    reason = DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED
                )
            )
        } else if (!runtimePort.isLocalNetworkAvailable()) {
            AccessPreparation.Immediate(
                unavailable(
                    snapshot = initialSnapshot,
                    reason = DeviceMenuUnavailableReason.LOCAL_NETWORK_UNAVAILABLE
                )
            )
        } else {
            prepareNetworkAvailableAccess(
                deviceUid = deviceUid,
                initialSnapshot = initialSnapshot
            )
        }
    }

    private fun prepareNetworkAvailableAccess(
        deviceUid: DeviceUid,
        initialSnapshot: DeviceSnapshot
    ): AccessPreparation {
        runtimePort.refreshVisibleDevices(localNetworkAvailable = true)
        val failureReason = fastFailureReason(initialSnapshot)
        val activeRuntime = runtimePort.currentRuntimeConnectionState(deviceUid)
        val reusableProof = initialSnapshot.hasRecentControlProof(elapsedRealtimeMillis()) &&
            DeviceMenuAuthenticationPolicy.isActiveAuthenticatedSession(
                state = activeRuntime,
                requestedDeviceUid = deviceUid
            )

        return when {
            failureReason != null -> AccessPreparation.Immediate(
                unavailable(initialSnapshot, failureReason)
            )
            reusableProof -> AccessPreparation.Immediate(available(initialSnapshot))
            else -> AccessPreparation.Verify(initialSnapshot)
        }
    }

    private suspend fun verifyPreparedAccess(
        deviceUid: DeviceUid,
        initialSnapshot: DeviceSnapshot
    ): DeviceMenuAccessResult {
        val verification = withTimeoutOrNull(MENU_ACCESS_BUDGET_MS) {
            runCatching { runtimePort.refreshNow() }
            val refreshedSnapshot = runtimePort.currentDevice(deviceUid) ?: initialSnapshot
            val failureReason = fastFailureReason(refreshedSnapshot)

            when {
                failureReason != null -> VerificationResult.Unavailable(failureReason)
                else -> verifyDiscoveredRuntimeEndpoint(deviceUid, refreshedSnapshot)
            }
        } ?: unavailableAfterControlFailure(
            deviceUid = deviceUid,
            reason = DeviceMenuUnavailableReason.VERIFICATION_TIMED_OUT
        )

        return when (verification) {
            is VerificationResult.Available -> available(verification.snapshot)
            is VerificationResult.Unavailable -> unavailable(
                snapshot = runtimePort.currentDevice(deviceUid) ?: initialSnapshot,
                reason = verification.reason
            )
        }
    }

    private suspend fun verifyDiscoveredRuntimeEndpoint(
        deviceUid: DeviceUid,
        fallbackSnapshot: DeviceSnapshot
    ): VerificationResult {
        val runtimeSnapshot = awaitRuntimeEndpointSnapshot(
            deviceUid = deviceUid,
            fallbackSnapshot = fallbackSnapshot
        ) ?: return VerificationResult.Unavailable(
            DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
        )
        val failureReason = fastFailureReason(runtimeSnapshot)

        return when {
            !runtimePort.isLocalNetworkAvailable() -> VerificationResult.Unavailable(
                DeviceMenuUnavailableReason.LOCAL_NETWORK_UNAVAILABLE
            )
            failureReason != null -> VerificationResult.Unavailable(failureReason)
            else -> verifyRuntimeLiveSnapshot(deviceUid)
        }
    }

    private suspend fun awaitRuntimeEndpointSnapshot(
        deviceUid: DeviceUid,
        fallbackSnapshot: DeviceSnapshot
    ): DeviceSnapshot? {
        val currentSnapshot = runtimePort.currentDevice(deviceUid) ?: fallbackSnapshot
        if (currentSnapshot.endpoint.hasWebSocketEndpoint) {
            return currentSnapshot
        }

        val runtimeEndpointFlow = runtimePort
            .observeDevice(deviceUid)
            .filterNotNull()
            .filter { snapshot -> snapshot.endpoint.hasWebSocketEndpoint }

        return withTimeoutOrNull(RUNTIME_ENDPOINT_DISCOVERY_TIMEOUT_MS) {
            runtimeEndpointFlow.first()
        } ?: runtimePort.currentDevice(deviceUid)
            ?.takeIf { snapshot -> snapshot.endpoint.hasWebSocketEndpoint }
    }

    private suspend fun verifyRuntimeLiveSnapshot(
        deviceUid: DeviceUid
    ): VerificationResult {
        return when (awaitAuthenticatedRuntime(deviceUid)) {
            AuthenticationOutcome.Authenticated -> verifyAuthenticatedRuntime(deviceUid)
            AuthenticationOutcome.AuthRequired -> VerificationResult.Unavailable(
                DeviceMenuUnavailableReason.AUTHENTICATION_REQUIRED
            )
            AuthenticationOutcome.Failed -> unavailableAfterControlFailure(
                deviceUid = deviceUid,
                reason = DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE
            )
            AuthenticationOutcome.TimedOut -> unavailableAfterControlFailure(
                deviceUid = deviceUid,
                reason = DeviceMenuUnavailableReason.VERIFICATION_TIMED_OUT
            )
        }
    }

    private suspend fun verifyAuthenticatedRuntime(
        deviceUid: DeviceUid
    ): VerificationResult {
        val proofReceived = requestFreshRuntimeProof(deviceUid) || run {
            delay(RUNTIME_PROBE_RETRY_DELAY_MS)
            requestFreshRuntimeProof(deviceUid)
        }
        val canonicalSnapshot = if (proofReceived) {
            runtimePort.currentDevice(deviceUid)
        } else {
            null
        }

        return canonicalSnapshot?.let(VerificationResult::Available)
            ?: unavailableAfterControlFailure(
                deviceUid = deviceUid,
                reason = DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE
            )
    }

    private fun unavailableAfterControlFailure(
        deviceUid: DeviceUid,
        reason: DeviceMenuUnavailableReason
    ): VerificationResult.Unavailable {
        runtimePort.recordControlFailure(deviceUid)
        return VerificationResult.Unavailable(reason)
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
    ): Boolean = withTimeoutOrNull(RUNTIME_PROBE_TIMEOUT_MS) {
        runtimePort.proveCurrentLiveness(deviceUid)
    } ?: false

    private fun fastFailureReason(
        snapshot: DeviceSnapshot
    ): DeviceMenuUnavailableReason? {
        return when (snapshot.connectionState.onlineState) {
            DeviceOnlineState.AUTH_REQUIRED -> {
                DeviceMenuUnavailableReason.AUTHENTICATION_REQUIRED
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

    private sealed interface AccessPreparation {
        data class Immediate(
            val result: DeviceMenuAccessResult
        ) : AccessPreparation

        data class Verify(
            val snapshot: DeviceSnapshot
        ) : AccessPreparation
    }

    private sealed interface VerificationResult {
        data class Available(val snapshot: DeviceSnapshot) : VerificationResult
        data class Unavailable(
            val reason: DeviceMenuUnavailableReason
        ) : VerificationResult
    }

    companion object {
        fun create(devicesRepository: DevicesRepository): DeviceMenuAccessOperations {
            val livenessOperations = DefaultDeviceMenuAccessOperations(
                runtimePort = RepositoryDeviceMenuRuntimePort(devicesRepository)
            )
            return CommercialDeviceMenuAccessOperations(
                livenessOperations = livenessOperations,
                currentSnapshot = devicesRepository::currentDevice
            )
        }

        private const val MENU_ACCESS_BUDGET_MS = 2_500L
        private const val MENU_PROOF_REUSE_MS = 4_000L
        private const val AUTHENTICATION_TIMEOUT_MS = 1_200L
        private const val RUNTIME_ENDPOINT_DISCOVERY_TIMEOUT_MS = 350L
        private const val RUNTIME_PROBE_TIMEOUT_MS = 850L
        private const val RUNTIME_PROBE_RETRY_DELAY_MS = 100L
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
