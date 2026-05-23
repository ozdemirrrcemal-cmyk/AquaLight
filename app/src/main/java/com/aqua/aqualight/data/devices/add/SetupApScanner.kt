package com.aqua.aqualight.data.devices.add

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SetupApScanner {

    suspend fun scan(
        context: Context
    ): List<DeviceAddCandidate> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext

        if (!hasWifiScanPermission(appContext)) {
            return@withContext emptyList()
        }

        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        runCatching {
            wifiManager.startScan()
        }

        val scanResults = runCatching {
            wifiManager.scanResults
        }.getOrDefault(emptyList())

        return@withContext scanResults
            .asSequence()
            .mapNotNull { result ->
                val ssid = result.SSID.orEmpty()

                if (!DeviceSetupSsidParser.isPossibleAquaSetupSsid(ssid)) {
                    return@mapNotNull null
                }

                DeviceSetupSsidParser.parse(ssid)
            }
            .distinctBy { candidate ->
                candidate.setupSsid
            }
            .toList()
    }

    fun hasWifiScanPermission(
        context: Context
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}