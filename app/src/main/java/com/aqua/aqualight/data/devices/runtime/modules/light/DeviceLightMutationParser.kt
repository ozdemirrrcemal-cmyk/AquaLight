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

    internal object ManagedPlan {
        fun parse(
            data: JSONObject,
            product: DeviceLightProduct
        ): DeviceLightManagedPlan {
        requireBaseOrEventKeys(data, MANAGED_PLAN_KEYS, "light.auto.plan data")
        val phaseData = data.requireLightArray("phases")
        val phases = List(phaseData.length()) { index ->
            DeviceLightV1JsonParser.parseManagedPlanPhase(
                phaseData.requireLightObject(index),
                product
            )
        }
        val result = DeviceLightManagedPlan(
            storageGeneration = data.requireLightLong(
                "storageGeneration",
                0,
                DeviceLightRuntimeContract.Limit.UINT32_MAX
            ),
            revision = data.requireLightLong(
                "revision",
                0,
                DeviceLightRuntimeContract.Limit.UINT32_MAX
            ),
            installed = data.requireLightBoolean("installed"),
            planId = data.requireNullableLightText("planId"),
            initialStartPercent = data.requireLightInt(
                "initialStartPercent",
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_INITIAL_START_PERCENT_MIN,
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_INITIAL_START_PERCENT_MAX
            ),
            phaseCount = data.requireLightInt(
                "phaseCount",
                0,
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_PHASE_CAPACITY
            ),
            phases = phases,
            runtime = parseRuntime(data.requireLightObject("runtime")),
            event = parseOptionalEvent(data)
        )
        require(
            result.initialStartPercent %
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_INITIAL_START_PERCENT_STEP == 0
        )
        require(result.phaseCount == phases.size)
        require(result.installed == (result.planId != null))
        result.planId?.let(::requireLightManagedPlanId)
        if (result.installed) {
            require(phases.isNotEmpty())
            require(phases.dropLast(1).all { it.validUntilEpochDayExclusive != null })
            require(phases.last().validUntilEpochDayExclusive == null)
            require(phases.zipWithNext().all { (left, right) ->
                left.validUntilEpochDayExclusive == right.validFromEpochDay
            })
        } else {
            require(phases.isEmpty())
            require(result.runtime.state == DeviceLightManagedPlanRuntimeState.NOT_INSTALLED)
        }
        return result
    }

        fun parseDelete(data: JSONObject): DeviceLightManagedPlanDeleteResult {
        requireBaseOrEventKeys(data, MANAGED_PLAN_DELETE_KEYS, "light.auto.plan.delete.data")
        return DeviceLightManagedPlanDeleteResult(
            revision = data.requireLightLong(
                "revision",
                0,
                DeviceLightRuntimeContract.Limit.UINT32_MAX
            ),
            storageGeneration = data.requireLightLong(
                "storageGeneration",
                0,
                DeviceLightRuntimeContract.Limit.UINT32_MAX
            ),
            planId = data.requireLightText("planId").also(::requireLightManagedPlanId),
            deleted = data.requireLightBoolean("deleted").also { require(it) },
            event = parseOptionalEvent(data)
        )
    }

        private fun parseRuntime(data: JSONObject): DeviceLightManagedPlanRuntime {
        data.requireLightKeys(MANAGED_PLAN_RUNTIME_KEYS, "light managed plan runtime")
        val result = DeviceLightManagedPlanRuntime(
            clockReady = data.requireLightBoolean("clockReady"),
            state = DeviceLightManagedPlanRuntimeState.fromWireExact(data.requireLightText("state")),
            activePhaseIndex = data.requireNullableLightInt(
                "activePhaseIndex",
                0,
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_PHASE_CAPACITY - 1
            ),
            transitionPermille = data.requireNullableLightInt(
                "transitionPermille",
                DeviceLightRuntimeContract.Limit.PERMILLE_MIN,
                DeviceLightRuntimeContract.Limit.PERMILLE_MAX
            ),
            nextTransitionEpochDay = data.requireNullableLightLong(
                "nextTransitionEpochDay",
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_EPOCH_DAY_MIN,
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_EPOCH_DAY_MAX
            )
        )
        require((result.activePhaseIndex != null) == (result.transitionPermille != null))
        require(
            (result.state == DeviceLightManagedPlanRuntimeState.ACTIVE) ==
                (result.activePhaseIndex != null)
        )
        require(result.state != DeviceLightManagedPlanRuntimeState.RTC_BLOCKED || !result.clockReady)
        return result
        }
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
        val planSpanData = data.requireLightArray("planSpans")
        val planSpans = List(planSpanData.length()) { index ->
            parseManagedPlanGraphSpan(planSpanData.requireLightArray(index))
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
            autoSpans = spans,
            planSpans = planSpans
        )
        require(result.channelScale == DeviceLightRuntimeContract.Limit.PERMILLE_MAX)
        require(
            (result.schedulerGeneration == null && result.localDate == null && result.nowTimeMs == null) ||
                (result.schedulerGeneration != null && result.localDate != null && result.nowTimeMs != null)
        )
        require(result.hasScheduleToday == points.isNotEmpty())
        require(result.mode == DeviceLightMode.AUTO || spans.isEmpty())
        require(result.mode == DeviceLightMode.AUTO || planSpans.isEmpty())
        require(result.basis == DeviceLightGraphBasis.AUTHORED_SCHEDULE || spans.isEmpty())
        require(result.basis == DeviceLightGraphBasis.MANAGED_PLAN || planSpans.isEmpty())
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

        private fun parseManagedPlanGraphSpan(tuple: JSONArray): DeviceLightManagedPlanGraphSpan {
            require(tuple.length() == DeviceLightRuntimeContract.Limit.GRAPH_PLAN_SPAN_TUPLE_SIZE)
            return DeviceLightManagedPlanGraphSpan(
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
                planId = tuple.requireLightText(
                    DeviceLightRuntimeContract.Limit.GRAPH_PLAN_SPAN_ID_INDEX
                ).also(::requireLightManagedPlanId),
                phaseIndex = tuple.requireLightInt(
                    DeviceLightRuntimeContract.Limit.GRAPH_PLAN_SPAN_PHASE_INDEX,
                    0,
                    DeviceLightRuntimeContract.Limit.MANAGED_PLAN_PHASE_CAPACITY - 1
                )
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
    private val MANAGED_PLAN_KEYS = setOf(
        "storageGeneration", "revision", "installed", "planId", "initialStartPercent",
        "phaseCount", "phases", "runtime"
    )
    private val MANAGED_PLAN_RUNTIME_KEYS = setOf(
        "clockReady", "state", "activePhaseIndex", "transitionPermille",
        "nextTransitionEpochDay"
    )
    private val MANAGED_PLAN_DELETE_KEYS = setOf(
        "revision", "storageGeneration", "planId", "deleted"
    )
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
        "points", "autoSpans", "planSpans"
    )
    private val PROGRAM_ID = Regex("^ap-[0-9a-f]{8}$")
}

private val LIGHT_MANAGED_PLAN_ID = Regex("^lp-[0-9a-f]{8}$")

private fun requireLightManagedPlanId(value: String) =
    require(LIGHT_MANAGED_PLAN_ID.matches(value)) { "Invalid Light managed planId." }

private fun JSONObject.withoutOptionalLightEvent(): JSONObject = JSONObject(toString()).also {
    it.remove("event")
}
