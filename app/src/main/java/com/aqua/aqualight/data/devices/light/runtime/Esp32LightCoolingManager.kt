package com.aqua.aqualight.data.devices.light.runtime

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import kotlin.math.roundToInt

class Esp32LightCoolingManager(
    private val httpClient: Esp32HttpJsonClient = Esp32HttpJsonClient()
) {

    suspend fun read(
        ip: String
    ): Result<LightCoolingState> {
        val queryJson = JSONObject()
        .put(
            "LCool",
            JSONObject()
            .put("Count", 0)
            .put(
                "Data",
                JSONObject()
                .put(
                    "All",
                    JSONObject()
                    .put("Enabled", 0)
                    .put("TMin", 0)
                    .put("TMax", 0)
                    .put("LbT", JSONArray())
                    .put("GPIO_PWM", 0)
                )
            )
        )
        .put(
            "LPWMChanelFan",
            JSONObject()
            .put("Count", 0)
            .put(
                "Data",
                JSONObject()
                .put(
                    "All",
                    JSONObject()
                    .put("VNow", 0)
                    .put("Regime", 0)
                    .put("GPIO_PWM", 0)
                )
            )
        )
        .toString()

        val response = httpClient.getJson(
            ip = ip,
            json = queryJson,
            requestTag = "cooling_read"
        ).getOrElse {
            error ->
            return Result.failure(error)
        }

        return runCatching {
            parseState(response)
        }
    }

    suspend fun setSettingsForAllFans(
        ip: String,
        enabled: Boolean,
        fanStartTemperatureCelsius: Int,
        fanFullSpeedTemperatureCelsius: Int,
        fanCount: Int
    ): LightCommandResult {
        if (fanCount <= 0) {
            return LightCommandResult.failure(
                "Cooling fan is not configured"
            )
        }

        val safeStart =
        fanStartTemperatureCelsius.coerceIn(25, 45)

        val safeFull =
        fanFullSpeedTemperatureCelsius
        .coerceIn(safeStart + 5, 70)

        val data = JSONObject()

        repeat(fanCount) {
            index ->
            data.put(
                index.toString(),
                JSONObject()
                .put(
                    "Enabled",
                    if (enabled) {
                        1
                    } else {
                        0
                    }
                )
                .put("TMin", safeStart)
                .put("TMax", safeFull)
            )
        }

        val json = JSONObject()
        .put(
            "LCool",
            JSONObject()
            .put("Data", data)
        )
        .toString()

        return httpClient.postSet(
            ip = ip,
            json = json,
            requestTag = "cooling_set"
        )
    }

    private fun parseState(
        response: String
    ): LightCoolingState {
        val root = JSONObject(
            normalizeResponseJson(response)
        )

        val coolRoot = root.optJSONObject("LCool")
        val coolData = coolRoot?.optJSONObject("Data") ?: JSONObject()

        val fanPwmData = root
        .optJSONObject("LPWMChanelFan")
        ?.optJSONObject("Data")
        ?: JSONObject()

        val pwmByIndex =
        parseFanPwmData(fanPwmData).associateBy {
            item ->
            item.index
        }

        val pwmByGpio =
        parseFanPwmData(fanPwmData)
        .filter {
            item ->
            item.gpioPwm.isNotBlank() && item.gpioPwm != "-"
        }
        .associateBy {
            item ->
            item.gpioPwm
        }

        val fans = mutableListOf<LightCoolingFanState>()

        coolData.keys().forEach {
            key ->
            if (key == "All") {
                return@forEach
            }

            val index = key.toIntOrNull() ?: return@forEach
            val item = coolData.optJSONObject(key) ?: return@forEach

            val gpioPwm = item.optString(
                "GPIO_PWM",
                ""
            )

            val pwm = if (gpioPwm.isNotBlank() && gpioPwm != "-") {
                pwmByGpio[gpioPwm]
            } else {
                pwmByIndex[index]
            }

            fans += LightCoolingFanState(
                index = index,
                enabled = item.optBooleanCompat("Enabled"),
                fanStartTemperatureCelsius = item
                .optNullableDouble("TMin")
                ?.roundToInt()
                ?.coerceIn(25, 45)
                ?: 30,
                fanFullSpeedTemperatureCelsius = item
                .optNullableDouble("TMax")
                ?.roundToInt()
                ?.coerceIn(35, 70)
                ?: 50,
                outputPercent = pwm?.outputPercent,
                regime = pwm?.regime.orEmpty(),
                linkedSensorCount = item
                .optJSONArray("LbT")
                ?.countEnabledItems()
                ?: 0
            )
        }

        return LightCoolingState(
            hasData = coolRoot != null || fanPwmData.length() > 0,
            fans = fans.sortedBy {
                fan ->
                fan.index
            }
        )
    }

    private fun parseFanPwmData(
        data: JSONObject
    ): List<FanPwmSnapshot> {
        val result = mutableListOf<FanPwmSnapshot>()

        data.keys().forEach {
            key ->
            if (key == "All") {
                return@forEach
            }

            val index = key.toIntOrNull() ?: return@forEach
            val item = data.optJSONObject(key) ?: return@forEach

            result += FanPwmSnapshot(
                index = index,
                gpioPwm = item.optString("GPIO_PWM", ""),
                outputPercent = item
                .optNullableDouble("VNow")
                ?.toPercent(),
                regime = item.optString("Regime", "")
            )
        }

        return result
    }

    private fun Double.toPercent(): Int {
        val value = if (this <= 1.0) {
            this * 100.0
        } else {
            this
        }

        return value
        .roundToInt()
        .coerceIn(0, 100)
    }

    private fun JSONArray.countEnabledItems(): Int {
        var count = 0

        for (index in 0 until length()) {
            val enabled = when (val value = opt(index)) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value == "1" || value.equals(
                    "true",
                    ignoreCase = true
                )
                else -> false
            }

            if (enabled) {
                count++
            }
        }

        return count
    }

    private fun JSONObject.optBooleanCompat(
        key: String
    ): Boolean {
        if (!has(key) || isNull(key)) {
            return false
        }

        return when (val value = opt(key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value == "1" || value.equals(
                "true",
                ignoreCase = true
            )
            else -> false
        }
    }

    private fun JSONObject.optNullableDouble(
        key: String
    ): Double? {
        if (!has(key) || isNull(key)) {
            return null
        }

        val value = optDouble(
            key,
            Double.NaN
        )

        return if (value.isNaN()) {
            null
        } else {
            value
        }
    }

    private fun normalizeResponseJson(
        response: String
    ): String {
        val trimmed = response.trim()

        if (trimmed.startsWith("{")) {
            return trimmed
        }

        if (trimmed.startsWith("Json=")) {
            val jsonStart = "Json=".length
            val jsonEnd = trimmed.indexOf("&sRet=")

            val rawJson = if (jsonEnd >= 0) {
                trimmed.substring(jsonStart, jsonEnd)
            } else {
                trimmed.substring(jsonStart)
            }

            return URLDecoder.decode(
                rawJson,
                Charsets.UTF_8.name()
            )
        }

        return trimmed
    }

    private data class FanPwmSnapshot(
        val index: Int,
        val gpioPwm: String,
        val outputPercent: Int?,
        val regime: String
    )
}