package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceLightProduct(
    val wireValue: String,
    val sceneFields: List<String>,
    val supportsAcclimation: Boolean
) {
    WRGB_PRO_ELITE(
        DeviceLightRuntimeContract.Product.WRGB_PRO_ELITE,
        listOf("redPercent", "greenPercent", "bluePercent", "whitePercent"),
        true
    ),
    RGB_PRO_SLIM(
        DeviceLightRuntimeContract.Product.RGB_PRO_SLIM,
        listOf("redPercent", "greenPercent", "bluePercent"),
        false
    );

    val channelCount: Int get() = sceneFields.size

    companion object {
        fun fromWireExact(value: String): DeviceLightProduct =
            entries.singleOrNull { it.wireValue == value }
                ?: error("Unsupported Light V1 productKey: $value")
    }
}

enum class DeviceLightMode(val wireValue: String) {
    MANUAL("MANUAL"),
    AUTO("AUTO"),
    CUSTOM("CUSTOM");

    companion object {
        fun fromWireExact(value: String): DeviceLightMode =
            enumByWire(value, entries) { it.wireValue }
    }
}

enum class DeviceLightOutputReason(val wireValue: String) {
    ACTIVE("ACTIVE"),
    SCHEDULED_OFF("SCHEDULED_OFF"),
    ALL_CHANNELS_ZERO("ALL_CHANNELS_ZERO"),
    RTC_NOT_READY("RTC_NOT_READY"),
    THERMAL_SHUTDOWN("THERMAL_SHUTDOWN"),
    POWER_LIMITED("POWER_LIMITED"),
    HARDWARE_FAULT("HARDWARE_FAULT");

    companion object {
        fun fromWireExact(value: String): DeviceLightOutputReason =
            enumByWire(value, entries) { it.wireValue }
    }
}

enum class DeviceLightAutoRuntimeState(val wireValue: String) {
    NOT_SELECTED("NOT_SELECTED"),
    RTC_BLOCKED("RTC_BLOCKED"),
    DARK("DARK"),
    RAMP_UP("RAMP_UP"),
    HOLD("HOLD"),
    RAMP_DOWN("RAMP_DOWN");

    companion object {
        fun fromWireExact(value: String): DeviceLightAutoRuntimeState =
            enumByWire(value, entries) { it.wireValue }
    }
}

enum class DeviceLightCustomRuntimeState(val wireValue: String) {
    NOT_SELECTED("NOT_SELECTED"),
    RTC_BLOCKED("RTC_BLOCKED"),
    NOT_INSTALLED("NOT_INSTALLED"),
    NOT_SCHEDULED_TODAY("NOT_SCHEDULED_TODAY"),
    DARK("DARK"),
    ACTIVE("ACTIVE");

    companion object {
        fun fromWireExact(value: String): DeviceLightCustomRuntimeState =
            enumByWire(value, entries) { it.wireValue }
    }
}

enum class DeviceLightAcclimationState(val wireValue: String) {
    DISABLED("DISABLED"),
    ACTIVE("ACTIVE"),
    COMPLETED("COMPLETED");

    companion object {
        fun fromWireExact(value: String): DeviceLightAcclimationState =
            enumByWire(value, entries) { it.wireValue }
    }
}

enum class DeviceLightModelSource(val wireValue: String) {
    DESIGN_NOMINAL("DESIGN_NOMINAL"),
    UNAVAILABLE("UNAVAILABLE");

    companion object {
        fun fromWireExact(value: String): DeviceLightModelSource =
            enumByWire(value, entries) { it.wireValue }
    }
}

enum class DeviceLightPowerLimitBasis(val wireValue: String) {
    NONE("NONE"),
    LED("LED"),
    FIXTURE("FIXTURE"),
    BOTH("BOTH");

    companion object {
        fun fromWireExact(value: String): DeviceLightPowerLimitBasis =
            enumByWire(value, entries) { it.wireValue }
    }
}

