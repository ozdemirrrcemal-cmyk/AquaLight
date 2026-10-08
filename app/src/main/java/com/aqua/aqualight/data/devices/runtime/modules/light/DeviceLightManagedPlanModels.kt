package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceLightAutoScheduleSource(val wireValue: String) {
    PROGRAMS("PROGRAMS"),
    MANAGED_PLAN("MANAGED_PLAN");

    companion object {
        fun fromWireExact(value: String): DeviceLightAutoScheduleSource =
            entries.singleOrNull { it.wireValue == value }
                ?: error("Unknown Light AUTO schedule source: $value")
    }
}

enum class DeviceLightManagedPlanRuntimeState(val wireValue: String) {
    NOT_INSTALLED("NOT_INSTALLED"),
    NOT_SELECTED("NOT_SELECTED"),
    RTC_BLOCKED("RTC_BLOCKED"),
    BEFORE_PLAN("BEFORE_PLAN"),
    ACTIVE("ACTIVE");

    companion object {
        fun fromWireExact(value: String): DeviceLightManagedPlanRuntimeState =
            entries.singleOrNull { it.wireValue == value }
                ?: error("Unknown Light managed plan runtime state: $value")
    }
}

data class DeviceLightManagedPlanPhase(
    val validFromEpochDay: Int,
    val validUntilEpochDayExclusive: Int?,
    val transitionDays: Int,
    val weekdaysMask: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val rampDurationMs: Long,
    val scene: DeviceLightScene
) {
    init {
        requireManagedPlanPhase(this)
    }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.VALID_FROM_EPOCH_DAY, validFromEpochDay)
        .put(
            DeviceLightRuntimeContract.Field.VALID_UNTIL_EPOCH_DAY_EXCLUSIVE,
            validUntilEpochDayExclusive ?: JSONObject.NULL
        )
        .put(DeviceLightRuntimeContract.Field.TRANSITION_DAYS, transitionDays)
        .put(DeviceLightRuntimeContract.Field.WEEKDAYS_MASK, weekdaysMask)
        .put(DeviceLightRuntimeContract.Field.START_TIME_MS, startTimeMs)
        .put(DeviceLightRuntimeContract.Field.END_TIME_MS, endTimeMs)
        .put(DeviceLightRuntimeContract.Field.RAMP_DURATION_MS, rampDurationMs)
        .put(DeviceLightRuntimeContract.Field.SCENE, scene.toJson())
}

data class DeviceLightManagedPlanRuntime(
    val clockReady: Boolean,
    val state: DeviceLightManagedPlanRuntimeState,
    val activePhaseIndex: Int?,
    val transitionPermille: Int?,
    val nextTransitionEpochDay: Int?
) {
    init {
        activePhaseIndex?.let {
            require(it in 0 until DeviceLightRuntimeContract.Limit.MANAGED_PLAN_PHASE_CAPACITY)
        }
        transitionPermille?.let {
            require(
                it in DeviceLightRuntimeContract.Limit.PERMILLE_MIN..
                    DeviceLightRuntimeContract.Limit.PERMILLE_MAX
            )
        }
        nextTransitionEpochDay?.let {
            require(
                it in DeviceLightRuntimeContract.Limit.MANAGED_PLAN_EPOCH_DAY_MIN..
                    DeviceLightRuntimeContract.Limit.MANAGED_PLAN_END_EPOCH_DAY_MAX
            )
        }
        when (state) {
            DeviceLightManagedPlanRuntimeState.ACTIVE -> {
                require(activePhaseIndex != null)
                require(transitionPermille != null)
            }
            DeviceLightManagedPlanRuntimeState.BEFORE_PLAN -> {
                require(activePhaseIndex == null)
                require(transitionPermille == null)
                require(nextTransitionEpochDay != null)
            }
            DeviceLightManagedPlanRuntimeState.NOT_INSTALLED,
            DeviceLightManagedPlanRuntimeState.NOT_SELECTED,
            DeviceLightManagedPlanRuntimeState.RTC_BLOCKED -> {
                require(activePhaseIndex == null)
                require(transitionPermille == null)
                require(nextTransitionEpochDay == null)
            }
        }
    }
}

data class DeviceLightManagedAutoPlan(
    val storageGeneration: Long,
    val revision: Long,
    val installed: Boolean,
    val planId: String?,
    val initialStartPercent: Int,
    val phaseCount: Int,
    val phases: List<DeviceLightManagedPlanPhase>,
    val runtime: DeviceLightManagedPlanRuntime
) {
    init {
        requireUint32(storageGeneration)
        requireUint32(revision)
        require(phaseCount == phases.size)
        if (installed) {
            requireNotNull(planId).also(::requireLightManagedPlanId)
            requireManagedPlanDocument(initialStartPercent, phases)
        } else {
            require(planId == null)
            require(phaseCount == 0)
            require(phases.isEmpty())
            require(
                initialStartPercent ==
                    DeviceLightRuntimeContract.Limit.MANAGED_PLAN_INITIAL_START_PERCENT_DEFAULT
            )
            require(runtime.state == DeviceLightManagedPlanRuntimeState.NOT_INSTALLED)
        }
    }
}

