package com.aqua.aqualight.data.devices.light.runtime

import kotlin.math.roundToInt
import org.json.JSONObject
import java.net.URLDecoder

class Esp32LightThermalProtectionManager(
    private val httpClient: Esp32HttpJsonClient = Esp32HttpJsonClient()
) {

    suspend fun read(
        ip: String
    ): Result<LightThermalProtectionState> {
        val queryJson = JSONObject()
            .put(
                "LTemperature",
                JSONObject()
                    .put("Count", 0)
                    .put(
                        "Data",
                        JSONObject()
                            .put(
                                "All",
                                JSONObject()
                                    .put("TempLightErr", 0)
                                    .put("Temperature", 0)
                            )
                    )
            )
            .put(
                "LLight",
                JSONObject()
                    .put("LightDownErr", 0)
                    .put("TimeDownErr", 0)
                    .put("kLightErr", 0)
            )
            .toString()

        val response = httpClient.getJson(
            ip = ip,
            json = queryJson,
            requestTag = "thermal_protection_read"
        ).getOrElse { error ->
            return Result.failure(error)
        }

        return runCatching {
            parseState(response)
        }
    }

    suspend fun setSettings(
        ip: String,
        limitTemperatureCelsius: Int,
        lightReductionPercent: Int,
        recoveryIntervalSeconds: Int,
        sensorCount: Int
    ): LightCommandResult {
        if (sensorCount <= 0) {
            return LightCommandResult.failure(
                "Temperature sensor is not configured"
            )
        }

        val safeLimit =
            limitTemperatureCelsius.coerceIn(40, 75)

        val safeReduction =
            lightReductionPercent.coerceIn(40, 90)

        val safeRecovery =
            recoveryIntervalSeconds.coerceIn(15, 300)

        val resolvedSensorCount =
            sensorCount.coerceAtLeast(1)

        val temperatureData = JSONObject()

        repeat(resolvedSensorCount) { index ->
            temperatureData.put(
                index.toString(),
                JSONObject()
                    .put("TempLightErr", safeLimit)
            )
        }

        val settingsJson = JSONObject()
            .put(
                "LTemperature",
                JSONObject()
                    .put("Count", resolvedSensorCount)
                    .put("Data", temperatureData)
            )
            .put(
                "LLight",
                JSONObject()
                    .put("LightDownErr", safeReduction)
                    .put("TimeDownErr", safeRecovery)
            )
            .toString()

        val settingsResult = httpClient.postSet(
            ip = ip,
            json = settingsJson,
            requestTag = "thermal_protection_set"
        )

        if (!settingsResult.isSuccess) {
            return settingsResult
        }

        return httpClient.postSet(
            ip = ip,
            json = buildSaveJson(),
            requestTag = "thermal_protection_save"
        )
    }

    private fun buildSaveJson(): String {
        return JSONObject()
            .put(
                "Main",
                JSONObject()
                    .put("SaveCool", 1)
                    .put("SaveLight", 1)
            )
            .toString()
    }

    private fun parseState(
        response: String
    ): LightThermalProtectionState {
        val root = JSONObject(
            normalizeResponseJson(response)
        )

        val temperatureRoot =
            root.optJSONObject("LTemperature")

        val temperatureData =
            temperatureRoot?.optJSONObject("Data")

        val declaredSensorCount =
            temperatureRoot?.optInt("Count", 0) ?: 0

        val sensorCount = maxOf(
            declaredSensorCount,
            countNumericObjects(temperatureData)
        )

        val firstTemperatureObject =
            firstObjectFromData(temperatureData)

        val currentTemperature =
            firstTemperatureObject?.optNullableDouble("Temperature")

        val limitTemperature =
            firstTemperatureObject
                ?.optNullableDouble("TempLightErr")
                ?.roundToInt()
                ?: 50

        val lightRoot =
            root.optJSONObject("LLight")

        val lightReduction =
            lightRoot
                ?.optNullableDouble("LightDownErr")
                ?.roundToInt()
                ?: 70

        val recoveryInterval =
            lightRoot
                ?.optNullableDouble("TimeDownErr")
                ?.roundToInt()
                ?: 60

        val currentReductionMultiplier =
            lightRoot?.optNullableDouble("kLightErr")

        return LightThermalProtectionState(
            hasData = temperatureRoot != null || lightRoot != null,
            sensorCount = sensorCount,
            currentTemperatureCelsius = currentTemperature,
            limitTemperatureCelsius = limitTemperature.coerceIn(40, 75),
            lightReductionPercent = lightReduction.coerceIn(40, 90),
            recoveryIntervalSeconds = recoveryInterval.coerceIn(15, 300),
            currentReductionMultiplier = currentReductionMultiplier
        )
    }

    private fun countNumericObjects(
        data: JSONObject?
    ): Int {
        if (data == null) {
            return 0
        }

        var count = 0
        val keys = data.keys()

        while (keys.hasNext()) {
            val key = keys.next()

            if (key == "All") {
                continue
            }

            if (
                key.toIntOrNull() != null &&
                data.optJSONObject(key) != null
            ) {
                count++
            }
        }

        return count
    }

    private fun firstObjectFromData(
        data: JSONObject?
    ): JSONObject? {
        if (data == null) {
            return null
        }

        val keys = data.keys()

        while (keys.hasNext()) {
            val key = keys.next()

            if (key == "All") {
                continue
            }

            val item = data.optJSONObject(key)

            if (item != null) {
                return item
            }
        }

        return null
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
}