enum class DeviceLightGraphReason(val wireValue: String) {
    OK("OK"),
    MODE_HAS_NO_SCHEDULE("MODE_HAS_NO_SCHEDULE"),
    RTC_NOT_READY("RTC_NOT_READY"),
    NO_ENABLED_AUTO_PROGRAM_TODAY("NO_ENABLED_AUTO_PROGRAM_TODAY"),
    CUSTOM_NOT_INSTALLED("CUSTOM_NOT_INSTALLED"),
    CUSTOM_NOT_SCHEDULED_TODAY("CUSTOM_NOT_SCHEDULED_TODAY");

    companion object {
        fun fromWireExact(value: String): DeviceLightGraphReason =
            enumByWire(value, entries) { it.wireValue }
    }
}

enum class DeviceLightGraphBasis(val wireValue: String) {
    AUTHORED_SCHEDULE("AUTHORED_SCHEDULE"),
    NONE("NONE");

    companion object {
        fun fromWireExact(value: String): DeviceLightGraphBasis =
            enumByWire(value, entries) { it.wireValue }
    }
}

data class DeviceLightScene(
    val product: DeviceLightProduct,
    val percents: Map<String, Int>
) {
    init {
        require(percents.keys == product.sceneFields.toSet()) {
            "Light scene fields must exactly match ${product.wireValue}."
        }
        require(
            percents.values.all {
                it in DeviceLightRuntimeContract.Limit.PERCENT_MIN..
                    DeviceLightRuntimeContract.Limit.PERCENT_MAX
            }
        ) {
            "Light scene percentages must be integers from 0 through 100."
        }
    }

    fun toJson(): JSONObject = JSONObject().also { json ->
        product.sceneFields.forEach { field -> json.put(field, percents.getValue(field)) }
    }

    fun toTuple(timeMs: Long): JSONArray = JSONArray().put(timeMs).also { tuple ->
        product.sceneFields.forEach { field -> tuple.put(percents.getValue(field)) }
    }

    companion object {
        fun wrgb(red: Int, green: Int, blue: Int, white: Int): DeviceLightScene =
            DeviceLightScene(
                DeviceLightProduct.WRGB_PRO_ELITE,
                linkedMapOf(
                    "redPercent" to red,
                    "greenPercent" to green,
                    "bluePercent" to blue,
                    "whitePercent" to white
                )
            )

        fun rgb(red: Int, green: Int, blue: Int): DeviceLightScene =
            DeviceLightScene(
                DeviceLightProduct.RGB_PRO_SLIM,
                linkedMapOf(
                    "redPercent" to red,
                    "greenPercent" to green,
                    "bluePercent" to blue
                )
            )
    }
}

data class DeviceLightChannelDescriptor(
    val key: String,
    val percentField: String,
    val displayName: String,
    val displayColorRgb: Int,
    val order: Int
)

data class DeviceLightFeatures(
    val acclimation: Boolean,
    val fanControl: Boolean,
    val temperatureSensor: Boolean,
    val thermal: Boolean,
    val estimatedPower: Boolean,
    val estimatedColor: Boolean
)

data class DeviceLightScales(
    val acclimation: Int,
    val thermal: Int,
    val powerLimit: Int
)

data class DeviceLightElectricalDesign(
    val available: Boolean,
    val contractRevision: Int?,
    val fixtureLengthMm: Int?,
    val maximumFixtureInputPowerW: Double?,
    val minimumAdapterContinuousPowerW: Double?,
    val maximumLedElectricalPowerW: Double?,
    val nominalLedElectricalPowerW: Double?
)

data class DeviceLightPowerStatus(
    val available: Boolean,
    val source: DeviceLightModelSource,
    val estimatedLedPowerW: Double?,
    val estimatedFixturePowerAvailable: Boolean,
    val estimatedFixturePowerW: Double?,
    val hardLedPowerLimitW: Double?,
    val hardFixturePowerLimitAvailable: Boolean,
    val hardFixturePowerLimitW: Double?,
    val ratioAvailable: Boolean,
    val ratio: Double?,
    val limited: Boolean,
    val limitScale: Int,
    val limitBasis: DeviceLightPowerLimitBasis,
    val modelRevision: Int?
)

