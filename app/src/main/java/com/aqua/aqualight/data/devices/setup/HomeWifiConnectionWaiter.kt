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
        timeoutMs: Long = 60_000L
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs

        delay(3_000L)

        while (SystemClock.elapsedRealtime() < deadline) {
            if (isHomeWifiReady(expectedSsid)) {
                return true
            }

            delay(1_500L)
        }

        return false
    }

    private fun isHomeWifiReady(
        expectedSsid: String
    ): Boolean {
        val connectivityManager = appContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val wifiNetworkReady = connectivityManager.allNetworks.any { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return@any false

            val isWifi = capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            )

            if (!isWifi) {
                return@any false
            }

            val ssid = readSsidFromCapabilities(capabilities)
                ?: readSsidFromWifiManager()

            if (ssid.isNullOrBlank() || ssid == UNKNOWN_SSID) {
                return@any true
            }

            ssid == expectedSsid
        }

        return wifiNetworkReady
    }

    private fun readSsidFromCapabilities(
        capabilities: NetworkCapabilities
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null
        }

        val wifiInfo = capabilities.transportInfo as? WifiInfo
            ?: return null

        return cleanSsid(wifiInfo.ssid)
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