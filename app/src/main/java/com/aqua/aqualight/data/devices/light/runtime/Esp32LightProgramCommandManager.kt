package com.aqua.aqualight.data.devices.light.runtime

import android.content.Context
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.interpolator.LightCurveInterpolator
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramPhaseType
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramTimelineBuilder
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramTimelinePhase
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.validation.LightProgramScheduleConflictValidator
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay

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
        .filter {
            program ->
            program.deviceId == deviceId && program.isActive
        }
        .sortedBy {
            program ->
            program.draft.start.totalMinutes
        }

        if (activePrograms.isEmpty()) {
            return clearPrograms(
                deviceId = deviceId
            )
        }

        val conflict = findActiveProgramConflict(
            programs = activePrograms
        )

        if (conflict != null) {
            return LightCommandResult.failure(
                "${conflict.first.name} overlaps with ${conflict.second.name}"
            )
        }

        val address = resolveAddress(deviceId)
        ?: return LightCommandResult.failure("Device address could not be resolved")

        val mapping = mappingReader.readMapping(
            ip = address.ip,
            forceRefresh = true
        ).getOrElse {
            error ->
            return LightCommandResult.failure(
                error.message ?: "Light channel mapping could not be read"
            )
        }

        val json = buildScheduleJson(
            programs = activePrograms,
            mapping = mapping
        ).getOrElse {
            error ->
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
        ).getOrElse {
            error ->
            return LightCommandResult.failure(
                error.message ?: "Light channel mapping could not be read"
            )
        }

        val zeroScheduleJson = buildZeroScheduleJson(
            mapping = mapping
        ).getOrElse {
            error ->
            return LightCommandResult.failure(
                error.message ?: "Light program could not be cleared"
            )
        }

        val zeroScheduleResult = httpClient.postSet(
            ip = address.ip,
            json = zeroScheduleJson,
            requestTag = "light_program_zero_schedule"
        )

        if (!zeroScheduleResult.isSuccess) {
            return zeroScheduleResult
        }

        delay(CLEAR_TO_OFF_DELAY_MS)

        val manualOffJson = buildManualOffJson(
            mapping = mapping
        ).getOrElse {
            error ->
            return LightCommandResult.failure(
                error.message ?: "Light output could not be turned off"
            )
        }

        return httpClient.postSet(
            ip = address.ip,
            json = manualOffJson,
            requestTag = "light_program_force_off"
        )
    }

    private fun buildZeroScheduleJson(
        mapping: LightDeviceChannelMapping
    ): Result<String> {
        val mappedEntries = mappedRgbwEntries(
            mapping = mapping
        )

        if (mappedEntries.isEmpty()) {
            return Result.failure(
                IllegalStateException("No RGBW channel mapping found")
            )
        }

        val lightData = JSONObject()

        mappedEntries.forEach {
            entry ->
            lightData.put(
                entry.lightIndex,
                JSONObject()
                .put("GPIO_PWM", entry.gpioPwm)
                .put("LP", buildZeroLightPoints())
            )
        }

        val json = JSONObject()
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

    private suspend fun resolveAddress(
        deviceId: Long
    ): LightDeviceAddressResolver.Result.Success? {
        return when (
            val result = addressResolver.resolve(
                deviceId = deviceId,
                requireOnline = true
            )
        ) {
            is LightDeviceAddressResolver.Result.Success -> result
            is LightDeviceAddressResolver.Result.Failure -> null
        }
    }

    private fun findActiveProgramConflict(
        programs: List<SavedLightProgram>
    ): Pair<SavedLightProgram, SavedLightProgram>? {
        val sortedPrograms = programs.sortedBy {
            program ->
            program.draft.start.totalMinutes
        }

        sortedPrograms.forEachIndexed {
            index, candidate ->
            val otherPrograms = sortedPrograms.filterIndexed {
                otherIndex, _ ->
                otherIndex != index
            }

            val conflict = LightProgramScheduleConflictValidator.findConflict(
                candidate = candidate,
                existingPrograms = otherPrograms
            )

            if (conflict != null) {
                return candidate to conflict
            }
        }

        return null
    }

    private fun mappedRgbwEntries(
        mapping: LightDeviceChannelMapping
    ): List<LightDeviceChannelMapping.Entry> {
        return mapping.rgbwEntries()
        .filter {
            entry ->
            entry.gpioPwm.isNotBlank() && entry.gpioPwm != "-"
        }
    }

    private fun buildScheduleJson(
        programs: List<SavedLightProgram>,
        mapping: LightDeviceChannelMapping
    ): Result<String> {
        val mappedEntries = mappedRgbwEntries(
            mapping = mapping
        )

        if (mappedEntries.isEmpty()) {
            return Result.failure(
                IllegalStateException("No RGBW channel mapping found")
            )
        }

        val lightData = JSONObject()

        mappedEntries.forEach {
            entry ->
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


    private fun buildZeroLightPoints(): JSONArray {
        return JSONArray()
        .put(
            JSONArray()
            .put(labelForMinute(0))
            .put(0.0)
        )
        .put(
            JSONArray()
            .put(labelForMinute(MINUTES_PER_DAY))
            .put(0.0)
        )
    }

    private fun buildLightPointsForChannel(
        programs: List<SavedLightProgram>,
        semantic: LightChannelSemantic
    ): JSONArray {
        val points = mutableListOf<SchedulePoint>()

        addOrReplacePoint(
            points = points,
            minute = 0,
            label = labelForMinute(0),
            value = 0.0
        )

        addOrReplacePoint(
            points = points,
            minute = MINUTES_PER_DAY,
            label = labelForMinute(MINUTES_PER_DAY),
            value = 0.0
        )

        programs
        .sortedBy {
            program ->
            program.draft.start.totalMinutes
        }
        .forEach {
            program ->
            val timeline = LightProgramTimelineBuilder.build(
                draft = program.draft
            )

            timeline.phases.forEach {
                phase ->
                when (phase.type) {
                    LightProgramPhaseType.MAIN_CURVE -> {
                        addMainCurvePointsForChannel(
                            points = points,
                            phase = phase,
                            semantic = semantic,
                            transitionMode = program.draft.transitionMode
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
        .sortedBy {
            item ->
            item.minute
        }
        .forEach {
            item ->
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
        semantic: LightChannelSemantic,
        transitionMode: LightCurveTransitionMode
    ) {
        val peakPercent = valueForSemantic(
            values = phase.channelValues,
            semantic = semantic
        ).coerceIn(0, 100)

        val peakValue = percentToEsp32Value(
            valuePercent = peakPercent
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

        if (transitionMode == LightCurveTransitionMode.LINEAR) {
            addLinearMainCurvePoints(
                points = points,
                startMinute = startMinute,
                peakStartMinute = peakStartMinute,
                peakEndMinute = peakEndMinute,
                endMinute = endMinute,
                peakValue = peakValue
            )
            return
        }

        val curvePoints = LightCurveInterpolator.buildCurvePoints(
            startMinute = startMinute,
            peakStartMinute = peakStartMinute,
            peakEndMinute = peakEndMinute,
            endMinute = endMinute,
            peakPercent = peakPercent,
            transitionMode = transitionMode
        ).sortedBy {
            point ->
            point.x
        }

        if (curvePoints.isEmpty()) {
            addLinearMainCurvePoints(
                points = points,
                startMinute = startMinute,
                peakStartMinute = peakStartMinute,
                peakEndMinute = peakEndMinute,
                endMinute = endMinute,
                peakValue = peakValue
            )
            return
        }

        val sampledMinutes = (
            sampleRampMinutes(
                startMinute = startMinute,
                endMinute = peakStartMinute
            ) +
            listOf(peakEndMinute) +
            sampleRampMinutes(
                startMinute = peakEndMinute,
                endMinute = endMinute
            )
        )
        .map {
            minute ->
            minute.coerceIn(0, MINUTES_PER_DAY)
        }
        .distinct()
        .sorted()

        sampledMinutes.forEach {
            minute ->
            val percent = calculatePercentAtMinute(
                curvePoints = curvePoints,
                minute = minute
            )

            addOrReplacePoint(
                points = points,
                minute = minute,
                label = labelForMinute(minute),
                value = percentToEsp32Value(
                    valuePercent = percent
                )
            )
        }
    }

    private fun addLinearMainCurvePoints(
        points: MutableList<SchedulePoint>,
        startMinute: Int,
        peakStartMinute: Int,
        peakEndMinute: Int,
        endMinute: Int,
        peakValue: Double
    ) {
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

    private fun sampleRampMinutes(
        startMinute: Int,
        endMinute: Int
    ): List<Int> {
        if (endMinute <= startMinute) {
            return listOf(startMinute)
        }

        val distance = endMinute - startMinute
        val result = mutableListOf<Int>()

        for (index in 0..SCHEDULE_RAMP_SAMPLE_COUNT) {
            val ratio =
            index.toDouble() / SCHEDULE_RAMP_SAMPLE_COUNT.toDouble()

            val sampledMinute = (
                startMinute.toDouble() + (distance.toDouble() * ratio)
            ).roundToInt()

            result += sampledMinute.coerceIn(
                0,
                MINUTES_PER_DAY
            )
        }

        return result.distinct()
    }

    private fun calculatePercentAtMinute(
        curvePoints: List<android.graphics.PointF>,
        minute: Int
    ): Int {
        val current = minute.toDouble()

        val previous = curvePoints.lastOrNull {
            point ->
            point.x.toDouble() <= current
        }

        val next = curvePoints.firstOrNull {
            point ->
            point.x.toDouble() >= current
        }

        val value = when {
            previous == null -> {
                curvePoints.first().y.toDouble()
            }

            next == null -> {
                curvePoints.last().y.toDouble()
            }

            previous.x == next.x -> {
                previous.y.toDouble()
            } else -> {
                val previousX = previous.x.toDouble()
                val nextX = next.x.toDouble()
                val previousY = previous.y.toDouble()
                val nextY = next.y.toDouble()

                val progress =
                (current - previousX) / (nextX - previousX)

                previousY + ((nextY - previousY) * progress)
            }
        }

        return value
        .roundToInt()
        .coerceIn(0, 100)
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
                .coerceIn(
                    0,
                    MINUTES_PER_DAY
                )

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
                .coerceIn(
                    0,
                    MINUTES_PER_DAY
                )

                val normalizedEnd = (end - MINUTES_PER_DAY)
                .coerceIn(
                    0,
                    MINUTES_PER_DAY
                )

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
                .coerceIn(
                    0,
                    MINUTES_PER_DAY
                )

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
                .coerceIn(
                    0,
                    MINUTES_PER_DAY
                )

                val normalizedEnd = (end - MINUTES_PER_DAY)
                .coerceIn(
                    0,
                    MINUTES_PER_DAY
                )

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

        val safeLabel = label
        .trim()
        .ifBlank {
            labelForMinute(safeMinute)
        }

        val point = SchedulePoint(
            minute = safeMinute,
            label = safeLabel,
            value = value
        )

        val existingIndex = points.indexOfFirst {
            item ->
            item.minute == safeMinute
        }

        if (existingIndex >= 0) {
            points[existingIndex] = point
        } else {
            points += point
        }
    }

    private fun buildManualOffJson(
        mapping: LightDeviceChannelMapping
    ): Result<String> {
        val mappedEntries = mappedRgbwEntries(
            mapping = mapping
        )

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
                buildManualOffData(
                    entries = mappedEntries
                )
            )
            .put("Group", 1)
        )
        .toString()

        return Result.success(json)
    }

    private fun buildManualOffData(
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
                    .put("V", MANUAL_OFF_VALUE)
                    .put("TOffMs", MANUAL_OFF_TIMEOUT_MS)
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
        return valuePercent.coerceIn(0, 100).toDouble() / 100.0
    }

    companion object {
        private const val MANUAL_OFF_VALUE = 0
        private const val MANUAL_OFF_TIMEOUT_MS = 604_800_000L
        private const val CLEAR_TO_OFF_DELAY_MS = 150L
        private const val SCHEDULE_RAMP_SAMPLE_COUNT = 8
        private const val MINUTES_PER_DAY = 24 * 60
    }
}