data class DeviceLightDisplayRgb(val red: Int, val green: Int, val blue: Int)

data class DeviceLightColorStatus(
    val available: Boolean,
    val source: DeviceLightModelSource,
    val cieX: Double?,
    val cieY: Double?,
    val cctAvailable: Boolean,
    val estimatedCctK: Int?,
    val duvAvailable: Boolean,
    val duv: Double?,
    val displayRgb: DeviceLightDisplayRgb?,
    val modelRevision: Int?
)

data class DeviceLightPreviewStatus(val active: Boolean, val remainingMs: Long)
data class DeviceLightManualStatus(val scene: DeviceLightScene)

data class DeviceLightAutoPolicy(
    val capacity: Int,
    val timeStepMs: Long,
    val rampDurationsMs: List<Long>
)

data class DeviceLightCustomPolicy(val maxPoints: Int, val timeStepMs: Long)

data class DeviceLightAcclimationPolicy(
    val supported: Boolean,
    val startPercentMin: Int?,
    val startPercentMax: Int?,
    val startPercentStep: Int?,
    val defaultStartPercent: Int?,
    val durationDaysMin: Int?,
    val durationDaysMax: Int?,
    val durationDaysStep: Int?,
    val defaultDurationDays: Int?,
    val targetPercent: Int?
)

data class DeviceLightPolicy(
    val auto: DeviceLightAutoPolicy,
    val custom: DeviceLightCustomPolicy,
    val acclimation: DeviceLightAcclimationPolicy
)

data class DeviceLightSchedulerStatus(
    val ready: Boolean,
    val reason: String,
    val generation: Long?,
    val localDate: String?,
    val currentWeekdayMask: Int,
    val currentTimeMs: Long?
)

data class DeviceLightAutoSummary(
    val revision: Long,
    val programCount: Int,
    val enabledCount: Int,
    val runtimeState: DeviceLightAutoRuntimeState,
    val activeProgramId: String?
)

data class DeviceLightCustomSummary(
    val revision: Long,
    val installed: Boolean,
    val weekdaysMask: Int,
    val pointCount: Int,
    val runtimeState: DeviceLightCustomRuntimeState
)

data class DeviceLightAcclimationStatus(
    val supported: Boolean,
    val revision: Long?,
    val state: DeviceLightAcclimationState?,
    val clockReady: Boolean?,
    val startPercent: Int?,
    val currentPermille: Int?,
    val targetPercent: Int?,
    val durationDays: Int?,
    val startedAtEpochSeconds: Long?,
    val endsAtEpochSeconds: Long?,
    val remainingSeconds: Long?
)

data class DeviceLightRuntimeStatus(
    val rtcReady: Boolean,
    val physicalChannelCount: Int,
    val physicalOutputHealthy: Boolean,
    val event: String
)

data class DeviceLightStatus(
    val schema: String,
    val storageVersion: Int,
    val product: DeviceLightProduct,
    val channelScale: Int,
    val channels: List<DeviceLightChannelDescriptor>,
    val features: DeviceLightFeatures,
    val mode: DeviceLightMode,
    val outputActive: Boolean,
    val outputReason: DeviceLightOutputReason,
    val requested: DeviceLightScene,
    val effective: DeviceLightScene,
    val scales: DeviceLightScales,
    val electricalDesign: DeviceLightElectricalDesign,
    val power: DeviceLightPowerStatus,
    val color: DeviceLightColorStatus,
    val preview: DeviceLightPreviewStatus,
    val manual: DeviceLightManualStatus,
    val policy: DeviceLightPolicy,
    val scheduler: DeviceLightSchedulerStatus,
    val auto: DeviceLightAutoSummary,
    val custom: DeviceLightCustomSummary,
    val acclimation: DeviceLightAcclimationStatus,
    val runtime: DeviceLightRuntimeStatus
)

data class DeviceLightControlSetPayload(val mode: DeviceLightMode) {
    fun toJson(): JSONObject = JSONObject().put(DeviceLightRuntimeContract.Field.MODE, mode.wireValue)
}

