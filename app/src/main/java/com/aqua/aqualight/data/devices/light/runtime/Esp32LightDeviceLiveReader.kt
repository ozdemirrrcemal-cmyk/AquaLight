package com.aqua.aqualight.data.devices.light.runtime

import org.json.JSONObject
import java.net.URLDecoder
import kotlin.math.roundToInt

class Esp32LightDeviceLiveReader(
    private val httpClient: Esp32HttpJsonClient = Esp32HttpJsonClient(),
    private val timeReader: Esp32LightDeviceTimeReader =
        Esp32LightDeviceTimeReader(httpClient),
    private val mappingReader: Esp32LightChannelMappingReader =
        Esp32LightChannelMappingReader(httpClient),
    private val thermalProtectionManager: Esp32LightThermalProtectionManager =
        Esp32LightThermalProtectionManager(httpClient),
    private val coolingManager: Esp32LightCoolingManager =
        Esp32LightCoolingManager(httpClient)
) {

    suspend fun read(
        ip: String
    ): Result<Esp32LightDeviceLiveSnapshot> {
        val timeResult = timeReader.readTime(
            ip = ip
        )

        val channelsResult = readChannels(
            ip = ip
        )

        val thermalResult = thermalProtectionManager.read(
            ip = ip
        )

        val coolingResult = coolingManager.read(
            ip = ip
        )

        if (
            timeResult.isFailure &&
            channelsResult.isFailure &&
            thermalResult.isFailure &&
            coolingResult.isFailure
        ) {
            return Result.failure(
                channelsResult.exceptionOrNull()
                    ?: timeResult.exceptionOrNull()
                    ?: thermalResult.exceptionOrNull()
                    ?: coolingResult.exceptionOrNull()
                    ?: IllegalStateException("Live device data could not be read")
            )
        }

        val partialError = when {
            timeResult.isFailure -> {
                timeResult.exceptionOrNull()?.message
            }

            channelsResult.isFailure -> {
                channelsResult.exceptionOrNull()?.message
            }

            thermalResult.isFailure -> {
                thermalResult.exceptionOrNull()?.message
            }

            coolingResult.isFailure -> {
                coolingResult.exceptionOrNull()?.message
            }

            else -> {
                null
            }
        }

        return Result.success(
            Esp32LightDeviceLiveSnapshot(
                deviceTime = timeResult.getOrNull(),
                channels = channelsResult.getOrElse {
                    emptyList()
                },
                thermalProtection = thermalResult.getOrNull(),
                cooling = coolingResult.getOrNull(),
                partialErrorMessage = partialError
            )
        )
    }

    private suspend fun readChannels(
        ip: String
    ): Result<List<LightDeviceLiveChannelState>> {
        val mapping = mappingReader.readMapping(
            ip = ip
        ).getOrElse { error ->
            return Result.failure(error)
        }

        val queryJson = JSONObject()
            .put(
                "LPWMChanelLED",
                JSONObject()
                    .put("Count", 0)
                    .put(
                        "Data",
                        JSONObject()
                            .put(
                                "All",
                                JSONObject()
                                    .put("VNow", 0)
                                    .put(
                                        "VManual",
                                        JSONObject()
                                            .put("V", 0)
                                            .put("TOffMs", 0)
                                            .put("Active", 0)
                                    )
                                    .put("Regime", 0)
                                    .put("Name", 0)
                                    .put("Color", 0)
                                    .put("GPIO_PWM", 0)
                                    .put("Gr", 0)
                                    .put("W", 0)
                            )
                    )
            )
            .toString()

        val response = httpClient.getJson(
            ip = ip,
            json = queryJson,
            requestTag = "light_live_channels"
        ).getOrElse { error ->
            return Result.failure(error)
        }

        return runCatching {
            parseChannels(
                response = response,
                mapping = mapping
            )
        }
    }

    private fun parseChannels(
        response: String,
        mapping: LightDeviceChannelMapping
    ): List<LightDeviceLiveChannelState> {
        val root = JSONObject(
            normalizeResponseJson(response)
        )

        val pwmData = root
            .optJSONObject("LPWMChanelLED")
            ?.optJSONObject("Data")
            ?: JSONObject()

        val entriesByPwmIndex = mapping.entries.associateBy { entry ->
            entry.pwmIndex
        }

        val channels = mutableListOf<LightDeviceLiveChannelState>()

        pwmData.keys().forEach { pwmIndex ->
            val item = pwmData.optJSONObject(pwmIndex) ?: return@forEach
            val entry = entriesByPwmIndex[pwmIndex] ?: return@forEach

            val manualJson = item.optJSONObject("VManual")
            val manualValuePercent = manualJson
                ?.optNullableDouble("V")
                ?.let { value ->
                    normalizeManualPercent(value)
                }
            val manualRemainingMillis = manualJson
                ?.optNullableLong("TOffMs")
                ?: 0L
            val isManualOverrideActive = manualJson
                ?.optNullableBoolean("Active")
                ?: (
                    manualValuePercent != null &&
                        manualRemainingMillis > 0L
                    )

            channels += LightDeviceLiveChannelState(
                semantic = entry.semantic,
                pwmIndex = entry.pwmIndex,
                lightIndex = entry.lightIndex,
                gpioPwm = item.optString(
                    "GPIO_PWM",
                    entry.gpioPwm
                ),
                name = item.optString(
                    "Name",
                    entry.pwmName
                ),
                color = item.optLong(
                    "Color",
                    entry.pwmColor
                ),
                regime = item.optString(
                    "Regime",
                    ""
                ),
                vNow = item.optNullableDouble("VNow"),
                maxWatts = item.optNullableDouble("W"),
                manualValuePercent = manualValuePercent,
                manualRemainingMillis = manualRemainingMillis,
                isManualOverrideActive = isManualOverrideActive,
                hasManualOverrideTelemetry = manualJson != null
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

    private fun normalizeManualPercent(
        value: Double
    ): Int? {
        if (value < 0.0) {
            return null
        }

        val percent = if (value <= 1.0) {
            value * 100.0
        } else {
            value
        }

        return percent
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun JSONObject.optNullableLong(
        key: String
    ): Long? {
        if (!has(key) || isNull(key)) {
            return null
        }

        return optLong(
            key,
            0L
        )
    }

    private fun JSONObject.optNullableBoolean(
        key: String
    ): Boolean? {
        if (!has(key) || isNull(key)) {
            return null
        }

        return when (val value = opt(key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> {
                when (value.trim().lowercase()) {
                    "1", "true", "yes", "on", "active" -> true
                    "0", "false", "no", "off", "inactive" -> false
                    else -> null
                }
            }
            else -> null
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

data class Esp32LightDeviceLiveSnapshot(
    val deviceTime: LightDeviceTimeState?,
    val channels: List<LightDeviceLiveChannelState>,
    val thermalProtection: LightThermalProtectionState?,
    val cooling: LightCoolingState?,
    val partialErrorMessage: String?
)