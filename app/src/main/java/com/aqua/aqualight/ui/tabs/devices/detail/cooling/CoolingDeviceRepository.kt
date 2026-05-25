package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.graphics.Color
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
                    "auto", "0" -> AUTO
                    "on", "1" -> ON
                    "off", "2" -> OFF
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

            val ruleGpio = rule.gpioPwm.trim()

            if (ruleGpio.isBlank() || ruleGpio == "-") {
                return if (fanChannels.size == 1) {
                    fanChannels.first()
                } else {
                    null
                }
            }

            return fanChannels.firstOrNull {
                fan ->
                fan.gpioPwm.trim().equals(
                    ruleGpio,
                    ignoreCase = true
                )
            } ?: if (fanChannels.size == 1) {
                fanChannels.first()
            } else {
                null
            }
        }

        fun usedSensorsFor(
            rule: CoolRuleData?
        ): List<TemperatureSensorData> {
            if (rule == null) {
                return emptyList()
            }

            return sensors.filter {
                sensor ->
                rule.selectedTemperatureFlags.getOrNull(
                    sensor.index
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
            ).use {
                reader ->
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
        .mapNotNull {
            key ->
            val index = key.toIntOrNull() ?: return@mapNotNull null
            val sensorJson = data.optJSONObject(key) ?: return@mapNotNull null

            val name = sensorJson.optString(
                "Name",
                "Sensor ${index + 1}"
            ).ifBlank {
                "Sensor ${index + 1}"
            }

            val temperature = sensorJson.optDouble(
                "Temperature",
                Double.NaN
            ).let {
                value ->
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

            TemperatureSensorData(
                index = index,
                name = name,
                currentTemperature = temperature,
                history = history,
                color = normalizeDeviceColor(
                    rawColor = sensorJson.opt("Color"),
                    fallbackIndex = index
                )
            )
        }
        .sortedBy {
            sensor ->
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
        .mapNotNull {
            key ->
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
                ).ifBlank {
                    "Cooling ${index + 1}"
                },
                gpioPwm = ruleJson.optString(
                    "GPIO_PWM",
                    "-"
                ).trim(),
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
        .sortedBy {
            rule ->
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
        .mapNotNull {
            key ->
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
                ).ifBlank {
                    "Fan ${index + 1}"
                },
                gpioPwm = fanJson.optString(
                    "GPIO_PWM",
                    "-"
                ).trim(),
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
        .sortedBy {
            fan ->
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
        ) {
            index ->
            val value = array.opt(index)

            when (value) {
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

        value.forEach {
            char ->
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

    private fun normalizeDeviceColor(
        rawColor: Any?,
        fallbackIndex: Int
    ): Int {
        val colorValue = when (rawColor) {
            null,
            JSONObject.NULL -> null

            is Number -> rawColor.toLong()

            is String -> parseColorStringToLong(
                value = rawColor
            )

            else -> null
        }

        if (colorValue == null || colorValue == 0L) {
            return defaultSensorColor(
                index = fallbackIndex
            )
        }

        val color = colorValue.toInt()

        return if ((color ushr 24) == 0) {
            0xFF000000.toInt() or (color and 0x00FFFFFF)
        } else {
            color
        }
    }

    private fun parseColorStringToLong(
        value: String
    ): Long? {
        val trimmed = value.trim()

        if (trimmed.isBlank()) {
            return null
        }

        return runCatching {
            when {
                trimmed.startsWith("#") -> {
                    Color.parseColor(trimmed).toLong()
                }

                trimmed.startsWith("0x", ignoreCase = true) -> {
                    trimmed.removePrefix("0x")
                    .removePrefix("0X")
                    .toLong(16)
                }

                trimmed.all {
                    char -> char.isDigit()
                } -> {
                    trimmed.toLong()
                } else -> {
                    trimmed.toLong(16)
                }
            }
        }.getOrNull()
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

    suspend fun saveCoolingFanSettings(
        ipAddress: String,
        currentData: CoolingDashboardData,
        fanIndex: Int,
        ruleIndex: Int?,
        fanName: String,
        fanMode: FanRegime,
        startCooling: Float,
        fullPower: Float,
        minimumPowerPercent: Int,
        maximumPowerPercent: Int,
        selectedSensorIndexes: List<Int>
    ) = withContext(Dispatchers.IO) {
        val fan = currentData.fanChannels.firstOrNull {
            item ->
            item.index == fanIndex
        } ?: throw IllegalStateException(
            "Fan channel could not be found."
        )

        if (fan.gpioPwm.isBlank() || fan.gpioPwm == "-") {
            throw IllegalStateException(
                "Fan channel is not assigned to hardware."
            )
        }

        val cleanFanName = fanName.trim().ifBlank {
            fan.name.ifBlank {
                "Fan ${fan.index + 1}"
            }
        }

        val targetRuleIndex = ruleIndex ?: nextCoolRuleIndex(
            currentData = currentData
        )

        val ruleName = "$cleanFanName Cooling"

        val setJson = JSONObject().apply {
            put(
                "LPWMChanelFan",
                JSONObject().apply {
                    put(
                        "Data",
                        JSONObject().apply {
                            put(
                                fan.index.toString(),
                                JSONObject().apply {
                                    put(
                                        "Name",
                                        cleanFanName
                                    )

                                    put(
                                        "Regime",
                                        fanMode.displayName
                                    )

                                    put(
                                        "VMin",
                                        minimumPowerPercent.coerceIn(
                                            0,
                                            100
                                        ) / 100f
                                    )

                                    put(
                                        "VMax",
                                        maximumPowerPercent.coerceIn(
                                            0,
                                            100
                                        ) / 100f
                                    )
                                }
                            )
                        }
                    )
                }
            )

            put(
                "LCool",
                JSONObject().apply {
                    put(
                        "Data",
                        JSONObject().apply {
                            put(
                                targetRuleIndex.toString(),
                                JSONObject().apply {
                                    put(
                                        "Enabled",
                                        1
                                    )

                                    put(
                                        "Name",
                                        ruleName
                                    )

                                    put(
                                        "GPIO_PWM",
                                        fan.gpioPwm
                                    )

                                    put(
                                        "TMin",
                                        startCooling
                                    )

                                    put(
                                        "TMax",
                                        fullPower
                                    )

                                    put(
                                        "LbT",
                                        buildSensorFlagsJsonArray(
                                            currentData = currentData,
                                            selectedSensorIndexes = selectedSensorIndexes
                                        )
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }

        postSetJson(
            ipAddress = ipAddress,
            json = setJson,
            sRet = "cooling_settings"
        )

        postSetJson(
            ipAddress = ipAddress,
            json = JSONObject().apply {
                put(
                    "Main",
                    JSONObject().apply {
                        put(
                            "SaveCool",
                            1
                        )
                    }
                )
            },
            sRet = "save_cool"
        )
    }

    private fun nextCoolRuleIndex(
        currentData: CoolingDashboardData
    ): Int {
        return currentData.coolRules.maxOfOrNull {
            rule ->
            rule.index
        }?.plus(
            1
        ) ?: 0
    }

    private fun buildSensorFlagsJsonArray(
        currentData: CoolingDashboardData,
        selectedSensorIndexes: List<Int>
    ): JSONArray {
        val selectedSet = selectedSensorIndexes.toSet()

        val maxSensorIndex = currentData.sensors.maxOfOrNull {
            sensor ->
            sensor.index
        } ?: -1

        val maxSelectedIndex = selectedSensorIndexes.maxOrNull() ?: -1

        val maxIndex = maxOf(
            maxSensorIndex,
            maxSelectedIndex
        )

        return JSONArray().apply {
            for (index in 0..maxIndex) {
                put(
                    if (selectedSet.contains(index)) {
                        1
                    } else {
                        0
                    }
                )
            }
        }
    }

    private fun postSetJson(
        ipAddress: String,
        json: JSONObject,
        sRet: String
    ) {
        val encodedJson = URLEncoder.encode(
            json.toString(),
            StandardCharsets.UTF_8.name()
        )

        val encodedRet = URLEncoder.encode(
            sRet,
            StandardCharsets.UTF_8.name()
        )

        val body = "Json=$encodedJson&sRet=$encodedRet"

        val url = URL(
            "http://$ipAddress/set"
        )

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.doOutput = true

        connection.setRequestProperty(
            "Content-Type",
            "text/plain; charset=UTF-8"
        )

        try {
            connection.outputStream.use {
                outputStream ->
                outputStream.write(
                    body.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
            }

            val code = connection.responseCode

            if (code !in 200..299) {
                throw IllegalStateException(
                    "Device returned HTTP $code"
                )
            }

            connection.inputStream.use {
                inputStream ->
                inputStream.readBytes()
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE_41_DIGITS =
        "0123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    }
}