data class DeviceLightManualSetPayload(val scene: DeviceLightScene) {
    fun toJson(): JSONObject = JSONObject().put(DeviceLightRuntimeContract.Field.SCENE, scene.toJson())
}

data class DeviceLightAutoProgramCreatePayload(
    val expectedRevision: Long,
    val enabled: Boolean,
    val weekdaysMask: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val rampDurationMs: Long,
    val scene: DeviceLightScene
) {
    init { validateAutoProgram(expectedRevision, weekdaysMask, startTimeMs, endTimeMs, rampDurationMs) }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(DeviceLightRuntimeContract.Field.ENABLED, enabled)
        .put(DeviceLightRuntimeContract.Field.WEEKDAYS_MASK, weekdaysMask)
        .put(DeviceLightRuntimeContract.Field.START_TIME_MS, startTimeMs)
        .put(DeviceLightRuntimeContract.Field.END_TIME_MS, endTimeMs)
        .put(DeviceLightRuntimeContract.Field.RAMP_DURATION_MS, rampDurationMs)
        .put(DeviceLightRuntimeContract.Field.SCENE, scene.toJson())
}

data class DeviceLightAutoProgramUpdatePayload(
    val expectedRevision: Long,
    val programId: String,
    val weekdaysMask: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val rampDurationMs: Long,
    val scene: DeviceLightScene
) {
    init {
        requireProgramId(programId)
        validateAutoProgram(expectedRevision, weekdaysMask, startTimeMs, endTimeMs, rampDurationMs)
    }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(DeviceLightRuntimeContract.Field.PROGRAM_ID, programId)
        .put(DeviceLightRuntimeContract.Field.WEEKDAYS_MASK, weekdaysMask)
        .put(DeviceLightRuntimeContract.Field.START_TIME_MS, startTimeMs)
        .put(DeviceLightRuntimeContract.Field.END_TIME_MS, endTimeMs)
        .put(DeviceLightRuntimeContract.Field.RAMP_DURATION_MS, rampDurationMs)
        .put(DeviceLightRuntimeContract.Field.SCENE, scene.toJson())
}

data class DeviceLightAutoProgramEnabledSetPayload(
    val expectedRevision: Long,
    val programId: String,
    val enabled: Boolean
) {
    init { requireRevision(expectedRevision); requireProgramId(programId) }
    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(DeviceLightRuntimeContract.Field.PROGRAM_ID, programId)
        .put(DeviceLightRuntimeContract.Field.ENABLED, enabled)
}

data class DeviceLightAutoProgramDeletePayload(
    val expectedRevision: Long,
    val programId: String
) {
    init { requireRevision(expectedRevision); requireProgramId(programId) }
    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(DeviceLightRuntimeContract.Field.PROGRAM_ID, programId)
}

data class DeviceLightCustomPoint(val timeMs: Long, val scene: DeviceLightScene) {
    init { require(timeMs in 0..DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND) }
    fun toJsonTuple(): JSONArray = scene.toTuple(timeMs)
}

data class DeviceLightCustomInstallPayload(
    val expectedRevision: Long,
    val weekdaysMask: Int,
    val points: List<DeviceLightCustomPoint>
) {
    init {
        requireRevision(expectedRevision)
        require(
            weekdaysMask in DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MIN..
                DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MAX
        )
        require(points.size in 1..DeviceLightRuntimeContract.Limit.CUSTOM_POINT_CAPACITY)
        require(points.zipWithNext().all { (left, right) -> left.timeMs < right.timeMs })
        require(points.map { it.scene.product }.distinct().size == 1)
    }
    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(DeviceLightRuntimeContract.Field.WEEKDAYS_MASK, weekdaysMask)
        .put(
            DeviceLightRuntimeContract.Field.POINTS,
            JSONArray(points.map(DeviceLightCustomPoint::toJsonTuple))
        )
}

