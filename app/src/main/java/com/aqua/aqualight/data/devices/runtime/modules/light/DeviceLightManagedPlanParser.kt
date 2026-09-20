package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONObject

internal object DeviceLightManagedPlanParser {
    fun parsePlan(
        data: JSONObject,
        product: DeviceLightProduct
    ): DeviceLightManagedAutoPlan {
        data.requireLightKeys(PLAN_KEYS, "light.auto.plan data")
        val phaseData = data.requireLightArray("phases")
        val phases = List(phaseData.length()) { index ->
            parsePhase(phaseData.requireLightObject(index), product)
        }
        val result = DeviceLightManagedAutoPlan(
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
            runtime = parseRuntime(data.requireLightObject("runtime"))
        )
        require(phases.all { phase -> phase.scene.product == product })
        return result
    }

    fun parseDelete(data: JSONObject): DeviceLightManagedAutoPlanDeleteResult {
        data.requireLightKeys(DELETE_KEYS, "light.auto.plan.delete.data")
        val planId = data.requireLightText("planId").also(::requireLightManagedPlanId)
        return DeviceLightManagedAutoPlanDeleteResult(
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
            planId = planId,
            deleted = data.requireLightBoolean("deleted").also { require(it) }
        )
    }

    private fun parsePhase(
        data: JSONObject,
        product: DeviceLightProduct
    ): DeviceLightManagedPlanPhase {
        data.requireLightKeys(PHASE_KEYS, "Light managed plan phase")
        return DeviceLightManagedPlanPhase(
            validFromEpochDay = data.requireLightInt(
                "validFromEpochDay",
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_EPOCH_DAY_MIN,
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_EPOCH_DAY_MAX
            ),
            validUntilEpochDayExclusive = data.requireNullableLightInt(
                "validUntilEpochDayExclusive",
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_EPOCH_DAY_MIN + 1,
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_END_EPOCH_DAY_MAX
            ),
            transitionDays = data.requireLightInt(
                "transitionDays",
                0,
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_TRANSITION_DAYS_MAX
            ),
            weekdaysMask = data.requireLightInt(
                "weekdaysMask",
                DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MIN,
                DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MAX
            ),
            startTimeMs = data.requireLightLong(
                "startTimeMs",
                0,
                DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND
            ),
            endTimeMs = data.requireLightLong(
                "endTimeMs",
                0,
                DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND
            ),
            rampDurationMs = data.requireLightLong("rampDurationMs", 0),
            scene = DeviceLightV1JsonParser.parseScene(
                data.requireLightObject("scene"),
                product,
                "Light managed plan phase scene"
            )
        )
    }

    private fun parseRuntime(data: JSONObject): DeviceLightManagedPlanRuntime {
        data.requireLightKeys(RUNTIME_KEYS, "light.auto.plan.runtime")
        return DeviceLightManagedPlanRuntime(
            clockReady = data.requireLightBoolean("clockReady"),
            state = DeviceLightManagedPlanRuntimeState.fromWireExact(
                data.requireLightText("state")
            ),
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
            nextTransitionEpochDay = data.requireNullableLightInt(
                "nextTransitionEpochDay",
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_EPOCH_DAY_MIN,
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_END_EPOCH_DAY_MAX
            )
        )
    }

    private val PLAN_KEYS = setOf(
        "storageGeneration", "revision", "installed", "planId", "initialStartPercent",
        "phaseCount", "phases", "runtime"
    )
    private val PHASE_KEYS = setOf(
        "validFromEpochDay", "validUntilEpochDayExclusive", "transitionDays",
        "weekdaysMask", "startTimeMs", "endTimeMs", "rampDurationMs", "scene"
    )
    private val RUNTIME_KEYS = setOf(
        "clockReady", "state", "activePhaseIndex", "transitionPermille",
        "nextTransitionEpochDay"
    )
    private val DELETE_KEYS = setOf(
        "revision", "storageGeneration", "planId", "deleted"
    )
}
