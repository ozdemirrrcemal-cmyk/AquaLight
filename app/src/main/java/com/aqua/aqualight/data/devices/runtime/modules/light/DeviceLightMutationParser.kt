package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

/** Exact parsers for every successful Light V1 command response. */
internal object DeviceLightMutationParser {
    fun parseControl(data: JSONObject): DeviceLightControlSetResult {
        requireBaseOrEventKeys(data, CONTROL_KEYS, "light.control.set.data")
        return DeviceLightControlSetResult(
            mode = DeviceLightMode.fromWireExact(data.requireLightText("mode")),
            event = parseOptionalEvent(data)
        )
    }

    fun parseManual(data: JSONObject, product: DeviceLightProduct): DeviceLightManualSetResult {
        requireBaseOrEventKeys(data, MANUAL_KEYS, "light.manual data")
        return DeviceLightManualSetResult(
            scene = DeviceLightV1JsonParser.parseScene(
                data.requireLightObject("scene"),
                product,
                "light.manual.data.scene"
            ),
            event = parseOptionalEvent(data)
        )
    }

    fun parseAutoPrograms(data: JSONObject, product: DeviceLightProduct): DeviceLightAutoPrograms {
        data.requireLightKeys(AUTO_PROGRAMS_KEYS, "light.auto.programs.get.data")
        val jsonPrograms = data.requireLightArray("programs")
        val programs = List(jsonPrograms.length()) { index ->
            DeviceLightV1JsonParser.parseProgram(jsonPrograms.requireLightObject(index), product)
        }
        val result = DeviceLightAutoPrograms(
            revision = data.requireLightLong(
                "revision",
                0,
                DeviceLightRuntimeContract.Limit.UINT32_MAX
            ),
            capacity = data.requireLightInt("capacity", 0),
            programCount = data.requireLightInt("programCount", 0),
            enabledCount = data.requireLightInt("enabledCount", 0),
            programs = programs
        )
        require(result.capacity == DeviceLightRuntimeContract.Limit.AUTO_PROGRAM_CAPACITY)
        require(result.programCount == programs.size)
        require(result.enabledCount == programs.count { it.enabled })
        require(programs.map { it.programId }.distinct().size == programs.size)
        return result
    }

    fun parseAutoProgramMutation(
        data: JSONObject,
        product: DeviceLightProduct
    ): DeviceLightAutoProgramMutationResult {
        requireBaseOrEventKeys(data, AUTO_MUTATION_KEYS, "Light AUTO mutation data")
        return DeviceLightAutoProgramMutationResult(
            revision = data.requireLightLong(
                "revision",
                0,
                DeviceLightRuntimeContract.Limit.UINT32_MAX
            ),
            program = DeviceLightV1JsonParser.parseProgram(
                data.requireLightObject("program"),
                product
            ),
            event = parseOptionalEvent(data)
        )
    }

    fun parseAutoProgramDelete(data: JSONObject): DeviceLightAutoProgramDeleteResult {
        requireBaseOrEventKeys(data, AUTO_DELETE_KEYS, "light.auto.program.delete.data")
        val programId = data.requireLightText("programId")
        require(PROGRAM_ID.matches(programId))
        return DeviceLightAutoProgramDeleteResult(
            revision = data.requireLightLong(
                "revision",
                0,
                DeviceLightRuntimeContract.Limit.UINT32_MAX
            ),
            programId = programId,
            deleted = data.requireLightBoolean("deleted").also { require(it) },
            event = parseOptionalEvent(data)
        )
    }

