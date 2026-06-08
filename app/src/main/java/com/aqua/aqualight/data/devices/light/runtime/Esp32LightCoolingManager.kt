package com.aqua.aqualight.data.devices.light.runtime

import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

class Esp32LightCoolingManager(
    private val httpClient: Esp32HttpJsonClient = Esp32HttpJsonClient()
) {

    suspend fun read(
        ip: String
    ): Result<LightCoolingState> {
        val response = httpClient.getJson(
            ip = ip,
            json = buildCoolingReadJson(),
            requestTag = "cooling_read"
        ).getOrElse { error ->
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
            fanFullSpeedTemperatureCelsius.coerceIn(
                safeStart + 5,
                70
            )

        val root = readCoolingRoot(
            ip = ip
        ).getOrElse { error ->
            return LightCommandResult.failure(
                error.message ?: "Cooling configuration could not be read"
            )
        }

        val coolData = root
            .optJSONObject("LCool")
            ?.optJSONObject("Data")
            ?: JSONObject()

        val fanPwmData = root
            .optJSONObject("LPWMChanelFan")
            ?.optJSONObject("Data")
            ?: JSONObject()

        val fanPwmSnapshots = parseFanPwmData(
            data = fanPwmData
        ).filter { item ->
            isValidGpioPwm(item.gpioPwm)
        }.sortedBy { item ->
            item.index
        }

        val fanPwmByIndex = fanPwmSnapshots.associateBy { item ->
            item.index
        }

        val fanPwmByGpio = fanPwmSnapshots.associateBy { item ->
            item.gpioPwm
        }

        val targetIndices = buildTargetFanIndices(
            coolData = coolData,
            fanPwmSnapshots = fanPwmSnapshots,
            fallbackFanCount = fanCount
        )

        if (targetIndices.isEmpty()) {
            return LightCommandResult.failure(
                "Cooling fan is not configured"
            )
        }

        val fallbackLinkedSensors =
            firstLinkedSensorArray(coolData)
                ?: defaultLinkedSensorArray()

        val coolSettingsData = JSONObject()
        val fanRegimeData = JSONObject()

        targetIndices.forEach { index ->
            val key = index.toString()

            val currentCoolItem = coolData.optJSONObject(key)

            val currentLinkedSensors = currentCoolItem
                ?.optJSONArray("LbT")
                ?.takeIf { array ->
                    array.countEnabledItems() > 0
                }
                ?: fallbackLinkedSensors

            val currentCoolGpio = currentCoolItem
                ?.optString("GPIO_PWM", "")
                .orEmpty()

            val fanPwmGpio = fanPwmByIndex[index]
                ?.gpioPwm
                .orEmpty()

            val resolvedGpioPwm = when {
                isValidGpioPwm(fanPwmGpio) -> fanPwmGpio
                isValidGpioPwm(currentCoolGpio) -> currentCoolGpio
                else -> "-"
            }

            coolSettingsData.put(
                key,
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
                    .put("LbT", copyJsonArray(currentLinkedSensors))
                    .put("GPIO_PWM", resolvedGpioPwm)
            )

            val targetPwm = fanPwmByIndex[index]
                ?: fanPwmByGpio[resolvedGpioPwm]

            if (targetPwm != null) {
                fanRegimeData.put(
                    targetPwm.index.toString(),
                    JSONObject()
                        .put(
                            "Regime",
                            if (enabled) {
                                "Auto"
                            } else {
                                "Off"
                            }
                        )
                )
            }
        }

        if (enabled && fanRegimeData.length() <= 0) {
            return LightCommandResult.failure(
                "Cooling PWM channel is not configured"
            )
        }

        val settingsJson = buildCoolingSettingsJson(
            coolSettingsData = coolSettingsData,
            fanRegimeData = fanRegimeData,
            targetIndices = targetIndices
        )

        val settingsResult = httpClient.postSet(
            ip = ip,
            json = settingsJson,
            requestTag = "cooling_set"
        )

        if (!settingsResult.isSuccess) {
            return settingsResult
        }

        return httpClient.postSet(
            ip = ip,
            json = buildSaveJson(),
            requestTag = "cooling_save"
        )
    }

    private suspend fun readCoolingRoot(
        ip: String
    ): Result<JSONObject> {
        val response = httpClient.getJson(
            ip = ip,
            json = buildCoolingReadJson(),
            requestTag = "cooling_read_before_set"
        ).getOrElse { error ->
            return Result.failure(error)
        }

        return runCatching {
            JSONObject(
                normalizeResponseJson(response)
            )
        }
    }

    private fun buildCoolingReadJson(): String {
        return JSONObject()
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
    }

    private fun buildCoolingSettingsJson(
        coolSettingsData: JSONObject,
        fanRegimeData: JSONObject,
        targetIndices: List<Int>
    ): String {
        val count = (targetIndices.maxOrNull() ?: -1) + 1

        val root = JSONObject()
            .put(
                "LCool",
                JSONObject()
                    .put("Count", count.coerceAtLeast(0))
                    .put("Data", coolSettingsData)
            )

        if (fanRegimeData.length() > 0) {
            root.put(
                "LPWMChanelFan",
                JSONObject()
                    .put("Data", fanRegimeData)
            )
        }

        return root.toString()
    }

    private fun buildSaveJson(): String {
        return JSONObject()
            .put(
                "Main",
                JSONObject()
                    .put("SaveCool", 1)
            )
            .toString()
    }

    private fun buildTargetFanIndices(
        coolData: JSONObject,
        fanPwmSnapshots: List<FanPwmSnapshot>,
        fallbackFanCount: Int
    ): List<Int> {
        val indices = mutableSetOf<Int>()

        numericKeys(coolData).forEach { index ->
            indices += index
        }

        fanPwmSnapshots.forEach { snapshot ->
            indices += snapshot.index
        }

        repeat(fallbackFanCount.coerceAtLeast(0)) { index ->
            indices += index
        }

        return indices
            .filter { index ->
                index >= 0
            }
            .sorted()
    }

    private fun numericKeys(
        data: JSONObject
    ): List<Int> {
        val result = mutableListOf<Int>()
        val keys = data.keys()

        while (keys.hasNext()) {
            val key = keys.next()

            if (key == "All") {
                continue
            }

            val index = key.toIntOrNull()
                ?: continue

            if (index >= 0) {
                result += index
            }
        }

        return result.sorted()
    }

    private fun firstLinkedSensorArray(
        coolData: JSONObject
    ): JSONArray? {
        val keys = coolData.keys()

        while (keys.hasNext()) {
            val key = keys.next()

            if (key == "All") {
                continue
            }

            val array = coolData
                .optJSONObject(key)
                ?.optJSONArray("LbT")

            if (
                array != null &&
                array.countEnabledItems() > 0
            ) {
                return array
            }
        }

        return null
    }

    private fun defaultLinkedSensorArray(): JSONArray {
        return JSONArray()
            .put(1)
    }

    private fun copyJsonArray(
        source: JSONArray
    ): JSONArray {
        val copy = JSONArray()

        for (index in 0 until source.length()) {
            copy.put(
                source.opt(index)
            )
        }

        return copy
    }

    private fun isValidGpioPwm(
        value: String?
    ): Boolean {
        val cleanValue = value
            .orEmpty()
            .trim()

        return cleanValue.isNotBlank() && cleanValue != "-"
    }

    private fun parseState(
        response: String
    ): LightCoolingState {
        val root = JSONObject(
            normalizeResponseJson(response)
        )

        val coolRoot = root.optJSONObject("LCool")
        val coolData = coolRoot?.optJSONObject("Data") ?: JSONObject()

        val fanPwmRoot = root.optJSONObject("LPWMChanelFan")
        val fanPwmData = fanPwmRoot?.optJSONObject("Data") ?: JSONObject()

        val fanPwmSnapshots = parseFanPwmData(
            data = fanPwmData
        )

        val pwmByIndex =
            fanPwmSnapshots.associateBy { item ->
                item.index
            }

        val pwmByGpio =
            fanPwmSnapshots
                .filter { item ->
                    item.gpioPwm.isNotBlank() && item.gpioPwm != "-"
                }
                .associateBy { item ->
                    item.gpioPwm
                }

        val indices = buildTargetFanIndices(
            coolData = coolData,
            fanPwmSnapshots = fanPwmSnapshots,
            fallbackFanCount = 0
        )

        val fans = mutableListOf<LightCoolingFanState>()

        indices.forEach { index ->
            val key = index.toString()
            val item = coolData.optJSONObject(key)

            val gpioPwm = item?.optString(
                "GPIO_PWM",
                ""
            ).orEmpty()

            val pwm = if (gpioPwm.isNotBlank() && gpioPwm != "-") {
                pwmByGpio[gpioPwm]
            } else {
                pwmByIndex[index]
            }

            val configuredEnabled =
                item?.optBooleanCompat("Enabled") ?: false

            val isAutoRegime =
                pwm?.regime.equals(
                    "Auto",
                    ignoreCase = true
                )

            val resolvedEnabled = if (pwm != null) {
                configuredEnabled && isAutoRegime
            } else {
                configuredEnabled
            }

            val startTemperature = item
                ?.optNullableDouble("TMin")
                ?.roundToInt()
                ?.coerceIn(25, 45)
                ?: 30

            val fullSpeedTemperature = item
                ?.optNullableDouble("TMax")
                ?.roundToInt()
                ?.coerceIn(35, 70)
                ?.coerceAtLeast(startTemperature + 5)
                ?.coerceAtMost(70)
                ?: 50

            fans += LightCoolingFanState(
                index = index,
                enabled = resolvedEnabled,
                fanStartTemperatureCelsius = startTemperature,
                fanFullSpeedTemperatureCelsius = fullSpeedTemperature,
                outputPercent = pwm?.outputPercent,
                regime = pwm?.regime.orEmpty(),
                linkedSensorCount = item
                    ?.optJSONArray("LbT")
                    ?.countEnabledItems()
                    ?: 0
            )
        }

        return LightCoolingState(
            hasData = coolRoot != null || fanPwmRoot != null,
            fans = fans.sortedBy { fan ->
                fan.index
            }
        )
    }

    private fun parseFanPwmData(
        data: JSONObject
    ): List<FanPwmSnapshot> {
        val result = mutableListOf<FanPwmSnapshot>()
        val keys = data.keys()

        while (keys.hasNext()) {
            val key = keys.next()

            if (key == "All") {
                continue
            }

            val index = key.toIntOrNull()
                ?: continue

            val item = data.optJSONObject(key)
                ?: continue

            result += FanPwmSnapshot(
                index = index,
                gpioPwm = item.optString("GPIO_PWM", ""),
                outputPercent = item
                    .optNullableDouble("VNow")
                    ?.toPercent(),
                regime = item.optString("Regime", "")
            )
        }

        return result.sortedBy { item ->
            item.index
        }
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