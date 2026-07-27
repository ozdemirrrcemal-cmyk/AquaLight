package com.aqua.aqualight.data.devices.discovery.udp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Small coordinator for UDP scan and refresh operations.
 *
 * The receive socket is recreated whenever Android's canonical local Network changes. This prevents
 * a listener that was bound to a lost Wi-Fi route from silently remaining dead after reconnection.
 */
class AqlDiscoverySupervisor(
    private val scanner: AqlDiscoveryUdpScanner = AqlDiscoveryUdpScanner(),
    private val refreshSender: AqlDiscoveryRefreshSender = AqlDiscoveryRefreshSender(),
    initialScanningActive: Boolean = true
) {

    private val scanRequests = MutableSharedFlow<Boolean>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).also { requests ->
        requests.tryEmit(initialScanningActive)
    }

    private val _refreshResults = MutableSharedFlow<AqlDiscoveryRefreshSender.SendResult>(
        replay = 0,
        extraBufferCapacity = 8
    )

    val devices: Flow<AqlDiscoveredDevice> = channelFlow {
        scanRequests.collectLatest { localNetworkAvailable ->
            if (localNetworkAvailable) {
                scanner.scan().collect { device -> send(device) }
            }
        }
    }

    val refreshResults: SharedFlow<AqlDiscoveryRefreshSender.SendResult> =
        _refreshResults.asSharedFlow()

    fun start(scope: CoroutineScope, onDevice: (AqlDiscoveredDevice) -> Unit): Job =
        scope.launch {
            devices.collect { onDevice(it) }
        }

    fun restartScanner(localNetworkAvailable: Boolean) {
        scanRequests.tryEmit(localNetworkAvailable)
    }

    suspend fun sendRefreshNow(): AqlDiscoveryRefreshSender.SendResult {
        val result = refreshSender.sendRefresh()
        _refreshResults.emit(result)
        return result
    }

    suspend fun sendForegroundRefreshBurst(): List<AqlDiscoveryRefreshSender.SendResult> {
        val results = refreshSender.sendForegroundBurst()
        results.forEach { _refreshResults.emit(it) }
        return results
    }
}
