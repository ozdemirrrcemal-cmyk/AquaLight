package com.aqua.aqualight.data.devices.light.runtime

import android.content.Context
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramPhaseType
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramTimelineBuilder
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramTimelinePhase
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
            return clearPrograms(
                deviceId = deviceId
            )
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

    suspend fun clearPrograms(
        deviceId: Long
    ): LightCommandResult {
        if (deviceId <= 0L) {
            return LightCommandResult.failure("Device information is missing")
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

        val json = buildClearScheduleJson(
            mapping = mapping
        ).getOrElse { error ->
            return LightCommandResult.failure(
                error.message ?: "Light program could not be cleared"
            )
        }

        return httpClient.postSet(
            ip = address.ip,
            json = json,
            requestTag = "light_program_clear"
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

    private fun buildClearScheduleJson(
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
                    .put("LP", JSONArray())
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
                val timeline = LightProgramTimelineBuilder.build(
                    draft = program.draft
                )

                timeline.phases.forEach { phase ->
                    when (phase.type) {
                        LightProgramPhaseType.MAIN_CURVE -> {
                            addMainCurvePointsForChannel(
                                points = points,
                                phase = phase,
                                semantic = semantic
                            )
                        }

                        LightProgramPhaseType.MOONLIGHT -> {
                            addMoonlightPointsForChannel(
                                points = points,
                                phase = phase,
                                semantic = semantic
                            )
                        }

                        LightProgramPhaseType.CLOUD_OVERLAY -> Unit
                    }
                }
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

    private fun addMainCurvePointsForChannel(
        points: MutableList<SchedulePoint>,
        phase: LightProgramTimelinePhase,
        semantic: LightChannelSemantic
    ) {
        val peakValue = percentToEsp32Value(
            valuePercent = valueForSemantic(
                values = phase.channelValues,
                semantic = semantic
            )
        )

        val startMinute = phase.startMinute.coerceIn(
            0,
            MINUTES_PER_DAY
        )

        val peakStartMinute = (phase.peakStartMinute ?: phase.startMinute)
            .coerceIn(
                0,
                MINUTES_PER_DAY
            )

        val peakEndMinute = (phase.peakEndMinute ?: phase.endMinute)
            .coerceIn(
                0,
                MINUTES_PER_DAY
            )

        val endMinute = phase.endMinute.coerceIn(
            0,
            MINUTES_PER_DAY
        )

        addOrReplacePoint(
            points = points,
            minute = startMinute,
            label = labelForMinute(startMinute),
            value = 0.0
        )

        addOrReplacePoint(
            points = points,
            minute = peakStartMinute,
            label = labelForMinute(peakStartMinute),
            value = peakValue
        )

        addOrReplacePoint(
            points = points,
            minute = peakEndMinute,
            label = labelForMinute(peakEndMinute),
            value = peakValue
        )

        addOrReplacePoint(
            points = points,
            minute = endMinute,
            label = labelForMinute(endMinute),
            value = 0.0
        )
    }

    private fun addMoonlightPointsForChannel(
        points: MutableList<SchedulePoint>,
        phase: LightProgramTimelinePhase,
        semantic: LightChannelSemantic
    ) {
        val value = percentToEsp32Value(
            valuePercent = valueForSemantic(
                values = phase.channelValues,
                semantic = semantic
            )
        )

        if (value <= 0.0) {
            addMoonlightOffPointsForUnusedChannel(
                points = points,
                phase = phase
            )
            return
        }

        val start = phase.startMinute
        val end = phase.endMinute

        when {
            start < MINUTES_PER_DAY && end <= MINUTES_PER_DAY -> {
                addFlatSegmentPoints(
                    points = points,
                    startMinute = start,
                    endMinute = end,
                    value = value
                )
            }

            start < MINUTES_PER_DAY && end > MINUTES_PER_DAY -> {
                addFlatSegmentPoints(
                    points = points,
                    startMinute = start,
                    endMinute = MINUTES_PER_DAY,
                    value = value
                )

                val morningEnd = (end - MINUTES_PER_DAY)
                    .coerceIn(0, MINUTES_PER_DAY)

                if (morningEnd > 0) {
                    addFlatSegmentPoints(
                        points = points,
                        startMinute = 0,
                        endMinute = morningEnd,
                        value = value
                    )
                }
            }

            start >= MINUTES_PER_DAY -> {
                val normalizedStart = (start - MINUTES_PER_DAY)
                    .coerceIn(0, MINUTES_PER_DAY)

                val normalizedEnd = (end - MINUTES_PER_DAY)
                    .coerceIn(0, MINUTES_PER_DAY)

                if (normalizedEnd > normalizedStart) {
                    addFlatSegmentPoints(
                        points = points,
                        startMinute = normalizedStart,
                        endMinute = normalizedEnd,
                        value = value
                    )
                }
            }
        }
    }

    private fun addMoonlightOffPointsForUnusedChannel(
        points: MutableList<SchedulePoint>,
        phase: LightProgramTimelinePhase
    ) {
        val start = phase.startMinute
        val end = phase.endMinute

        when {
            start < MINUTES_PER_DAY && end <= MINUTES_PER_DAY -> {
                addOrReplacePoint(
                    points = points,
                    minute = start.coerceIn(0, MINUTES_PER_DAY),
                    label = labelForMinute(start),
                    value = 0.0
                )

                addOrReplacePoint(
                    points = points,
                    minute = end.coerceIn(0, MINUTES_PER_DAY),
                    label = labelForMinute(end),
                    value = 0.0
                )
            }

            start < MINUTES_PER_DAY && end > MINUTES_PER_DAY -> {
                addOrReplacePoint(
                    points = points,
                    minute = start.coerceIn(0, MINUTES_PER_DAY),
                    label = labelForMinute(start),
                    value = 0.0
                )

                addOrReplacePoint(
                    points = points,
                    minute = MINUTES_PER_DAY,
                    label = labelForMinute(MINUTES_PER_DAY),
                    value = 0.0
                )

                val morningEnd = (end - MINUTES_PER_DAY)
                    .coerceIn(0, MINUTES_PER_DAY)

                if (morningEnd > 0) {
                    addOrReplacePoint(
                        points = points,
                        minute = 0,
                        label = labelForMinute(0),
                        value = 0.0
                    )

                    addOrReplacePoint(
                        points = points,
                        minute = morningEnd,
                        label = labelForMinute(morningEnd),
                        value = 0.0
                    )
                }
            }

            start >= MINUTES_PER_DAY -> {
                val normalizedStart = (start - MINUTES_PER_DAY)
                    .coerceIn(0, MINUTES_PER_DAY)

                val normalizedEnd = (end - MINUTES_PER_DAY)
                    .coerceIn(0, MINUTES_PER_DAY)

                if (normalizedEnd > normalizedStart) {
                    addOrReplacePoint(
                        points = points,
                        minute = normalizedStart,
                        label = labelForMinute(normalizedStart),
                        value = 0.0
                    )

                    addOrReplacePoint(
                        points = points,
                        minute = normalizedEnd,
                        label = labelForMinute(normalizedEnd),
                        value = 0.0
                    )
                }
            }
        }
    }

    private fun addFlatSegmentPoints(
        points: MutableList<SchedulePoint>,
        startMinute: Int,
        endMinute: Int,
        value: Double
    ) {
        val safeStart = startMinute.coerceIn(
            0,
            MINUTES_PER_DAY
        )

        val safeEnd = endMinute.coerceIn(
            0,
            MINUTES_PER_DAY
        )

        if (safeEnd <= safeStart) {
            return
        }

        addOrReplacePoint(
            points = points,
            minute = safeStart,
            label = labelForMinute(safeStart),
            value = value
        )

        addOrReplacePoint(
            points = points,
            minute = safeEnd,
            label = labelForMinute(safeEnd),
            value = if (safeEnd == MINUTES_PER_DAY) {
                value
            } else {
                0.0
            }
        )
    }

    private fun valueForSemantic(
        values: LightCurveChannelValues,
        semantic: LightChannelSemantic
    ): Int {
        return when (semantic) {
            LightChannelSemantic.RED -> values.red
            LightChannelSemantic.GREEN -> values.green
            LightChannelSemantic.BLUE -> values.blue
            LightChannelSemantic.WHITE -> values.white
            LightChannelSemantic.UNKNOWN -> 0
        }
    }

    private fun addOrReplacePoint(
        points: MutableList<SchedulePoint>,
        minute: Int,
        label: String,
        value: Double
    ) {
        val safeMinute = minute.coerceIn(
            0,
            MINUTES_PER_DAY
        )

        val point = SchedulePoint(
            minute = safeMinute,
            label = labelForMinute(safeMinute),
            value = value
        )

        val existingIndex = points.indexOfFirst { item ->
            item.minute == safeMinute
        }

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

    private fun labelForMinute(
        minute: Int
    ): String {
        val safeMinute = minute.coerceIn(
            0,
            MINUTES_PER_DAY
        )

        if (safeMinute == MINUTES_PER_DAY) {
            return "24:00"
        }

        val hour = safeMinute / 60
        val minutePart = safeMinute % 60

        return "%02d:%02d".format(
            hour,
            minutePart
        )
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
        private const val MINUTES_PER_DAY = 24 * 60
    }
}