    internal object CustomAndAcclimation {
        fun parseCustom(
        data: JSONObject,
        product: DeviceLightProduct
    ): DeviceLightCustomDocument {
        requireBaseOrEventKeys(data, CUSTOM_KEYS, "light.custom data")
        val pointData = data.requireLightArray("points")
        val points = List(pointData.length()) { index ->
            DeviceLightV1JsonParser.parseCustomPoint(pointData.requireLightArray(index), product)
        }
        val result = DeviceLightCustomDocument(
            revision = data.requireLightLong(
                "revision",
                0,
                DeviceLightRuntimeContract.Limit.UINT32_MAX
            ),
            installed = data.requireLightBoolean("installed"),
            weekdaysMask = data.requireLightInt(
                "weekdaysMask",
                0,
                DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MAX
            ),
            pointCount = data.requireLightInt(
                "pointCount",
                0,
                DeviceLightRuntimeContract.Limit.CUSTOM_POINT_CAPACITY
            ),
            points = points,
            event = parseOptionalEvent(data)
        )
        require(result.pointCount == points.size)
        require(points.zipWithNext().all { (left, right) -> left.timeMs < right.timeMs })
        require(result.installed || (result.weekdaysMask == 0 && points.isEmpty()))
        require(
            !result.installed || result.weekdaysMask in
                DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MIN..
                DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MAX
        )
        return result
    }

        fun parseAcclimation(
        data: JSONObject,
        product: DeviceLightProduct
    ): DeviceLightAcclimationStatus {
        requireBaseOrEventKeys(data, ACCLIMATION_KEYS, "light.acclimation data")
        return DeviceLightV1JsonParser.Activity.parseAcclimation(
            data.withoutOptionalLightEvent(),
            product
        )
        }
    }

    fun parsePreviewSet(data: JSONObject): DeviceLightPreviewResult {
        data.requireLightKeys(PREVIEW_SET_KEYS, "light.preview.set.data")
        return DeviceLightPreviewResult(
            active = data.requireLightBoolean("active").also { require(it) },
            remainingMs = data.requireLightLong(
                "remainingMs",
                0,
                DeviceLightRuntimeContract.Limit.MAX_PREVIEW_DURATION_MS
            ),
            event = requireStatusEvent(data)
        )
    }

    fun parsePreviewClear(data: JSONObject): DeviceLightPreviewResult {
        data.requireLightKeys(PREVIEW_CLEAR_KEYS, "light.preview.clear.data")
        return DeviceLightPreviewResult(
            active = data.requireLightBoolean("active").also { require(!it) },
            remainingMs = null,
            event = requireStatusEvent(data)
        )
    }

    internal object Graph {
        fun parseGraph(data: JSONObject, product: DeviceLightProduct): DeviceLightGraph {
        data.requireLightKeys(GRAPH_KEYS, "light.graph.get.data")
        val pointData = data.requireLightArray("points")
        val points = List(pointData.length()) { index ->
            parseGraphPoint(pointData.requireLightArray(index), product)
        }
        val spanData = data.requireLightArray("autoSpans")
        val spans = List(spanData.length()) { index ->
            parseGraphSpan(spanData.requireLightArray(index))
        }
        val result = DeviceLightGraph(
            mode = DeviceLightMode.fromWireExact(data.requireLightText("mode")),
            available = data.requireLightBoolean("available"),
            reason = DeviceLightGraphReason.fromWireExact(data.requireLightText("reason")),
            sourceRevision = data.requireLightLong(
                "sourceRevision",
                0,
                DeviceLightRuntimeContract.Limit.UINT32_MAX
            ),
            schedulerGeneration = data.requireNullableLightLong("schedulerGeneration", 0),
            localDate = data.requireNullableLightText("localDate"),
            currentWeekdayMask = data.requireLightInt(
                "currentWeekdayMask",
                0,
                DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MAX
            ),
            nowTimeMs = data.requireNullableLightLong(
                "nowTimeMs",
                0,
                DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND
            ),
            basis = DeviceLightGraphBasis.fromWireExact(data.requireLightText("basis")),
            channelScale = data.requireLightInt("channelScale"),
            hasScheduleToday = data.requireLightBoolean("hasScheduleToday"),
            points = points,
            autoSpans = spans
        )
        require(result.channelScale == DeviceLightRuntimeContract.Limit.PERMILLE_MAX)
        require(
            (result.schedulerGeneration == null && result.localDate == null && result.nowTimeMs == null) ||
                (result.schedulerGeneration != null && result.localDate != null && result.nowTimeMs != null)
        )
        require(result.hasScheduleToday == points.isNotEmpty())
        require(result.mode == DeviceLightMode.AUTO || spans.isEmpty())
        return result
    }