data class DeviceLightManagedAutoPlanApplyPayload(
    val expectedRevision: Long,
    val expectedStorageGeneration: Long,
    val planId: String?,
    val initialStartPercent: Int,
    val phases: List<DeviceLightManagedPlanPhase>
) {
    init {
        requireUint32(expectedRevision)
        requireUint32(expectedStorageGeneration)
        planId?.let(::requireLightManagedPlanId)
        requireManagedPlanDocument(initialStartPercent, phases)
    }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(
            DeviceLightRuntimeContract.Field.EXPECTED_STORAGE_GENERATION,
            expectedStorageGeneration
        )
        .put(DeviceLightRuntimeContract.Field.PLAN_ID, planId ?: JSONObject.NULL)
        .put(DeviceLightRuntimeContract.Field.INITIAL_START_PERCENT, initialStartPercent)
        .put(
            DeviceLightRuntimeContract.Field.PHASES,
            JSONArray(phases.map(DeviceLightManagedPlanPhase::toJson))
        )
}

data class DeviceLightManagedAutoPlanDeletePayload(
    val expectedRevision: Long,
    val expectedStorageGeneration: Long,
    val planId: String
) {
    init {
        requireUint32(expectedRevision)
        requireUint32(expectedStorageGeneration)
        requireLightManagedPlanId(planId)
    }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(
            DeviceLightRuntimeContract.Field.EXPECTED_STORAGE_GENERATION,
            expectedStorageGeneration
        )
        .put(DeviceLightRuntimeContract.Field.PLAN_ID, planId)
}

data class DeviceLightManagedAutoPlanDeleteResult(
    val revision: Long,
    val storageGeneration: Long,
    val planId: String,
    val deleted: Boolean
)

internal fun requireLightManagedPlanId(value: String) {
    require(MANAGED_PLAN_ID.matches(value)) { "Invalid Light managed planId." }
}

private fun requireManagedPlanDocument(
    initialStartPercent: Int,
    phases: List<DeviceLightManagedPlanPhase>
) {
    require(
        initialStartPercent in
            DeviceLightRuntimeContract.Limit.MANAGED_PLAN_INITIAL_START_PERCENT_MIN..
            DeviceLightRuntimeContract.Limit.MANAGED_PLAN_INITIAL_START_PERCENT_MAX
    )
    require(
        initialStartPercent %
            DeviceLightRuntimeContract.Limit.MANAGED_PLAN_INITIAL_START_PERCENT_STEP == 0
    )
    require(phases.size in 1..DeviceLightRuntimeContract.Limit.MANAGED_PLAN_PHASE_CAPACITY)
    require(phases.map { phase -> phase.scene.product }.distinct().size == 1)
    phases.zipWithNext().forEach { (previous, next) ->
        require(previous.validUntilEpochDayExclusive == next.validFromEpochDay)
    }
    require(phases.last().validUntilEpochDayExclusive == null)
}

private fun requireManagedPlanPhase(phase: DeviceLightManagedPlanPhase) {
    require(
        phase.validFromEpochDay in
            DeviceLightRuntimeContract.Limit.MANAGED_PLAN_EPOCH_DAY_MIN..
            DeviceLightRuntimeContract.Limit.MANAGED_PLAN_EPOCH_DAY_MAX
    )
    phase.validUntilEpochDayExclusive?.let { end ->
        require(
            end in (phase.validFromEpochDay + 1)..
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_END_EPOCH_DAY_MAX
        )
        require(
            phase.transitionDays == 0 ||
                phase.transitionDays < end - phase.validFromEpochDay
        )
    }
    require(
        phase.transitionDays in 0..
            DeviceLightRuntimeContract.Limit.MANAGED_PLAN_TRANSITION_DAYS_MAX
    )
    require(
        phase.weekdaysMask in DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MIN..
            DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MAX
    )
    require(
        phase.startTimeMs in 0..DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND &&
            phase.startTimeMs % DeviceLightRuntimeContract.Limit.SCHEDULE_TIME_STEP_MS == 0L
    )
    require(
        phase.endTimeMs in 0..DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND &&
            phase.endTimeMs % DeviceLightRuntimeContract.Limit.SCHEDULE_TIME_STEP_MS == 0L
    )
    require(phase.endTimeMs > phase.startTimeMs)
    require(phase.rampDurationMs in MANAGED_PLAN_RAMPS)
    require(phase.rampDurationMs * 2L <= phase.endTimeMs - phase.startTimeMs)
}

private fun requireUint32(value: Long) {
    require(value in 0..DeviceLightRuntimeContract.Limit.UINT32_MAX)
}

private val MANAGED_PLAN_RAMPS = setOf(
    DeviceLightRuntimeContract.Limit.RAMP_DISABLED_MS,
    DeviceLightRuntimeContract.Limit.RAMP_30_MINUTES_MS,
    DeviceLightRuntimeContract.Limit.RAMP_60_MINUTES_MS,
    DeviceLightRuntimeContract.Limit.RAMP_90_MINUTES_MS,
    DeviceLightRuntimeContract.Limit.RAMP_120_MINUTES_MS,
    DeviceLightRuntimeContract.Limit.RAMP_150_MINUTES_MS
)
private val MANAGED_PLAN_ID = Regex("^lp-[0-9a-f]{8}$")