data class DeviceLightAcclimationStartPayload(
    val expectedRevision: Long,
    val startPercent: Int,
    val durationDays: Int
) {
    init {
        requireRevision(expectedRevision)
        require(
            startPercent in DeviceLightRuntimeContract.Limit.ACCLIMATION_START_PERCENT_MIN..
                DeviceLightRuntimeContract.Limit.ACCLIMATION_START_PERCENT_MAX &&
                startPercent % DeviceLightRuntimeContract.Limit.ACCLIMATION_START_PERCENT_STEP == 0
        )
        require(
            durationDays in DeviceLightRuntimeContract.Limit.ACCLIMATION_DURATION_DAYS_MIN..
                DeviceLightRuntimeContract.Limit.ACCLIMATION_DURATION_DAYS_MAX
        )
    }
    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(DeviceLightRuntimeContract.Field.START_PERCENT, startPercent)
        .put(DeviceLightRuntimeContract.Field.DURATION_DAYS, durationDays)
}

data class DeviceLightAcclimationStopPayload(val expectedRevision: Long) {
    init { requireRevision(expectedRevision) }
    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
}

sealed interface DeviceLightPreviewSetPayload {
    fun toJson(): JSONObject

    data class Scene(
        val scene: DeviceLightScene,
        val durationMs: Long? = null
    ) : DeviceLightPreviewSetPayload {
        init { durationMs?.let(::requirePreviewDuration) }
        override fun toJson(): JSONObject = JSONObject()
            .put(DeviceLightRuntimeContract.Field.SCENE, scene.toJson())
            .also { json -> durationMs?.let { json.put(DeviceLightRuntimeContract.Field.DURATION_MS, it) } }
    }

    data class VirtualTime(
        val virtualTimeMs: Long,
        val durationMs: Long? = null
    ) : DeviceLightPreviewSetPayload {
        init {
            require(virtualTimeMs in 0..DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND)
            durationMs?.let(::requirePreviewDuration)
        }
        override fun toJson(): JSONObject = JSONObject()
            .put(DeviceLightRuntimeContract.Field.VIRTUAL_TIME_MS, virtualTimeMs)
            .also { json -> durationMs?.let { json.put(DeviceLightRuntimeContract.Field.DURATION_MS, it) } }
    }
}

private fun validateAutoProgram(
    expectedRevision: Long,
    weekdaysMask: Int,
    startTimeMs: Long,
    endTimeMs: Long,
    rampDurationMs: Long
) {
    requireRevision(expectedRevision)
    require(
        weekdaysMask in DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MIN..
            DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MAX
    )
    require(startTimeMs in 0..DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND)
    require(endTimeMs in 0..DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND)
    require(startTimeMs != endTimeMs)
    require(rampDurationMs in ALLOWED_RAMP_DURATIONS_MS)
    val duration = if (endTimeMs > startTimeMs) {
        endTimeMs - startTimeMs
    } else {
        DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY - startTimeMs + endTimeMs
    }
    require(rampDurationMs * 2L <= duration)
}

private fun requireRevision(value: Long) =
    require(value in 0..DeviceLightRuntimeContract.Limit.UINT32_MAX)
private fun requireProgramId(value: String) = require(PROGRAM_ID.matches(value))
private fun requirePreviewDuration(value: Long) =
    require(value in 1..DeviceLightRuntimeContract.Limit.MAX_PREVIEW_DURATION_MS)

private fun <T> enumByWire(value: String, entries: Iterable<T>, wire: (T) -> String): T =
    entries.singleOrNull { wire(it) == value } ?: error("Unknown Light V1 enum value: $value")

private val ALLOWED_RAMP_DURATIONS_MS = setOf(
    DeviceLightRuntimeContract.Limit.RAMP_DISABLED_MS,
    DeviceLightRuntimeContract.Limit.RAMP_30_MINUTES_MS,
    DeviceLightRuntimeContract.Limit.RAMP_60_MINUTES_MS,
    DeviceLightRuntimeContract.Limit.RAMP_90_MINUTES_MS,
    DeviceLightRuntimeContract.Limit.RAMP_120_MINUTES_MS,
    DeviceLightRuntimeContract.Limit.RAMP_150_MINUTES_MS
)
private val PROGRAM_ID = Regex("^ap-[0-9a-f]{8}$")