        private fun parseGraphPoint(
        tuple: JSONArray,
        product: DeviceLightProduct
    ): DeviceLightGraphPoint {
        require(tuple.length() == product.channelCount + 1)
        return DeviceLightGraphPoint(
            timeMs = tuple.requireLightLong(0, 0, DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY),
            channelPermille = List(product.channelCount) { index ->
                tuple.requireLightInt(
                    index + 1,
                    DeviceLightRuntimeContract.Limit.PERMILLE_MIN,
                    DeviceLightRuntimeContract.Limit.PERMILLE_MAX
                )
            }
        )
    }

        private fun parseGraphSpan(tuple: JSONArray): DeviceLightGraphSpan {
        require(tuple.length() == DeviceLightRuntimeContract.Limit.GRAPH_SPAN_TUPLE_SIZE)
        val id = tuple.requireLightText(DeviceLightRuntimeContract.Limit.GRAPH_SPAN_PROGRAM_ID_INDEX)
        require(PROGRAM_ID.matches(id))
        return DeviceLightGraphSpan(
            startTimeMsWithinToday = tuple.requireLightLong(
                0,
                0,
                DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY
            ),
            endTimeMsWithinToday = tuple.requireLightLong(
                1,
                0,
                DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY
            ),
            programId = id
        )
        }
    }

    private fun requireBaseOrEventKeys(data: JSONObject, base: Set<String>, label: String) {
        val actual = data.keys().asSequence().toSet()
        require(actual == base || actual == base + "event") {
            "$label keys differ from the firmware contract."
        }
        parseOptionalEvent(data)
    }

    private fun parseOptionalEvent(data: JSONObject): String? = if (data.has("event")) {
        requireStatusEvent(data)
    } else {
        null
    }

    private fun requireStatusEvent(data: JSONObject): String =
        data.requireLightText("event").also {
            require(it == DeviceLightRuntimeContract.Event.STATUS_CHANGED)
        }

    private val CONTROL_KEYS = setOf("mode")
    private val MANUAL_KEYS = setOf("scene")
    private val AUTO_PROGRAMS_KEYS = setOf(
        "revision", "capacity", "programCount", "enabledCount", "programs"
    )
    private val AUTO_MUTATION_KEYS = setOf("revision", "program")
    private val AUTO_DELETE_KEYS = setOf("revision", "programId", "deleted")
    private val CUSTOM_KEYS = setOf(
        "revision", "installed", "weekdaysMask", "pointCount", "points"
    )
    private val ACCLIMATION_KEYS = setOf(
        "supported", "revision", "state", "clockReady", "startPercent", "currentPermille",
        "targetPercent", "durationDays", "startedAtEpochSeconds", "endsAtEpochSeconds",
        "remainingSeconds"
    )
    private val PREVIEW_SET_KEYS = setOf("active", "remainingMs", "event")
    private val PREVIEW_CLEAR_KEYS = setOf("active", "event")
    private val GRAPH_KEYS = setOf(
        "mode", "available", "reason", "sourceRevision", "schedulerGeneration", "localDate",
        "currentWeekdayMask", "nowTimeMs", "basis", "channelScale", "hasScheduleToday",
        "points", "autoSpans"
    )
    private val PROGRAM_ID = Regex("^ap-[0-9a-f]{8}$")
}

private fun JSONObject.withoutOptionalLightEvent(): JSONObject = JSONObject(toString()).also {
    it.remove("event")
}
