package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

    enum class FanRegime(
        val displayName: String
    ) {
        AUTO("Auto"),
        ON("On"),
        OFF("Off");

        companion object {
            fun fromRaw(
                value: String
            ): FanRegime {
                return when (value.trim().lowercase()) {
                    "auto" -> AUTO
                    "on" -> ON
                    "off" -> OFF
                    else -> OFF
                }
            }
        }
    }

    data class FanChannelData(
        val index: Int,
        val name: String,
        val gpioPwm: String,
        val regime: FanRegime,
        val vMin: Float,
        val vMax: Float,
        val vNow: Float?,
        val invert: Boolean
    )

    data class CoolRuleData(
        val index: Int,
        val enabled: Boolean,
        val name: String,
        val gpioPwm: String,
        val selectedTemperatureFlags: List<Boolean>,
        val tMin: Float,
        val tMax: Float
    )

    data class CoolingDashboardData(
        val ip: String?,
        val sensors: List<TemperatureSensorData>,
        val coolRules: List<CoolRuleData>,
        val fanChannels: List<FanChannelData>
    ) {
        fun primaryCoolRule(): CoolRuleData? {
            return coolRules.firstOrNull()
        }

        fun attachedFanFor(
            rule: CoolRuleData?
        ): FanChannelData? {
            if (rule == null) {
                return null
            }

            return fanChannels.firstOrNull { fan ->
                fan.gpioPwm == rule.gpioPwm
            }
        }

        fun usedSensorsFor(
            rule: CoolRuleData?
        ): List<TemperatureSensorData> {
            if (rule == null) {
                return emptyList()
            }

            return sensors.filterIndexed { index, _ ->
                rule.selectedTemperatureFlags.getOrNull(
                    index
                ) == true
            }
        }
    }

    suspend fun fetchCoolingDashboardData(
        ipAddress: String
    ): CoolingDashboardData = withContext(Dispatchers.IO) {
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

            put(
                "LCool",
                JSONObject().apply {
                    put("Count", 0)
                    put(
                        "Data",
                        JSONObject().apply {
                            put(
                                "All",
                                JSONObject().apply {
                                    put("Enabled", 0)
                                    put("Name", 0)
                                    put("GPIO_PWM", 0)
                                    put("LbT", 0)
                                    put("TMin", 0)
                                    put("TMax", 0)
                                }
                            )
                        }
                    )
                }
            )

            put(
                "LPWMChanelFan",
                JSONObject().apply {
                    put("Count", 0)
                    put(
                        "Data",
                        JSONObject().apply {
                            put(
                                "All",
                                JSONObject().apply {
                                    put("Regime", 0)
                                    put("Name", 0)
                                    put("GPIO_PWM", 0)
                                    put("VMin", 0)
                                    put("VMax", 0)
                                    put("VNow", 0)
                                    put("Invert", 0)
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

        val url = URL(
            "http://$ipAddress/get?Json=$encodedJson&sRet=0"
        )

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        try {
            val code = connection.responseCode

            if (code !in 200..299) {
                throw IllegalStateException(
                    "Device returned HTTP $code"
                )
            }

            val response = BufferedReader(
                InputStreamReader(connection.inputStream)
            ).use { reader ->
                reader.readText()
            }

            parseCoolingDashboardResponse(
                response = response
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCoolingDashboardResponse(
        response: String
    ): CoolingDashboardData {
        val root = JSONObject(response)
        val ip = root.optString("IP", null)

        return CoolingDashboardData(
            ip = ip,
            sensors = parseTemperatureSensors(
                root = root
            ),
            coolRules = parseCoolRules(
                root = root
            ),
            fanChannels = parseFanChannels(
                root = root
            )
        )
    }

    private fun parseTemperatureSensors(
        root: JSONObject
    ): List<TemperatureSensorData> {
        val data = root.optJSONObject("LTemperature")
            ?.optJSONObject("Data")
            ?: return emptyList()

        return data.keys()
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

                val history = decodeTemperatureHistory(
                    encoded = sensorJson.optString(
                        "LT",
                        ""
                    )
                )

                val colorFromDevice = sensorJson.optLong(
                    "Color",
                    0L
                ).toInt()

                TemperatureSensorData(
                    index = index,
                    name = name,
                    currentTemperature = temperature,
                    history = history,
                    color = if (colorFromDevice != 0) {
                        colorFromDevice
                    } else {
                        defaultSensorColor(
                            index = index
                        )
                    }
                )
            }
            .sortedBy { sensor ->
                sensor.index
            }
            .toList()
    }

    private fun parseCoolRules(
        root: JSONObject
    ): List<CoolRuleData> {
        val data = root.optJSONObject("LCool")
            ?.optJSONObject("Data")
            ?: return emptyList()

        return data.keys()
            .asSequence()
            .mapNotNull { key ->
                val index = key.toIntOrNull() ?: return@mapNotNull null
                val ruleJson = data.optJSONObject(key) ?: return@mapNotNull null

                CoolRuleData(
                    index = index,
                    enabled = ruleJson.optBooleanCompat(
                        name = "Enabled"
                    ),
                    name = ruleJson.optString(
                        "Name",
                        "Cooling ${index + 1}"
                    ),
                    gpioPwm = ruleJson.optString(
                        "GPIO_PWM",
                        "-"
                    ),
                    selectedTemperatureFlags = parseBooleanArray(
                        array = ruleJson.optJSONArray("LbT")
                    ),
                    tMin = ruleJson.optFloatCompat(
                        name = "TMin",
                        defaultValue = 0f
                    ),
                    tMax = ruleJson.optFloatCompat(
                        name = "TMax",
                        defaultValue = 0f
                    )
                )
            }
            .sortedBy { rule ->
                rule.index
            }
            .toList()
    }

    private fun parseFanChannels(
        root: JSONObject
    ): List<FanChannelData> {
        val data = root.optJSONObject("LPWMChanelFan")
            ?.optJSONObject("Data")
            ?: return emptyList()

        return data.keys()
            .asSequence()
            .mapNotNull { key ->
                val index = key.toIntOrNull() ?: return@mapNotNull null
                val fanJson = data.optJSONObject(key) ?: return@mapNotNull null

                val rawVNow = fanJson.optDouble(
                    "VNow",
                    Double.NaN
                )

                FanChannelData(
                    index = index,
                    name = fanJson.optString(
                        "Name",
                        "Fan ${index + 1}"
                    ),
                    gpioPwm = fanJson.optString(
                        "GPIO_PWM",
                        "-"
                    ),
                    regime = FanRegime.fromRaw(
                        value = fanJson.optString(
                            "Regime",
                            "Off"
                        )
                    ),
                    vMin = fanJson.optFloatCompat(
                        name = "VMin",
                        defaultValue = 0f
                    ),
                    vMax = fanJson.optFloatCompat(
                        name = "VMax",
                        defaultValue = 1f
                    ),
                    vNow = if (rawVNow.isNaN() || rawVNow < 0.0) {
                        null
                    } else {
                        rawVNow.toFloat()
                    },
                    invert = fanJson.optBooleanCompat(
                        name = "Invert"
                    )
                )
            }
            .sortedBy { fan ->
                fan.index
            }
            .toList()
    }

    private fun parseBooleanArray(
        array: JSONArray?
    ): List<Boolean> {
        if (array == null) {
            return emptyList()
        }

        return List(
            size = array.length()
        ) { index ->
            val value = array.opt(index)

            when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value == "1" || value.equals(
                    "true",
                    ignoreCase = true
                )
                else -> false
            }
        }
    }

    private fun decodeTemperatureHistory(
        encoded: String
    ): List<Float?> {
        if (encoded.length < 3) {
            return emptyList()
        }

        val result = mutableListOf<Float?>()
        var index = 0

        while (index + 2 < encoded.length) {
            val block = encoded.substring(
                index,
                index + 3
            )

            val raw = decodeBase41(
                value = block
            )

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
            val digit = BASE_41_DIGITS.indexOf(
                char
            )

            if (digit < 0) {
                return 0
            }

            result = result * 41 + digit
        }

        return result
    }

    private fun JSONObject.optFloatCompat(
        name: String,
        defaultValue: Float
    ): Float {
        val value = opt(name)

        return when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    private fun JSONObject.optBooleanCompat(
        name: String,
        defaultValue: Boolean = false
    ): Boolean {
        val value = opt(name)

        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value == "1" ||
                value.equals(
                    "true",
                    ignoreCase = true
                ) ||
                value.equals(
                    "on",
                    ignoreCase = true
                )
            else -> defaultValue
        }
    }

    private fun defaultSensorColor(
        index: Int
    ): Int {
        return when (index % 5) {
            0 -> 0xFF1E88E5.toInt()
            1 -> 0xFFE53935.toInt()
            2 -> 0xFF43A047.toInt()
            3 -> 0xFFFFB300.toInt()
            else -> 0xFF8E24AA.toInt()
        }
    }

    companion object {
        private const val BASE_41_DIGITS =
            "0123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    }
}