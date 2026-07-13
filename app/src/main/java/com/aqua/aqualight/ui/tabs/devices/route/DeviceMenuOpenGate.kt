package com.aqua.aqualight.ui.tabs.devices.route

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class DeviceMenuOpenGate(
    private val devicesRepository: DevicesRepository,
    private val routeResolver: DeviceRouteResolver = DeviceRouteResolver()
) {

    suspend fun resolve(
        deviceUidText: String
    ): DeviceMenuOpenGateResult {
        val requestedDeviceUid = deviceUidText.trim()
        if (requestedDeviceUid.isBlank()) {
            return DeviceMenuOpenGateResult.Blocked(
                title = "",
                messageRes = R.string.device_menu_offline_message
            )
        }

        val gateStartedAtMillis = System.currentTimeMillis()
        val deviceUid = DeviceUid(requestedDeviceUid)
        val initialSnapshot = devicesRepository.currentDevice(deviceUid)
            ?: return DeviceMenuOpenGateResult.Blocked(
                title = "",
                messageRes = R.string.device_menu_offline_message
            )

        val localNetworkAvailable = devicesRepository.isLocalNetworkAvailable()
        devicesRepository.refreshVisibleDevices(localNetworkAvailable = localNetworkAvailable)
        if (!localNetworkAvailable) {
            return DeviceMenuOpenGateResult.Blocked(
                title = initialSnapshot.title,
                messageRes = R.string.device_menu_offline_message
            )
        }

        runCatching {
            devicesRepository.refreshNow()
        }

        val refreshedSnapshot = devicesRepository.currentDevice(deviceUid) ?: initialSnapshot
        val liveSnapshot = verifyLiveSnapshot(
            deviceUid = deviceUid,
            fallbackSnapshot = refreshedSnapshot,
            gateStartedAtMillis = gateStartedAtMillis
        )

        if (liveSnapshot != null) {
            return DeviceMenuOpenGateResult.OpenRoute(
                route = routeResolver.resolve(
                    snapshot = liveSnapshot,
                    requestedDeviceUid = requestedDeviceUid
                )
            )
        }

        val finalSnapshot = devicesRepository.currentDevice(deviceUid) ?: refreshedSnapshot
        return DeviceMenuOpenGateResult.Blocked(
            title = finalSnapshot.title,
            messageRes = R.string.device_menu_offline_message
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

        val currentSnapshot = devicesRepository.currentDevice(deviceUid) ?: fallbackSnapshot
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
            return@coroutineScope null
        }

        return@coroutineScope devicesRepository.currentDevice(deviceUid)
            ?: fallbackSnapshot
    }

    private suspend fun awaitAuthenticatedRuntime(
        deviceUid: DeviceUid,
        fallbackSnapshot: DeviceSnapshot,
        gateStartedAtMillis: Long
    ): Boolean = coroutineScope {
        val connectionStates = devicesRepository.runtimeConnectionStates()
            ?: return@coroutineScope false
        val authenticationSignal = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(AUTHENTICATION_TIMEOUT_MS) {
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

        val connectStarted = fallbackSnapshot.connectRuntimeIfPossible(deviceUid)
        if (!connectStarted) {
            authenticationSignal.cancel()
            return@coroutineScope false
        }

        val currentState = devicesRepository.currentRuntimeConnectionState(deviceUid)
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
        val runtimeEvents = devicesRepository.runtimeEvents()
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

        val requestId = devicesRepository
            .commandClient(deviceUid)
            ?.requestNetworkStatus()
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
        val registryFreshLanFlow = devicesRepository
            .observeDevice(deviceUid)
            .filterNotNull()
            .filter { snapshot -> snapshot.hasFreshLanProof(gateStartedAtMillis) }

        return withTimeoutOrNull(STRICT_LIVE_CHECK_TIMEOUT_MS) {
            registryFreshLanFlow.first()
        } ?: devicesRepository.currentDevice(deviceUid)
            ?.takeIf { snapshot -> snapshot.hasFreshLanProof(gateStartedAtMillis) }
            ?: fallbackSnapshot.takeIf { snapshot -> snapshot.hasFreshLanProof(gateStartedAtMillis) }
    }

    private fun DeviceSnapshot.connectRuntimeIfPossible(
        deviceUid: DeviceUid
    ): Boolean {
        if (!endpoint.hasWebSocketEndpoint) {
            return false
        }

        return devicesRepository.connectRuntime(deviceUid).isSuccess
    }

    private fun DeviceSnapshot.hasFreshLanProof(
        gateStartedAtMillis: Long
    ): Boolean {
        val lastUdpSeenAtMillis = connectionState.lastUdpSeenAtMillis ?: return false
        return lastUdpSeenAtMillis + LAN_PROOF_CLOCK_GRACE_MS >= gateStartedAtMillis
    }

    private companion object {
        const val AUTHENTICATION_TIMEOUT_MS = 12_000L
        const val RUNTIME_PROBE_TIMEOUT_MS = 3_000L
        const val STRICT_LIVE_CHECK_TIMEOUT_MS = 12_000L
        const val LAN_PROOF_CLOCK_GRACE_MS = 1_000L
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

        return response.id == expectedRequestId &&
            response.ok &&
            response.module == AqlWsContract.MODULE_NETWORK &&
            response.action == AqlWsContract.ACTION_NETWORK_STATUS_GET
    }
}

sealed interface DeviceMenuOpenGateResult {
    data class OpenRoute(
        val route: DeviceRoute
    ) : DeviceMenuOpenGateResult

    data class Blocked(
        val title: String,
        @StringRes val messageRes: Int
    ) : DeviceMenuOpenGateResult
}
