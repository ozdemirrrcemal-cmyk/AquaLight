package com.aqua.aqualight.data.devices.light.runtime

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/**
 * Durable last-known telemetry cache for instant first paint.
 *
 * The cache stores values that previously came from the ESP32. It is not used
 * as fresh/authoritative telemetry; it only prevents cold-start blank screens
 * while the live refresh loop confirms the current controller state.
 */
object LightDeviceSnapshotCache {

    private const val PREFS_NAME = "light_device_snapshot_cache"
    private const val KEY_PREFIX = "snapshot_"
    private const val SCHEMA_VERSION = 1

    @Volatile
    private var prefs: SharedPreferences? = null

    fun configure(context: Context) {
        if (prefs != null) {
            return
        }

        prefs = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    fun read(deviceId: Long): LightDeviceLiveState? {
        if (deviceId <= 0L) {
            return null
        }

        val json = prefs
            ?.getString(cacheKey(deviceId), null)
            ?: return null

        return runCatching {
            parseState(
                deviceId = deviceId,
                root = JSONObject(json),
                now = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    fun save(
        deviceId: Long,
        liveState: LightDeviceLiveState
    ) {
        if (deviceId <= 0L || !liveState.hasLiveChannels) {
            return
        }

        val preferences = prefs ?: return
        val capturedAt = liveState.liveDataUpdatedMillis
            .takeIf { millis -> millis > 0L }
            ?: liveState.lastUpdatedMillis
                .takeIf { millis -> millis > 0L }
            ?: System.currentTimeMillis()

        val root = JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("deviceId", deviceId)
            .put("capturedAtMillis", capturedAt)
            .put("deviceTimeUpdatedMillis", liveState.deviceTimeUpdatedMillis)
            .put("deviceTime", liveState.deviceTime?.toJson() ?: JSONObject.NULL)
            .put("channels", JSONArray().also { array ->
                liveState.channels.forEach { channel ->
                    array.put(channel.toJson())
                }
            })
            .put("thermalProtection", liveState.thermalProtection.toJson())
            .put("cooling", liveState.cooling.toJson())

        preferences.edit()
            .putString(cacheKey(deviceId), root.toString())
            .apply()
    }

    fun clear(deviceId: Long) {
        if (deviceId <= 0L) {
            return
        }

        prefs?.edit()
            ?.remove(cacheKey(deviceId))
            ?.apply()
    }

    fun clearAll() {
        prefs?.edit()
            ?.clear()
            ?.apply()
    }

    private fun parseState(
        deviceId: Long,
        root: JSONObject,
        now: Long
    ): LightDeviceLiveState? {
        val channels = root.optJSONArray("channels")
            ?.let(::parseChannels)
            .orEmpty()

        if (channels.isEmpty()) {
            return null
        }

        val capturedAt = root.optLong("capturedAtMillis", 0L)
            .takeIf { millis -> millis > 0L }
            ?: now

        val cachedDeviceTime = root.optJSONObject("deviceTime")
            ?.let { json -> parseDeviceTime(json) }

        val inferredDeviceTime = cachedDeviceTime?.let { time ->
            advanceDeviceTime(
                time = time,
                elapsedMillis = (now - capturedAt).coerceAtLeast(0L)
            )
        }

        return LightDeviceLiveState(
            deviceId = deviceId,
            isRefreshing = false,
            deviceTime = inferredDeviceTime,
            deviceTimeUpdatedMillis = if (inferredDeviceTime != null) now else 0L,
            channels = channels,
            thermalProtection = root.optJSONObject("thermalProtection")
                ?.let(::parseThermalProtection)
                ?: LightThermalProtectionState(),
            cooling = root.optJSONObject("cooling")
                ?.let(::parseCooling)
                ?: LightCoolingState(),
            liveDataUpdatedMillis = capturedAt,
            isLiveDataFresh = false,
            lastUpdatedMillis = capturedAt,
            errorMessage = null,
            dataSource = LightLiveDataSource.CACHE
        )
    }

    private fun parseChannels(array: JSONArray): List<LightDeviceLiveChannelState> {
        val channels = mutableListOf<LightDeviceLiveChannelState>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val semantic = runCatching {
                LightChannelSemantic.valueOf(
                    item.optString("semantic", LightChannelSemantic.UNKNOWN.name)
                )
            }.getOrDefault(LightChannelSemantic.UNKNOWN)

            channels += LightDeviceLiveChannelState(
                semantic = semantic,
                pwmIndex = item.optString("pwmIndex", ""),
                lightIndex = item.optString("lightIndex", ""),
                gpioPwm = item.optString("gpioPwm", ""),
                name = item.optString("name", ""),
                color = item.optLong("color", 0L),
                regime = item.optString("regime", ""),
                vNow = item.optNullableDouble("vNow"),
                maxWatts = item.optNullableDouble("maxWatts"),
                manualValuePercent = item.optNullableInt("manualValuePercent"),
                manualRemainingMillis = item.optLong("manualRemainingMillis", 0L),
                isManualOverrideActive = item.optBoolean("isManualOverrideActive", false),
                hasManualOverrideTelemetry = item.optBoolean("hasManualOverrideTelemetry", false)
            )
        }

        return channels.sortedBy { channel ->
            when (channel.semantic) {
                LightChannelSemantic.RED -> 0
                LightChannelSemantic.GREEN -> 1
                LightChannelSemantic.BLUE -> 2
                LightChannelSemantic.WHITE -> 3
                LightChannelSemantic.UNKNOWN -> 99
            }
        }
    }

    private fun parseDeviceTime(json: JSONObject): LightDeviceTimeState {
        val source = runCatching {
            LightDeviceTimeState.Source.valueOf(
                json.optString("source", LightDeviceTimeState.Source.DEVICE.name)
            )
        }.getOrDefault(LightDeviceTimeState.Source.DEVICE)

        return LightDeviceTimeState(
            year = json.optInt("year", 1970),
            month = json.optInt("month", 1),
            day = json.optInt("day", 1),
            weekDay = json.optInt("weekDay", 1).coerceIn(1, 7),
            hour = json.optInt("hour", 0).coerceIn(0, 23),
            minute = json.optInt("minute", 0).coerceIn(0, 59),
            second = json.optInt("second", 0).coerceIn(0, 59),
            source = source
        )
    }

    private fun parseThermalProtection(json: JSONObject): LightThermalProtectionState {
        return LightThermalProtectionState(
            hasData = json.optBoolean("hasData", false),
            sensorCount = json.optInt("sensorCount", 0),
            currentTemperatureCelsius = json.optNullableDouble("currentTemperatureCelsius"),
            limitTemperatureCelsius = json.optInt("limitTemperatureCelsius", 50),
            lightReductionPercent = json.optInt("lightReductionPercent", 70),
            recoveryIntervalSeconds = json.optInt("recoveryIntervalSeconds", 60),
            currentReductionMultiplier = json.optNullableDouble("currentReductionMultiplier")
        )
    }

    private fun parseCooling(json: JSONObject): LightCoolingState {
        val fansJson = json.optJSONArray("fans") ?: JSONArray()
        val fans = mutableListOf<LightCoolingFanState>()

        for (index in 0 until fansJson.length()) {
            val item = fansJson.optJSONObject(index) ?: continue

            fans += LightCoolingFanState(
                index = item.optInt("index", index),
                enabled = item.optBoolean("enabled", false),
                fanStartTemperatureCelsius = item.optInt("fanStartTemperatureCelsius", 30),
                fanFullSpeedTemperatureCelsius = item.optInt("fanFullSpeedTemperatureCelsius", 50),
                outputPercent = item.optNullableInt("outputPercent"),
                regime = item.optString("regime", ""),
                linkedSensorCount = item.optInt("linkedSensorCount", 0)
            )
        }

        return LightCoolingState(
            hasData = json.optBoolean("hasData", false),
            fans = fans
        )
    }

    private fun advanceDeviceTime(
        time: LightDeviceTimeState,
        elapsedMillis: Long
    ): LightDeviceTimeState {
        val calendar = Calendar.getInstance(Locale.getDefault()).apply {
            set(Calendar.YEAR, time.year)
            set(Calendar.MONTH, (time.month - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, time.day.coerceIn(1, 31))
            set(Calendar.HOUR_OF_DAY, time.hour.coerceIn(0, 23))
            set(Calendar.MINUTE, time.minute.coerceIn(0, 59))
            set(Calendar.SECOND, time.second.coerceIn(0, 59))
            set(Calendar.MILLISECOND, 0)
            add(Calendar.SECOND, (elapsedMillis / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }

        return LightDeviceTimeState(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
            weekDay = appWeekDay(calendar),
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
            second = calendar.get(Calendar.SECOND),
            source = time.source
        )
    }

    private fun appWeekDay(calendar: Calendar): Int {
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SUNDAY) {
            7
        } else {
            day - 1
        }
    }

    private fun LightDeviceTimeState.toJson(): JSONObject {
        return JSONObject()
            .put("year", year)
            .put("month", month)
            .put("day", day)
            .put("weekDay", weekDay)
            .put("hour", hour)
            .put("minute", minute)
            .put("second", second)
            .put("source", source.name)
    }

    private fun LightDeviceLiveChannelState.toJson(): JSONObject {
        return JSONObject()
            .put("semantic", semantic.name)
            .put("pwmIndex", pwmIndex)
            .put("lightIndex", lightIndex)
            .put("gpioPwm", gpioPwm)
            .put("name", name)
            .put("color", color)
            .put("regime", regime)
            .putNullable("vNow", vNow)
            .putNullable("maxWatts", maxWatts)
            .putNullable("manualValuePercent", manualValuePercent)
            .put("manualRemainingMillis", manualRemainingMillis)
            .put("isManualOverrideActive", isManualOverrideActive)
            .put("hasManualOverrideTelemetry", hasManualOverrideTelemetry)
    }

    private fun LightThermalProtectionState.toJson(): JSONObject {
        return JSONObject()
            .put("hasData", hasData)
            .put("sensorCount", sensorCount)
            .putNullable("currentTemperatureCelsius", currentTemperatureCelsius)
            .put("limitTemperatureCelsius", limitTemperatureCelsius)
            .put("lightReductionPercent", lightReductionPercent)
            .put("recoveryIntervalSeconds", recoveryIntervalSeconds)
            .putNullable("currentReductionMultiplier", currentReductionMultiplier)
    }

    private fun LightCoolingState.toJson(): JSONObject {
        return JSONObject()
            .put("hasData", hasData)
            .put("fans", JSONArray().also { array ->
                fans.forEach { fan ->
                    array.put(fan.toJson())
                }
            })
    }

    private fun LightCoolingFanState.toJson(): JSONObject {
        return JSONObject()
            .put("index", index)
            .put("enabled", enabled)
            .put("fanStartTemperatureCelsius", fanStartTemperatureCelsius)
            .put("fanFullSpeedTemperatureCelsius", fanFullSpeedTemperatureCelsius)
            .putNullable("outputPercent", outputPercent)
            .put("regime", regime)
            .put("linkedSensorCount", linkedSensorCount)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) {
            return null
        }

        val value = optDouble(key, Double.NaN)
        return if (value.isNaN()) null else value
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) {
            return null
        }

        return optInt(key)
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        return put(key, value ?: JSONObject.NULL)
    }

    private fun cacheKey(deviceId: Long): String = KEY_PREFIX + deviceId
}
