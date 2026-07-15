package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.toOwnerDeviceFamily
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
}

internal class DefaultDeviceMenuAccessOperations(
    private val runtimePort: DeviceMenuRuntimePort
) : DeviceMenuAccessOperations {

    override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult {
        val requestedDeviceUid = deviceUid.trim()
        if (requestedDeviceUid.isBlank()) {
            return DeviceMenuAccessResult.Unavailable(
                title = "",
                reason = DeviceMenuUnavailableReason.INVALID_DEVICE_UID
            )
        }

        val gateStartedAtMillis = System.currentTimeMillis()
        val typedDeviceUid = DeviceUid(requestedDeviceUid)
        val initialSnapshot = runtimePort.currentDevice(typedDeviceUid)
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

        runCatching { runtimePort.refreshNow() }

        val refreshedSnapshot = runtimePort.currentDevice(typedDeviceUid) ?: initialSnapshot
        val liveSnapshot = verifyLiveSnapshot(
            deviceUid = typedDeviceUid,
            fallbackSnapshot = refreshedSnapshot,
            gateStartedAtMillis = gateStartedAtMillis
        )

        if (liveSnapshot != null) {
            return DeviceMenuAccessResult.Available(
                deviceUid = liveSnapshot.deviceUid.value,
                title = liveSnapshot.title.ifBlank { liveSnapshot.deviceUid.value },
                family = liveSnapshot.product.family.toOwnerDeviceFamily()
            )
        }

        return unavailable(
            snapshot = runtimePort.currentDevice(typedDeviceUid) ?: refreshedSnapshot,
            reason = DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
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

    private suspend fun verifyLiveSnapshot(
        deviceUid: DeviceUid,
        fallbackSnapshot: DeviceSnapshot,
        gateStartedAtMillis: Long
    ): DeviceSnapshot? {
        if (fallbackSnapshot.endpoint.hasWebSocketEndpoint) {
            return verifyRuntimeLiveSnapshot(
                deviceUid = deviceUid,
                fallbackSnapshot = fallbackSnapshot,
                gateStartedAtMillis = gateStartedAtMillis
            )
        }

        val currentSnapshot = runtimePort.currentDevice(deviceUid) ?: fallbackSnapshot
        if (currentSnapshot.hasFreshLanProof(gateStartedAtMillis)) {
            return currentSnapshot
        }

        return waitForFreshLanSnapshot(
            deviceUid = deviceUid,
            fallbackSnapshot = fallbackSnapshot,
            gateStartedAtMillis = gateStartedAtMillis
        )
    }

    private suspend fun verifyRuntimeLiveSnapshot(
        deviceUid: DeviceUid,
        fallbackSnapshot: DeviceSnapshot,
        gateStartedAtMillis: Long
    ): DeviceSnapshot? = coroutineScope {
        if (
            !awaitAuthenticatedRuntime(
                deviceUid = deviceUid,
                fallbackSnapshot = fallbackSnapshot,
                gateStartedAtMillis = gateStartedAtMillis
            )
        ) {
            return@coroutineScope null
        }

        if (!requestFreshRuntimeProof(deviceUid)) {
            delay(RUNTIME_PROBE_RETRY_DELAY_MS)
            if (!requestFreshRuntimeProof(deviceUid)) {
                return@coroutineScope null
            }
        }

        return@coroutineScope runtimePort.currentDevice(deviceUid) ?: fallbackSnapshot
    }

    private suspend fun awaitAuthenticatedRuntime(
        deviceUid: DeviceUid,
        fallbackSnapshot: DeviceSnapshot,
        gateStartedAtMillis: Long
    ): Boolean {
        if (
            awaitAuthenticatedRuntimeAttempt(
                deviceUid = deviceUid,
                fallbackSnapshot = fallbackSnapshot,
                gateStartedAtMillis = gateStartedAtMillis,
                timeoutMillis = INITIAL_AUTHENTICATION_TIMEOUT_MS
            )
        ) {
            return true
        }

        delay(RECONNECT_RETRY_DELAY_MS)

        return awaitAuthenticatedRuntimeAttempt(
            deviceUid = deviceUid,
            fallbackSnapshot = runtimePort.currentDevice(deviceUid) ?: fallbackSnapshot,
            gateStartedAtMillis = gateStartedAtMillis,
            timeoutMillis = RETRY_AUTHENTICATION_TIMEOUT_MS
        )
    }

    private suspend fun awaitAuthenticatedRuntimeAttempt(
        deviceUid: DeviceUid,
        fallbackSnapshot: DeviceSnapshot,
        gateStartedAtMillis: Long,
        timeoutMillis: Long
    ): Boolean = coroutineScope {
        val connectionStates = runtimePort.runtimeConnectionStates()
            ?: return@coroutineScope false
        val authenticationSignal = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(timeoutMillis) {
                connectionStates
                    .filter { state ->
                        DeviceMenuAuthenticationPolicy.accepts(
                            state = state,
                            requestedDeviceUid = deviceUid,
                            gateStartedAtMillis = gateStartedAtMillis
                        )
                    }
                    .first()
            }
        }

        if (!runtimePort.connectRuntime(deviceUid)) {
            authenticationSignal.cancel()
            return@coroutineScope false
        }

        val currentState = runtimePort.currentRuntimeConnectionState(deviceUid)
        if (
            DeviceMenuAuthenticationPolicy.isActiveAuthenticatedSession(
                state = currentState,
                requestedDeviceUid = deviceUid
            )
        ) {
            authenticationSignal.cancel()
            return@coroutineScope true
        }

        return@coroutineScope authenticationSignal.await() != null
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
        return@coroutineScope proofSignal.await() != null
    }

    private suspend fun waitForFreshLanSnapshot(
        deviceUid: DeviceUid,
        fallbackSnapshot: DeviceSnapshot,
        gateStartedAtMillis: Long
    ): DeviceSnapshot? {
        val registryFreshLanFlow = runtimePort
            .observeDevice(deviceUid)
            .filterNotNull()
            .filter { snapshot -> snapshot.hasFreshLanProof(gateStartedAtMillis) }

        return withTimeoutOrNull(STRICT_LIVE_CHECK_TIMEOUT_MS) {
            registryFreshLanFlow.first()
        } ?: runtimePort.currentDevice(deviceUid)
            ?.takeIf { snapshot -> snapshot.hasFreshLanProof(gateStartedAtMillis) }
            ?: fallbackSnapshot.takeIf { snapshot -> snapshot.hasFreshLanProof(gateStartedAtMillis) }
    }

    private fun DeviceSnapshot.hasFreshLanProof(
        gateStartedAtMillis: Long
    ): Boolean {
        val lastUdpSeenAtMillis = connectionState.lastUdpSeenAtMillis ?: return false
        return lastUdpSeenAtMillis + LAN_PROOF_CLOCK_GRACE_MS >= gateStartedAtMillis
    }

    companion object {
        fun create(devicesRepository: DevicesRepository): DefaultDeviceMenuAccessOperations {
            return DefaultDeviceMenuAccessOperations(
                runtimePort = RepositoryDeviceMenuRuntimePort(devicesRepository)
            )
        }

        private const val INITIAL_AUTHENTICATION_TIMEOUT_MS = 6_000L
        private const val RECONNECT_RETRY_DELAY_MS = 4_000L
        private const val RETRY_AUTHENTICATION_TIMEOUT_MS = 12_000L
        private const val RUNTIME_PROBE_TIMEOUT_MS = 3_000L
        private const val RUNTIME_PROBE_RETRY_DELAY_MS = 250L
        private const val STRICT_LIVE_CHECK_TIMEOUT_MS = 12_000L
        private const val LAN_PROOF_CLOCK_GRACE_MS = 1_000L
    }
}

internal object DeviceMenuAuthenticationPolicy {
    fun isActiveAuthenticatedSession(
        state: AqlWsConnectionState?,
        requestedDeviceUid: DeviceUid
    ): Boolean {
        val authenticated = state as? AqlWsConnectionState.Authenticated ?: return false
        return authenticated.deviceUid == requestedDeviceUid
    }

    fun accepts(
        state: AqlWsConnectionState,
        requestedDeviceUid: DeviceUid,
        gateStartedAtMillis: Long
    ): Boolean {
        val authenticated = state as? AqlWsConnectionState.Authenticated ?: return false
        return authenticated.deviceUid == requestedDeviceUid &&
            authenticated.authenticatedAtMillis >= gateStartedAtMillis
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

        val moduleMatches = response.module.isBlank() ||
            response.module == AqlWsContract.MODULE_NETWORK
        val actionMatches = response.action.isBlank() ||
            response.action == AqlWsContract.ACTION_NETWORK_STATUS_GET

        return moduleMatches && actionMatches
    }
}
