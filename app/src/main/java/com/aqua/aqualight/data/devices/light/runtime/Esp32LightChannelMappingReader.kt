package com.aqua.aqualight.data.devices.light.runtime

import org.json.JSONObject

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
                JSONObject().put("All", 0)
            )
            .put(
                "LLight",
                JSONObject().put("All", 0)
            )
            .toString()

        val response = httpClient.getJson(
            ip = ip,
            json = queryJson,
            requestTag = "light_channel_mapping"
        ).getOrElse { error ->
            return Result.failure(error)
        }

        val mapping = parseMapping(response)

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
        val root = JSONObject(response)

        val pwmData = root
            .optJSONObject("LPWMChanelLED")
            ?.optJSONObject("Data")
            ?: JSONObject()

        val lightData = root
            .optJSONObject("LLight")
            ?.optJSONObject("Data")
            ?: JSONObject()

        val pwmByGpio = mutableMapOf<String, PwmChannelInfo>()

        pwmData.keys().forEach { key ->
            val item = pwmData.optJSONObject(key) ?: return@forEach
            val gpioPwm = item.optString("GPIO_PWM", "")

            if (gpioPwm.isNotBlank() && gpioPwm != "-") {
                pwmByGpio[gpioPwm] = PwmChannelInfo(
                    gpioPwm = gpioPwm,
                    name = item.optString("Name", ""),
                    color = item.optLong("Color", 0L)
                )
            }
        }

        val entries = mutableListOf<LightDeviceChannelMapping.Entry>()

        lightData.keys().forEach { lightIndex ->
            val lightItem = lightData.optJSONObject(lightIndex) ?: return@forEach
            val gpioPwm = lightItem.optString("GPIO_PWM", "")

            if (gpioPwm.isBlank() || gpioPwm == "-") {
                return@forEach
            }

            val pwmInfo = pwmByGpio[gpioPwm] ?: return@forEach

            entries += LightDeviceChannelMapping.Entry(
                lightIndex = lightIndex,
                gpioPwm = gpioPwm,
                pwmName = pwmInfo.name,
                pwmColor = pwmInfo.color,
                semantic = detectSemantic(
                    name = pwmInfo.name,
                    color = pwmInfo.color
                )
            )
        }

        return LightDeviceChannelMapping(
            entries = entries
        )
    }

    private fun detectSemantic(
        name: String,
        color: Long
    ): LightChannelSemantic {
        val normalizedName = name.lowercase()

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

    private data class PwmChannelInfo(
        val gpioPwm: String,
        val name: String,
        val color: Long
    )

    private data class CachedMapping(
        val mapping: LightDeviceChannelMapping,
        val createdAtMillis: Long
    )

    companion object {
        private const val CACHE_VALID_MS = 60_000L
    }
}