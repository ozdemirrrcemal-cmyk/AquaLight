package com.aqua.aqualight.lan

import android.content.Context
import com.aqua.aqualight.data.UserPreferencesManager
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

    fun start(context: Context, userPrefs: UserPreferencesManager) {
        if (monitorJob?.isActive == true) return

        val appContext = context.applicationContext

        monitorJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runScanOnce(appContext, userPrefs)

            while (isActive) {
                delay(SCAN_INTERVAL_MS)
                runScanOnce(appContext, userPrefs)
            }
        }
    }

    private suspend fun runScanOnce(
        context: Context,
        userPrefs: UserPreferencesManager
    ) {
        val devices: List<DiscoveredDevice> = try {
            discoverDevices(context, timeoutMs = SCAN_TIMEOUT_MS)
        } catch (_: Exception) {
            emptyList()
        }

        if (devices.isNotEmpty()) {
            userPrefs.updateDevicesLastSeen(devices)
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}