package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

data class DeviceLightManagedPlanPhase(
    val validFromEpochDay: Long,
    val validUntilEpochDayExclusive: Long?,
    val transitionDays: Int,
    val weekdaysMask: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val rampDurationMs: Long,
    val scene: DeviceLightScene
) {
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
    val nextTransitionEpochDay: Long?
)

data class DeviceLightManagedPlanDocument(
    val storageGeneration: Long,
    val revision: Long,
    val installed: Boolean,
    val planId: String?,
    val initialStartPercent: Int,
    val phaseCount: Int,
    val phases: List<DeviceLightManagedPlanPhase>,
    val runtime: DeviceLightManagedPlanRuntime,
    val event: String?
)

data class DeviceLightManagedPlanApplyPayload(
    val expectedRevision: Long,
    val expectedStorageGeneration: Long,
    val planId: String?,
    val initialStartPercent: Int,
    val phases: List<DeviceLightManagedPlanPhase>
) {
    init {
        require(expectedRevision in 0..DeviceLightRuntimeContract.Limit.UINT32_MAX)
        require(expectedStorageGeneration in 0..DeviceLightRuntimeContract.Limit.UINT32_MAX)
        require(planId == null || MANAGED_PLAN_ID.matches(planId))
        require(
            initialStartPercent in
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_INITIAL_START_PERCENT_MIN..
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_INITIAL_START_PERCENT_MAX &&
                initialStartPercent %
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_INITIAL_START_PERCENT_STEP == 0
        )
        DeviceLightManagedPlanValidation.requireValid(phases)
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
            JSONArray().also { array -> phases.forEach { phase -> array.put(phase.toJson()) } }
        )
}

data class DeviceLightManagedPlanDeletePayload(
    val expectedRevision: Long,
    val expectedStorageGeneration: Long,
    val planId: String
) {
    init {
        require(expectedRevision in 0..DeviceLightRuntimeContract.Limit.UINT32_MAX)
        require(expectedStorageGeneration in 0..DeviceLightRuntimeContract.Limit.UINT32_MAX)
        require(MANAGED_PLAN_ID.matches(planId))
    }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(
            DeviceLightRuntimeContract.Field.EXPECTED_STORAGE_GENERATION,
            expectedStorageGeneration
        )
        .put(DeviceLightRuntimeContract.Field.PLAN_ID, planId)
}

data class DeviceLightManagedPlanDeleteResult(
    val revision: Long,
    val storageGeneration: Long,
    val planId: String,
    val deleted: Boolean
)

internal object DeviceLightManagedPlanValidation {
    fun requireValid(phases: List<DeviceLightManagedPlanPhase>) {
        require(phases.size in 1..DeviceLightRuntimeContract.Limit.MANAGED_PLAN_PHASE_CAPACITY)
        require(phases.last().validUntilEpochDayExclusive == null)
        phases.forEachIndexed { index, phase ->
            require(
                phase.validFromEpochDay in
                    DeviceLightRuntimeContract.Limit.MANAGED_PLAN_MIN_EPOCH_DAY..
                    DeviceLightRuntimeContract.Limit.MANAGED_PLAN_MAX_EPOCH_DAY
            )
            phase.validUntilEpochDayExclusive?.let { end ->
                require(
                    end in phase.validFromEpochDay + 1..
                        DeviceLightRuntimeContract.Limit.MANAGED_PLAN_MAX_END_EPOCH_DAY
                )
                require(phase.transitionDays == 0 || phase.transitionDays < end - phase.validFromEpochDay)
            }
            require(
                phase.transitionDays in 0..
                    DeviceLightRuntimeContract.Limit.MANAGED_PLAN_TRANSITION_DAYS_MAX
            )
            require(
                phase.weekdaysMask in DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MIN..
                    DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MAX
            )
            require(phase.startTimeMs in 0..DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND)
            require(phase.endTimeMs in 0..DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND)
            require(phase.endTimeMs > phase.startTimeMs)
            require(phase.startTimeMs % DeviceLightRuntimeContract.Limit.SCHEDULE_TIME_STEP_MS == 0L)
            require(phase.endTimeMs % DeviceLightRuntimeContract.Limit.SCHEDULE_TIME_STEP_MS == 0L)
            require(phase.rampDurationMs in MANAGED_PLAN_RAMPS)
            require(phase.rampDurationMs * 2L <= phase.endTimeMs - phase.startTimeMs)
            if (index > 0) {
                require(
                    phases[index - 1].validUntilEpochDayExclusive == phase.validFromEpochDay
                )
                require(phases[index - 1].scene.product == phase.scene.product)
            }
        }
    }
}

internal val MANAGED_PLAN_ID = Regex("^lp-[0-9a-f]{8}$")

private val MANAGED_PLAN_RAMPS = setOf(
    DeviceLightRuntimeContract.Limit.RAMP_DISABLED_MS,
    DeviceLightRuntimeContract.Limit.RAMP_30_MINUTES_MS,
    DeviceLightRuntimeContract.Limit.RAMP_60_MINUTES_MS,
    DeviceLightRuntimeContract.Limit.RAMP_90_MINUTES_MS,
    DeviceLightRuntimeContract.Limit.RAMP_120_MINUTES_MS,
    DeviceLightRuntimeContract.Limit.RAMP_150_MINUTES_MS
)
