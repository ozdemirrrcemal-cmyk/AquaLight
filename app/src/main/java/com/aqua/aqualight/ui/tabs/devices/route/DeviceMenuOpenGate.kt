package com.aqua.aqualight.ui.tabs.devices.route

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
                fallbackSnapshot = fallbackSnapshot
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
        fallbackSnapshot: DeviceSnapshot
    ): DeviceSnapshot? = coroutineScope {
        val liveSignal = async {
            waitForLiveRuntimeSnapshot(
                deviceUid = deviceUid,
                fallbackSnapshot = fallbackSnapshot
            )
        }

        val connectStarted = fallbackSnapshot.connectRuntimeIfPossible(deviceUid)
        if (!connectStarted) {
            liveSignal.cancel()
            return@coroutineScope null
        }

        sendRuntimeProbe(deviceUid)
        liveSignal.await()
    }

    private suspend fun waitForLiveRuntimeSnapshot(
        deviceUid: DeviceUid,
        fallbackSnapshot: DeviceSnapshot
    ): DeviceSnapshot? {
        val runtimeLiveFlow = devicesRepository
            .runtimeEvents()
            ?.filter { event -> event.isLiveRuntimeSignalFor(deviceUid) }
            ?.map { devicesRepository.currentDevice(deviceUid) ?: fallbackSnapshot }
            ?: emptyFlow()

        return withTimeoutOrNull(STRICT_LIVE_CHECK_TIMEOUT_MS) {
            runtimeLiveFlow.first()
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

    private fun AqlWsEvent.isLiveRuntimeSignalFor(
        deviceUid: DeviceUid
    ): Boolean {
        if (this.deviceUid != deviceUid) {
            return false
        }

        return when (this) {
            is AqlWsEvent.Opened,
            is AqlWsEvent.Message -> true
            is AqlWsEvent.Closed,
            is AqlWsEvent.Failure -> false
        }
    }

    private companion object {
        const val STRICT_LIVE_CHECK_TIMEOUT_MS = 2_500L
        const val LAN_PROOF_CLOCK_GRACE_MS = 1_000L
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
