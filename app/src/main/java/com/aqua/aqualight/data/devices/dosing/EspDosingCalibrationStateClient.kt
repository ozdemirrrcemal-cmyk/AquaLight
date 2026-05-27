package com.aqua.aqualight.data.devices.dosing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class EspDosingChannelCalibrationState(
    val channelIndex: Int,
    val ye: Long,
    val dimension: String,
    val calibratedOnDevice: Boolean
)

object EspDosingCalibrationStateClient {

    suspend fun readChannelCalibrationState(
        deviceIp: String,
        channelIndex: Int
    ): EspDosingChannelCalibrationState? {
        val safeDeviceIp =
            deviceIp.trim()

        val safeChannelIndex =
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )

        if (safeDeviceIp.isBlank()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val requestJson =
                    """{"LPWMChanelTimer":{"Data":{"$safeChannelIndex":{"YE":0,"Dimension":0}}}}"""

                val encodedJson =
                    URLEncoder.encode(
                        requestJson,
                        "UTF-8"
                    )

                val encodedRet =
                    URLEncoder.encode(
                        "LPWMChanelTimer",
                        "UTF-8"
                    )

                val url =
                    URL(
                        "http://$safeDeviceIp/get?Json=$encodedJson&sRet=$encodedRet"
                    )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    2500

                connection.readTimeout =
                    2500

                connection.useCaches =
                    false

                val responseCode =
                    connection.responseCode

                if (responseCode !in 200..299) {
                    connection.disconnect()
                    return@runCatching null
                }

                val responseBody =
                    connection.inputStream
                        .bufferedReader()
                        .use { reader ->
                            reader.readText()
                        }

                connection.disconnect()

                val root =
                    JSONObject(responseBody)

                val channelJson =
                    root.optJSONObject("LPWMChanelTimer")
                        ?.optJSONObject("Data")
                        ?.optJSONObject(safeChannelIndex.toString())
                        ?: return@runCatching null

                val ye =
                    channelJson.optLong(
                        "YE",
                        -1L
                    )

                val dimension =
                    channelJson.optString(
                        "Dimension",
                        ""
                    )

                EspDosingChannelCalibrationState(
                    channelIndex = safeChannelIndex,
                    ye = ye,
                    dimension = dimension,
                    calibratedOnDevice = ye >= 1L
                )
            }.getOrNull()
        }
    }
}