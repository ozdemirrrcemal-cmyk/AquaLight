package com.aqua.aqualight.data.devices.light.runtime

import android.content.Context
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramTimeMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class Esp32LightProgramCommandManager(
    context: Context,
    private val addressResolver: LightDeviceAddressResolver =
        LightDeviceAddressResolver(context),
    private val httpClient: Esp32HttpJsonClient =
        Esp32HttpJsonClient(),
    private val mappingReader: Esp32LightChannelMappingReader =
        Esp32LightChannelMappingReader(httpClient)
) {

    suspend fun loadPrograms(
        deviceId: Long,
        programs: List<SavedLightProgram>
    ): LightCommandResult {
        if (deviceId <= 0L) {
            return LightCommandResult.failure("Device information is missing")
        }

        val activePrograms = programs
            .filter { program ->
                program.deviceId == deviceId && program.isActive
            }
            .sortedBy { program ->
                program.draft.start.totalMinutes
            }

        if (activePrograms.isEmpty()) {
            return LightCommandResult.failure("No active light program to load")
        }

        val address = resolveAddress(deviceId)
            ?: return LightCommandResult.failure("Device address could not be resolved")

        val mapping = mappingReader.readMapping(
            ip = address.ip,
            forceRefresh = true
        ).getOrElse { error ->
            return LightCommandResult.failure(
                error.message ?: "Light channel mapping could not be read"
            )
        }

        val json = buildScheduleJson(
            programs = activePrograms,
            mapping = mapping
        ).getOrElse { error ->
            return LightCommandResult.failure(
                error.message ?: "Light program could not be prepared"
            )
        }

        return httpClient.postSet(
            ip = address.ip,
            json = json,
            requestTag = "light_program_load"
        )
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

    private fun buildScheduleJson(
        programs: List<SavedLightProgram>,
        mapping: LightDeviceChannelMapping
    ): Result<String> {
        val mappedEntries = mapping.rgbwEntries()
            .filter { entry ->
                entry.gpioPwm.isNotBlank() && entry.gpioPwm != "-"
            }

        if (mappedEntries.isEmpty()) {
            return Result.failure(
                IllegalStateException("No RGBW channel mapping found")
            )
        }

        val lightData = JSONObject()

        mappedEntries.forEach { entry ->
            lightData.put(
                entry.lightIndex,
                JSONObject()
                    .put("GPIO_PWM", entry.gpioPwm)
                    .put(
                        "LP",
                        buildLightPointsForChannel(
                            programs = programs,
                            semantic = entry.semantic
                        )
                    )
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
                    .put("ChanelEdit", "AllCh")
                    .put("Count", mappedEntries.size)
                    .put("Data", lightData)
            )
            .toString()

        return Result.success(json)
    }

    private fun buildLightPointsForChannel(
        programs: List<SavedLightProgram>,
        semantic: LightChannelSemantic
    ): JSONArray {
        val points = mutableListOf<SchedulePoint>()

        programs
            .sortedBy { program ->
                program.draft.start.totalMinutes
            }
            .forEach { program ->
                val draft = program.draft

                val peakValue = percentToEsp32Value(
                    valuePercent = when (semantic) {
                        LightChannelSemantic.RED -> draft.channelValues.red
                        LightChannelSemantic.GREEN -> draft.channelValues.green
                        LightChannelSemantic.BLUE -> draft.channelValues.blue
                        LightChannelSemantic.WHITE -> draft.channelValues.white
                        LightChannelSemantic.UNKNOWN -> 0
                    }
                )

                addOrReplacePoint(
                    points = points,
                    minute = draft.start.totalMinutes,
                    label = draft.start.label,
                    value = 0.0
                )

                addOrReplacePoint(
                    points = points,
                    minute = draft.peakStart.totalMinutes,
                    label = draft.peakStart.label,
                    value = peakValue
                )

                addOrReplacePoint(
                    points = points,
                    minute = draft.peakEnd.totalMinutes,
                    label = draft.peakEnd.label,
                    value = peakValue
                )

                addOrReplacePoint(
                    points = points,
                    minute = LightProgramTimeMath.endMinutes(draft.end),
                    label = LightProgramTimeMath.endLabel(draft.end),
                    value = 0.0
                )
            }

        val array = JSONArray()

        points
            .sortedBy { item ->
                item.minute
            }
            .forEach { item ->
                array.put(
                    JSONArray()
                        .put(item.label)
                        .put(item.value)
                )
            }

        return array
    }

    private fun addOrReplacePoint(
        points: MutableList<SchedulePoint>,
        minute: Int,
        label: String,
        value: Double
    ) {
        val existingIndex = points.indexOfFirst { item ->
            item.minute == minute
        }

        val point = SchedulePoint(
            minute = minute,
            label = label,
            value = value
        )

        if (existingIndex >= 0) {
            points[existingIndex] = point
        } else {
            points += point
        }
    }

    private fun buildManualClearData(
        entries: List<LightDeviceChannelMapping.Entry>
    ): JSONObject {
        val data = JSONObject()

        entries.forEach { entry ->
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

    private data class SchedulePoint(
        val minute: Int,
        val label: String,
        val value: Double
    )

    private fun percentToEsp32Value(
        valuePercent: Int
    ): Double {
        val normalized = valuePercent
            .coerceIn(0, 100) / 100.0

        return (normalized * 1000.0)
            .roundToInt() / 1000.0
    }

    companion object {
        private const val MANUAL_CLEAR_VALUE = -1
    }
}