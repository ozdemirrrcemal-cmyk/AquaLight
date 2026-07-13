package com.aqua.aqualight.ui.tabs.devices.route

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
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
        val connectionStates = devicesRepository.runtimeConnectionStates()
            ?: return@coroutineScope null
        val authenticationSignal = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(STRICT_LIVE_CHECK_TIMEOUT_MS) {
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
            return@coroutineScope null
        }

        sendRuntimeProbe(deviceUid)

        val authenticated = authenticationSignal.await()
            ?: return@coroutineScope null

        return@coroutineScope devicesRepository.currentDevice(deviceUid)
            ?: fallbackSnapshot.takeIf {
                authenticated is AqlWsConnectionState.Authenticated
            }
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

    private fun sendRuntimeProbe(
        deviceUid: DeviceUid
    ) {
        devicesRepository.commandClient(deviceUid)?.ping()
    }

    private fun DeviceSnapshot.hasFreshLanProof(
        gateStartedAtMillis: Long
    ): Boolean {
        val lastUdpSeenAtMillis = connectionState.lastUdpSeenAtMillis ?: return false
        return lastUdpSeenAtMillis + LAN_PROOF_CLOCK_GRACE_MS >= gateStartedAtMillis
    }

    private companion object {
        const val STRICT_LIVE_CHECK_TIMEOUT_MS = 5_000L
        const val LAN_PROOF_CLOCK_GRACE_MS = 1_000L
    }
}

internal object DeviceMenuAuthenticationPolicy {

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

sealed interface DeviceMenuOpenGateResult {
    data class OpenRoute(
        val route: DeviceRoute
    ) : DeviceMenuOpenGateResult

    data class Blocked(
        val title: String,
        @StringRes val messageRes: Int
    ) : DeviceMenuOpenGateResult
}
