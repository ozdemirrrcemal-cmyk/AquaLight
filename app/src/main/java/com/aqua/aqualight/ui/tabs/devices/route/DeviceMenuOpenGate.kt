package com.aqua.aqualight.ui.tabs.devices.route

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.ui.common.devicepresence.DevicePresencePresentationMapper
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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
                title = DEFAULT_DEVICE_TITLE,
                message = DEFAULT_OFFLINE_MESSAGE
            )
        }

        val deviceUid = DeviceUid(requestedDeviceUid)
        val initialSnapshot = devicesRepository.currentDevice(deviceUid)
            ?: return DeviceMenuOpenGateResult.Blocked(
                title = DEFAULT_DEVICE_TITLE,
                message = DEFAULT_OFFLINE_MESSAGE
            )

        devicesRepository.refreshVisibleDevices()
        runCatching {
            devicesRepository.refreshNow()
        }

        val refreshedSnapshot = devicesRepository.currentDevice(deviceUid) ?: initialSnapshot
        if (refreshedSnapshot.isReachable()) {
            refreshedSnapshot.connectRuntimeIfPossible(deviceUid)
            return DeviceMenuOpenGateResult.OpenRoute(
                route = routeResolver.resolve(
                    snapshot = refreshedSnapshot,
                    requestedDeviceUid = requestedDeviceUid
                )
            )
        }

        refreshedSnapshot.connectRuntimeIfPossible(deviceUid)

        val reachableSnapshot = waitForReachableSnapshot(
            deviceUid = deviceUid,
            fallbackSnapshot = refreshedSnapshot
        )

        val finalSnapshot = reachableSnapshot
            ?: devicesRepository.currentDevice(deviceUid)
            ?: refreshedSnapshot

        if (reachableSnapshot != null || finalSnapshot.isReachable()) {
            return DeviceMenuOpenGateResult.OpenRoute(
                route = routeResolver.resolve(
                    snapshot = finalSnapshot,
                    requestedDeviceUid = requestedDeviceUid
                )
            )
        }

        return DeviceMenuOpenGateResult.Blocked(
            title = finalSnapshot.title.ifBlank { DEFAULT_DEVICE_TITLE },
            message = DEFAULT_OFFLINE_MESSAGE
        )
    }

    private suspend fun waitForReachableSnapshot(
        deviceUid: DeviceUid,
        fallbackSnapshot: DeviceSnapshot
    ): DeviceSnapshot? {
        val registryReachableFlow = devicesRepository
            .observeDevice(deviceUid)
            .filterNotNull()
            .filter { snapshot -> snapshot.isReachable() }

        val runtimeReachableFlow = devicesRepository
            .runtimeEvents()
            ?.filter { event -> event.isReachableSignalFor(deviceUid) }
            ?.map { devicesRepository.currentDevice(deviceUid) ?: fallbackSnapshot }
            ?: emptyFlow()

        return withTimeoutOrNull(REACHABILITY_CHECK_TIMEOUT_MS) {
            merge(
                registryReachableFlow,
                runtimeReachableFlow
            ).first()
        }
    }

    private fun DeviceSnapshot.connectRuntimeIfPossible(
        deviceUid: DeviceUid
    ) {
        if (endpoint.hasWebSocketEndpoint) {
            devicesRepository.connectRuntime(deviceUid)
        }
    }

    private fun DeviceSnapshot.isReachable(): Boolean {
        return DevicePresencePresentationMapper.isReachable(
            connectionState.onlineState
        )
    }

    private fun AqlWsEvent.isReachableSignalFor(
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
        const val REACHABILITY_CHECK_TIMEOUT_MS = 2_500L
        const val DEFAULT_DEVICE_TITLE = "Device"
        const val DEFAULT_OFFLINE_MESSAGE = "This device is offline right now. Make sure it is powered on and connected to the same Wi-Fi network."
    }
}

sealed interface DeviceMenuOpenGateResult {
    data class OpenRoute(
        val route: DeviceRoute
    ) : DeviceMenuOpenGateResult

    data class Blocked(
        val title: String,
        val message: String
    ) : DeviceMenuOpenGateResult
}
