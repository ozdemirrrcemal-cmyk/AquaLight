package com.aqua.aqualight.data.devices.light.runtime

import android.content.Context
import org.json.JSONObject
import kotlin.math.roundToInt

class Esp32LightDeviceCommandManager(
    context: Context,
    private val addressResolver: LightDeviceAddressResolver =
    LightDeviceAddressResolver(context),
    private val httpClient: Esp32HttpJsonClient =
    Esp32HttpJsonClient(),
    private val mappingReader: Esp32LightChannelMappingReader =
    Esp32LightChannelMappingReader(httpClient)
) : LightDeviceCommandManager {

    private val mappingCacheLock = Any()
    private val mappingCache = mutableMapOf<String, LightDeviceChannelMapping>()

    override suspend fun applyManualScene(
        deviceId: Long,
        sceneName: String,
        output: LightRgbwOutput
    ): LightCommandResult {
        return sendManualOutput(
            deviceId = deviceId,
            output = output,
            requestTag = "manual_scene"
        )
    }

    override suspend fun updateManualOutput(
        deviceId: Long,
        output: LightRgbwOutput
    ): LightCommandResult {
        return sendManualOutput(
            deviceId = deviceId,
            output = output,
            requestTag = "manual_output"
        )
    }

    override suspend fun updateManualChannel(
        deviceId: Long,
        semantic: LightChannelSemantic,
        valuePercent: Int
    ): LightCommandResult {
        val address = resolveAddress(deviceId)
        ?: return LightCommandResult.failure("Device address could not be resolved")

        val mapping = getCachedMapping(
            ip = address.ip
        ).getOrElse {
            error ->
            return LightCommandResult.failure(
                error.message ?: "Light channel mapping could not be read"
            )
        }

        val json = buildSingleManualChannelJson(
            mapping = mapping,
            semantic = semantic,
            valuePercent = valuePercent,
            keepManualUntilMs = DEFAULT_MANUAL_TIMEOUT_MS
        ).getOrElse {
            error ->
            return LightCommandResult.failure(
                error.message ?: "Light channel mapping is incomplete"
            )
        }

        return httpClient.postSet(
            ip = address.ip,
            json = json,
            requestTag = "manual_channel_${semantic.name.lowercase()}"
        )
    }

    override suspend fun setManualPower(
        deviceId: Long,
        isPowerOn: Boolean
    ): LightCommandResult {
        val output = if (isPowerOn) {
            val runtime = LightManualRuntimeStore.current(deviceId)

            LightRgbwOutput(
                red = runtime.red,
                green = runtime.green,
                blue = runtime.blue,
                white = runtime.white
            )
        } else {
            LightRgbwOutput(
                red = 0,
                green = 0,
                blue = 0,
                white = 0
            )
        }

        return sendManualOutput(
            deviceId = deviceId,
            output = output,
            requestTag = if (isPowerOn) {
                "manual_power_on"
            } else {
                "manual_power_off"
            }
        )
    }

    override suspend fun resumeAuto(
        deviceId: Long
    ): LightCommandResult {
        val address = resolveAddress(deviceId)
        ?: return LightCommandResult.failure("Device address could not be resolved")

        val mapping = getCachedMapping(
            ip = address.ip,
            forceRefresh = true
        ).getOrElse {
            error ->
            return LightCommandResult.failure(
                error.message ?: "Light channel mapping could not be read"
            )
        }

        val json = buildResumeAutoJson(
            mapping = mapping
        ).getOrElse {
            error ->
            return LightCommandResult.failure(
                error.message ?: "Light channel mapping is incomplete"
            )
        }

        return httpClient.postSet(
            ip = address.ip,
            json = json,
            requestTag = "resume_auto"
        )
    }

    private suspend fun sendManualOutput(
        deviceId: Long,
        output: LightRgbwOutput,
        requestTag: String
    ): LightCommandResult {
        val address = resolveAddress(deviceId)
        ?: return LightCommandResult.failure("Device address could not be resolved")

        val mapping = getCachedMapping(
            ip = address.ip
        ).getOrElse {
            error ->
            return LightCommandResult.failure(
                error.message ?: "Light channel mapping could not be read"
            )
        }

        val json = buildManualOutputJson(
            output = output,
            mapping = mapping,
            keepManualUntilMs = DEFAULT_MANUAL_TIMEOUT_MS
        ).getOrElse {
            error ->
            return LightCommandResult.failure(
                error.message ?: "Light channel mapping is incomplete"
            )
        }

        return httpClient.postSet(
            ip = address.ip,
            json = json,
            requestTag = requestTag
        )
    }

    private suspend fun getCachedMapping(
        ip: String,
        forceRefresh: Boolean = false
    ): Result<LightDeviceChannelMapping> {
        if (!forceRefresh) {
            val cached = synchronized(mappingCacheLock) {
                mappingCache[ip]
            }

            if (cached != null) {
                return Result.success(cached)
            }
        }

        val result = mappingReader.readMapping(
            ip = ip,
            forceRefresh = forceRefresh
        )

        val mapping = result.getOrNull()

        if (mapping != null) {
            synchronized(mappingCacheLock) {
                mappingCache[ip] = mapping
            }
        }

        return result
    }

    private suspend fun resolveAddress(
        deviceId: Long
    ): LightDeviceAddressResolver.Result.Success? {
        return when (
            val result = addressResolver.resolve(
                deviceId = deviceId,
                requireOnline = false
            )
        ) {
            is LightDeviceAddressResolver.Result.Success -> result
            is LightDeviceAddressResolver.Result.Failure -> null
        }
    }

    private fun buildManualOutputJson(
        output: LightRgbwOutput,
        mapping: LightDeviceChannelMapping,
        keepManualUntilMs: Long
    ): Result<String> {
        val safeOutput = output.normalized()

        val data = JSONObject()

        addMappedChannel(
            data = data,
            mapping = mapping,
            semantic = LightChannelSemantic.RED,
            valuePercent = safeOutput.red,
            keepManualUntilMs = keepManualUntilMs
        )

        addMappedChannel(
            data = data,
            mapping = mapping,
            semantic = LightChannelSemantic.GREEN,
            valuePercent = safeOutput.green,
            keepManualUntilMs = keepManualUntilMs
        )

        addMappedChannel(
            data = data,
            mapping = mapping,
            semantic = LightChannelSemantic.BLUE,
            valuePercent = safeOutput.blue,
            keepManualUntilMs = keepManualUntilMs
        )

        addMappedChannel(
            data = data,
            mapping = mapping,
            semantic = LightChannelSemantic.WHITE,
            valuePercent = safeOutput.white,
            keepManualUntilMs = keepManualUntilMs
        )

        if (data.length() == 0) {
            return Result.failure(
                IllegalStateException("No RGBW channel mapping found")
            )
        }

        val json = JSONObject()
        .put(
            "LPWMChanelLED",
            JSONObject()
            .put("Data", data)
            .put("Group", 1)
        )
        .toString()

        return Result.success(json)
    }

    private fun buildSingleManualChannelJson(
        mapping: LightDeviceChannelMapping,
        semantic: LightChannelSemantic,
        valuePercent: Int,
        keepManualUntilMs: Long
    ): Result<String> {
        val pwmIndex = mapping.pwmIndexFor(semantic)
        ?: return Result.failure(
            IllegalStateException("${semantic.name} channel mapping missing")
        )

        val data = JSONObject()
        .put(
            pwmIndex,
            JSONObject()
            .put(
                "VManual",
                JSONObject()
                .put(
                    "V",
                    percentToEsp32Value(valuePercent)
                )
                .put(
                    "TOffMs",
                    keepManualUntilMs
                )
            )
        )

        val json = JSONObject()
        .put(
            "LPWMChanelLED",
            JSONObject()
            .put("Data", data)
            .put("Group", 1)
        )
        .toString()

        return Result.success(json)
    }

    private fun addMappedChannel(
        data: JSONObject,
        mapping: LightDeviceChannelMapping,
        semantic: LightChannelSemantic,
        valuePercent: Int,
        keepManualUntilMs: Long
    ) {
        val pwmIndex = mapping.pwmIndexFor(semantic)
        ?: return

        data.put(
            pwmIndex,
            JSONObject()
            .put(
                "VManual",
                JSONObject()
                .put(
                    "V",
                    percentToEsp32Value(valuePercent)
                )
                .put(
                    "TOffMs",
                    keepManualUntilMs
                )
            )
        )
    }

    private fun buildResumeAutoJson(
        mapping: LightDeviceChannelMapping
    ): Result<String> {
        val mappedEntries = mapping.rgbwEntries()
        .filter {
            entry ->
            entry.gpioPwm.isNotBlank() && entry.gpioPwm != "-"
        }

        if (mappedEntries.isEmpty()) {
            return Result.failure(
                IllegalStateException("No RGBW channel mapping found")
            )
        }

        val json = JSONObject()
        .put(
            "LPWMChanelLED",
            JSONObject()
            .put(
                "Data",
                buildManualClearData(
                    entries = mappedEntries
                )
            )
            .put("Group", 1)
        )
        .put(
            "LLight",
            JSONObject()
            .put("LightEdit", 0)
        )
        .toString()

        return Result.success(json)
    }

    private fun buildManualClearData(
        entries: List<LightDeviceChannelMapping.Entry>
    ): JSONObject {
        val data = JSONObject()

        entries.forEach {
            entry ->
            data.put(
                entry.pwmIndex,
                JSONObject()
                .put(
                    "VManual",
                    JSONObject()
                    .put("V", MANUAL_CLEAR_VALUE)
                    .put("TOffMs", 0)
                )
            )
        }

        return data
    }

    private fun percentToEsp32Value(
        valuePercent: Int
    ): Double {
        val normalized = valuePercent
        .coerceIn(0, 100) / 100.0

        return (normalized * 1000.0)
        .roundToInt() / 1000.0
    }

    companion object {
        private const val DEFAULT_MANUAL_TIMEOUT_MS =
        24L * 60L * 60L * 1000L

        private const val MANUAL_CLEAR_VALUE = -1
    }
}