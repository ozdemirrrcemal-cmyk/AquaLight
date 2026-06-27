package com.aqua.aqualight.data.devices.discovery.udp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Small coordinator for UDP scan and refresh operations.
 *
 * This class is deliberately UI-agnostic. Repositories/ViewModels can collect [devices] and call
 * [sendForegroundRefreshBurst] when the app enters foreground or the user taps refresh.
 */
class AqlDiscoverySupervisor(
    private val scanner: AqlDiscoveryUdpScanner = AqlDiscoveryUdpScanner(),
    private val refreshSender: AqlDiscoveryRefreshSender = AqlDiscoveryRefreshSender()
) {

    private val _refreshResults = MutableSharedFlow<AqlDiscoveryRefreshSender.SendResult>(
        replay = 0,
        extraBufferCapacity = 8
    )

    val devices: Flow<AqlDiscoveredDevice> = scanner.scan()
    val refreshResults: SharedFlow<AqlDiscoveryRefreshSender.SendResult> = _refreshResults.asSharedFlow()

    fun start(scope: CoroutineScope, onDevice: (AqlDiscoveredDevice) -> Unit): Job =
        scope.launch {
            devices.collect { onDevice(it) }
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
