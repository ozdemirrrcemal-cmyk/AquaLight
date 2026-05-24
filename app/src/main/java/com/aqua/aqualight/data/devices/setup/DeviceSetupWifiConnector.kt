package com.aqua.aqualight.data.devices.setup

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DeviceSetupWifiConnector(
    context: Context
) {

    private val appContext = context.applicationContext

    data class SetupConnection(
        val network: Network,
        private val connectivityManager: ConnectivityManager,
        private val callback: ConnectivityManager.NetworkCallback
    ) {
        fun close() {
            runCatching {
                connectivityManager.bindProcessToNetwork(null)
            }

            runCatching {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }
    }

    suspend fun connectToSetupNetwork(
        ssid: String,
        password: String,
        timeoutMs: Long = 30_000L
    ): SetupConnection {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            error("Device setup requires Android 10 or newer.")
        }

        val connectivityManager = appContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val specifierBuilder = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)

        if (password.isNotBlank()) {
            specifierBuilder.setWpa2Passphrase(password)
        }

        val wifiSpecifier = specifierBuilder.build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(wifiSpecifier)
            .build()

        return suspendCancellableCoroutine { continuation ->
            var resumed = false

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(
                    network: Network
                ) {
                    if (resumed) {
                        return
                    }

                    resumed = true

                    runCatching {
                        connectivityManager.bindProcessToNetwork(network)
                    }

                    continuation.resume(
                        SetupConnection(
                            network = network,
                            connectivityManager = connectivityManager,
                            callback = this
                        )
                    )
                }

                override fun onUnavailable() {
                    if (resumed) {
                        return
                    }

                    resumed = true

                    continuation.resumeWithException(
                        IllegalStateException(
                            "Setup network connection was not approved or timed out."
                        )
                    )
                }

                override fun onLost(
                    network: Network
                ) {
                    // Kurulum bitince cihaz setup AP kapatabilir. Bu normal.
                }
            }

            connectivityManager.requestNetwork(
                request,
                callback,
                timeoutMs.toInt()
            )

            continuation.invokeOnCancellation {
                runCatching {
                    connectivityManager.bindProcessToNetwork(null)
                }

                runCatching {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            }
        }
    }
}