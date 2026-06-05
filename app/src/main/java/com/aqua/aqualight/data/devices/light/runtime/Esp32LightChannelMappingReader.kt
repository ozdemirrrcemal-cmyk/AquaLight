package com.aqua.aqualight.data.devices.light.runtime

import org.json.JSONObject
import java.net.URLDecoder

class Esp32LightChannelMappingReader(
    private val httpClient: Esp32HttpJsonClient = Esp32HttpJsonClient()
) {

    private val cache = mutableMapOf<String, CachedMapping>()

    suspend fun readMapping(
        ip: String,
        forceRefresh: Boolean = false
    ): Result<LightDeviceChannelMapping> {
        val now = System.currentTimeMillis()

        val cached = cache[ip]

        if (
            !forceRefresh &&
            cached != null &&
            now - cached.createdAtMillis <= CACHE_VALID_MS
        ) {
            return Result.success(cached.mapping)
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
                                    .put("Regime", 0)
                                    .put("Name", 0)
                                    .put("Color", 0)
                                    .put("GPIO_PWM", 0)
                                    .put("Gr", 0)
                                    .put("W", 0)
                            )
                    )
            )
            .put(
                "LLight",
                JSONObject()
                    .put("LightEdit", 0)
                    .put("ChanelEdit", "AllCh")
                    .put("Count", 0)
                    .put(
                        "Data",
                        JSONObject()
                            .put(
                                "All",
                                JSONObject()
                                    .put("GPIO_PWM", 0)
                                    .put("LP", 0)
                            )
                    )
            )
            .toString()

        val response = httpClient.getJson(
            ip = ip,
            json = queryJson,
            requestTag = "light_channel_mapping"
        ).getOrElse { error ->
            return Result.failure(error)
        }

        val mapping = runCatching {
            parseMapping(response)
        }.getOrElse { error ->
            return Result.failure(error)
        }

        if (!mapping.hasAnyMappedChannel()) {
            return Result.failure(
                IllegalStateException("Light channel mapping missing")
            )
        }

        cache[ip] = CachedMapping(
            mapping = mapping,
            createdAtMillis = now
        )

        return Result.success(mapping)
    }

    private fun parseMapping(
        response: String
    ): LightDeviceChannelMapping {
        val root = JSONObject(
            normalizeResponseJson(response)
        )

        val pwmData = root
            .optJSONObject("LPWMChanelLED")
            ?.optJSONObject("Data")
            ?: JSONObject()

        val lightData = root
            .optJSONObject("LLight")
            ?.optJSONObject("Data")
            ?: JSONObject()

        val lightIndexByGpio = mutableMapOf<String, String>()

        lightData.keys().forEach { lightIndex ->
            val item = lightData.optJSONObject(lightIndex) ?: return@forEach
            val gpioPwm = item.optString("GPIO_PWM", "")

            if (gpioPwm.isNotBlank() && gpioPwm != "-") {
                lightIndexByGpio[gpioPwm] = lightIndex
            }
        }

        val entries = mutableListOf<LightDeviceChannelMapping.Entry>()

        pwmData.keys().forEach { pwmIndex ->
            val item = pwmData.optJSONObject(pwmIndex) ?: return@forEach

            val name = item.optString("Name", "")
            val color = item.optLong("Color", 0L)
            val gpioPwm = item.optString("GPIO_PWM", "")

            val semantic = detectSemantic(
                name = name,
                color = color
            )

            if (semantic == LightChannelSemantic.UNKNOWN) {
                return@forEach
            }

            entries += LightDeviceChannelMapping.Entry(
                pwmIndex = pwmIndex,
                lightIndex = lightIndexByGpio[gpioPwm] ?: pwmIndex,
                gpioPwm = gpioPwm,
                pwmName = name,
                pwmColor = color,
                semantic = semantic
            )
        }

        return LightDeviceChannelMapping(
            entries = entries
        )
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

    private fun detectSemantic(
        name: String,
        color: Long
    ): LightChannelSemantic {
        val normalizedName = name.lowercase().trim()

        return when {
            normalizedName.contains("red") ||
                normalizedName.contains("kırmızı") ||
                normalizedName == "r" -> {
                LightChannelSemantic.RED
            }

            normalizedName.contains("green") ||
                normalizedName.contains("yeşil") ||
                normalizedName == "g" -> {
                LightChannelSemantic.GREEN
            }

            normalizedName.contains("blue") ||
                normalizedName.contains("mavi") ||
                normalizedName == "b" -> {
                LightChannelSemantic.BLUE
            }

            normalizedName.contains("white") ||
                normalizedName.contains("beyaz") ||
                normalizedName == "w" -> {
                LightChannelSemantic.WHITE
            }

            isRedLike(color) -> LightChannelSemantic.RED
            isGreenLike(color) -> LightChannelSemantic.GREEN
            isBlueLike(color) -> LightChannelSemantic.BLUE
            isWhiteLike(color) -> LightChannelSemantic.WHITE

            else -> LightChannelSemantic.UNKNOWN
        }
    }

    private fun isRedLike(
        color: Long
    ): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        return r > 180 && g < 100 && b < 100
    }

    private fun isGreenLike(
        color: Long
    ): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        return g > 160 && r < 130 && b < 130
    }

    private fun isBlueLike(
        color: Long
    ): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        return b > 160 && r < 130 && g < 160
    }

    private fun isWhiteLike(
        color: Long
    ): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        return r > 180 && g > 180 && b > 180
    }

    private data class CachedMapping(
        val mapping: LightDeviceChannelMapping,
        val createdAtMillis: Long
    )

    companion object {
        private const val CACHE_VALID_MS = 60_000L
    }
}