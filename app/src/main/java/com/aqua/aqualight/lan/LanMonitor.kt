package com.aqua.aqualight.lan

import android.content.Context
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.ui.tabs.devices.DiscoveredDevice
import com.aqua.aqualight.ui.tabs.devices.discoverDevices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object LanMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    /**
     * Uygulama açıldığında bir kez çağır:
     * Lan monitör cihazları periyodik olarak tarayıp DataStore’a yazacak.
     */
    fun start(context: Context, userPrefs: UserPreferencesManager) {
        if (started) return
        started = true

        scope.launch {
            // İlk açılışta hemen bir kez tara
            runScanOnce(context, userPrefs)

            while (isActive) {
                delay(10_000L) // 10 sn’de bir tara
                runScanOnce(context, userPrefs)
            }
        }
    }

    private suspend fun runScanOnce(context: Context, userPrefs: UserPreferencesManager) {
        val devices: List<DiscoveredDevice> = try {
            discoverDevices(context, timeoutMs = 1500L)
        } catch (_: Exception) {
            emptyList()
        }

        if (devices.isNotEmpty()) {
            userPrefs.updateDevicesLastSeen(devices)
        }
        // Hiç cihaz bulunmasa bile DataStore’u elleme → lastSeenMillis olduğu gibi kalsın
    }

    fun stop() {
        scope.cancel()
    }
}