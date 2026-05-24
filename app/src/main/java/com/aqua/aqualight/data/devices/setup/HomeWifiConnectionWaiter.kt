package com.aqua.aqualight.data.devices.setup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.delay

class HomeWifiConnectionWaiter(
    context: Context
) {

    private val appContext = context.applicationContext

    suspend fun waitUntilHomeWifiReady(
        expectedSsid: String,
        setupSsid: String,
        timeoutMs: Long = 75_000L
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs

        delay(5_000L)

        while (SystemClock.elapsedRealtime() < deadline) {
            if (
                isConnectedToExpectedHomeWifi(
                    expectedSsid = expectedSsid,
                    setupSsid = setupSsid
                )
            ) {
                return true
            }

            delay(1_500L)
        }

        return false
    }

    private fun isConnectedToExpectedHomeWifi(
        expectedSsid: String,
        setupSsid: String
    ): Boolean {
        val connectivityManager = appContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork
            ?: return false

        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return false

        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return false
        }

        val currentSsid = readCurrentSsid(
            capabilities = capabilities
        )

        if (!currentSsid.isNullOrBlank() && currentSsid != UNKNOWN_SSID) {
            return currentSsid == expectedSsid &&
                currentSsid != setupSsid
        }

        val currentIp = readCurrentWifiIp()

        return currentIp.isNotBlank() &&
            currentIp != "0.0.0.0" &&
            !currentIp.startsWith("192.168.4.")
    }

    private fun readCurrentSsid(
        capabilities: NetworkCapabilities
    ): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wifiInfo = capabilities.transportInfo as? WifiInfo

            val ssidFromCapabilities = cleanSsid(
                wifiInfo?.ssid
            )

            if (!ssidFromCapabilities.isNullOrBlank()) {
                return ssidFromCapabilities
            }
        }

        return readSsidFromWifiManager()
    }

    @Suppress("DEPRECATION")
    private fun readSsidFromWifiManager(): String? {
        val wifiManager = appContext.getSystemService(
            Context.WIFI_SERVICE
        ) as WifiManager

        return cleanSsid(
            wifiManager.connectionInfo?.ssid
        )
    }

    @Suppress("DEPRECATION")
    private fun readCurrentWifiIp(): String {
        val wifiManager = appContext.getSystemService(
            Context.WIFI_SERVICE
        ) as WifiManager

        val ip = wifiManager.connectionInfo?.ipAddress ?: return ""

        return listOf(
            ip and 0xFF,
            ip shr 8 and 0xFF,
            ip shr 16 and 0xFF,
            ip shr 24 and 0xFF
        ).joinToString(".")
    }

    private fun cleanSsid(
        ssid: String?
    ): String? {
        return ssid
            ?.replace("\"", "")
            ?.trim()
    }

    private companion object {
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}