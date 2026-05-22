package com.aqua.aqualight.lan

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.ui.tabs.devices.DiscoveredDevice
import com.aqua.aqualight.ui.tabs.devices.discoverDevices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object LanMonitor {

    private const val SCAN_INTERVAL_MS = 10_000L
    private const val SCAN_TIMEOUT_MS = 1_500L

    private var monitorJob: Job? = null

    fun start(
        context: Context
    ) {
        if (monitorJob?.isActive == true) {
            return
        }

        val appContext = context.applicationContext
        val devicesStore = DevicesDataStoreManager.create(appContext)

        monitorJob = CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        ).launch {
            runScanOnce(
                context = appContext,
                devicesStore = devicesStore
            )

            while (isActive) {
                delay(SCAN_INTERVAL_MS)

                runScanOnce(
                    context = appContext,
                    devicesStore = devicesStore
                )
            }
        }
    }

    private suspend fun runScanOnce(
        context: Context,
        devicesStore: DevicesDataStoreManager
    ) {
        val discoveredDevices: List<DiscoveredDevice> = try {
            discoverDevices(
                context = context,
                timeoutMs = SCAN_TIMEOUT_MS
            )
        } catch (exception: Exception) {
            exception.printStackTrace()
            emptyList()
        }

        if (discoveredDevices.isEmpty()) {
            return
        }

        val updates = discoveredDevices.map { device ->
            DevicesDataStoreManager.DeviceLastSeenUpdate(
                id = device.id,
                ip = device.ip,
                firmwareBuild = device.firmwareBuild.orEmpty()
            )
        }

        devicesStore.updateDevicesLastSeen(
            discovered = updates
        )
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}