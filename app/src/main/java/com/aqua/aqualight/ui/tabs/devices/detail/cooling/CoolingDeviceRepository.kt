package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

class CoolingDeviceRepository {

    data class TemperatureSensorData(
        val index: Int,
        val name: String,
        val currentTemperature: Float?,
        val history: List<Float?>,
        val color: Int
    )

    data class CoolingTemperatureData(
        val ip: String?,
        val sensors: List<TemperatureSensorData>
    )

    suspend fun fetchTemperatureData(
        ipAddress: String
    ): CoolingTemperatureData = withContext(Dispatchers.IO) {
        val requestJson = JSONObject().apply {
            put(
                "LTemperature",
                JSONObject().apply {
                    put("Count", 0)
                    put(
                        "Data",
                        JSONObject().apply {
                            put(
                                "All",
                                JSONObject().apply {
                                    put("Name", 0)
                                    put("Temperature", 0)
                                    put("LT", 0)
                                    put("Color", 0)
                                }
                            )
                        }
                    )
                }
            )
        }

        val encodedJson = URLEncoder.encode(
            requestJson.toString(),
            StandardCharsets.UTF_8.name()
        )

        val url = URL("http://$ipAddress/get?Json=$encodedJson&sRet=0")

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Device returned HTTP $code")
            }

            val response = BufferedReader(
                InputStreamReader(connection.inputStream)
            ).use { reader ->
                reader.readText()
            }

            parseTemperatureResponse(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTemperatureResponse(
        response: String
    ): CoolingTemperatureData {
        val root = JSONObject(response)
        val ip = root.optString("IP", null)

        val lTemperature = root.optJSONObject("LTemperature")
            ?: return CoolingTemperatureData(
                ip = ip,
                sensors = emptyList()
            )

        val data = lTemperature.optJSONObject("Data")
            ?: return CoolingTemperatureData(
                ip = ip,
                sensors = emptyList()
            )

        val sensors = data.keys()
            .asSequence()
            .mapNotNull { key ->
                val index = key.toIntOrNull() ?: return@mapNotNull null
                val sensorJson = data.optJSONObject(key) ?: return@mapNotNull null

                val name = sensorJson.optString(
                    "Name",
                    "Sensor ${index + 1}"
                )

                val temperature = sensorJson.optDouble(
                    "Temperature",
                    Double.NaN
                ).let { value ->
                    if (value.isNaN() || value < -100.0 || value > 200.0) {
                        null
                    } else {
                        value.toFloat()
                    }
                }

                val lt = sensorJson.optString("LT", "")
                val history = decodeTemperatureHistory(lt)

                val color = when (index % 5) {
                    0 -> 0xFF1E88E5.toInt()
                    1 -> 0xFFE53935.toInt()
                    2 -> 0xFF43A047.toInt()
                    3 -> 0xFFFFB300.toInt()
                    else -> 0xFF8E24AA.toInt()
                }

                CoolingDeviceRepository.TemperatureSensorData(
                    index = index,
                    name = name,
                    currentTemperature = temperature,
                    history = history,
                    color = color
                )
            }
            .sortedBy { it.index }
            .toList()

        return CoolingTemperatureData(
            ip = ip,
            sensors = sensors
        )
    }

    private fun decodeTemperatureHistory(
        encoded: String
    ): List<Float?> {
        if (encoded.length < 3) return emptyList()

        val result = mutableListOf<Float?>()
        var index = 0

        while (index + 2 < encoded.length) {
            val block = encoded.substring(index, index + 3)
            val raw = decodeBase41(block)
            val temperature = (raw - 32768) / 100f

            result.add(
                if (temperature > -100f && temperature < 200f) {
                    ((temperature * 100f).roundToInt() / 100f)
                } else {
                    null
                }
            )

            index += 3
        }

        return result
    }

    private fun decodeBase41(
        value: String
    ): Int {
        var result = 0

        value.forEach { char ->
            val digit = BASE_41_DIGITS.indexOf(char)
            if (digit < 0) return 0
            result = result * 41 + digit
        }

        return result
    }

    companion object {
        private const val BASE_41_DIGITS =
            "0123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    }
}