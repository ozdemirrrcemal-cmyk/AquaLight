package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONObject

internal object DeviceLightManagedPlanParser {
    fun parseDocument(
        data: JSONObject,
        product: DeviceLightProduct
    ): DeviceLightManagedPlanDocument {
        requireBaseOrEventKeys(data, DOCUMENT_KEYS, "light.auto.plan data")
        val phaseData = data.requireLightArray("phases")
        val phases = List(phaseData.length()) { index ->
            parsePhase(phaseData.requireLightObject(index), product, index)
        }
        val result = DeviceLightManagedPlanDocument(
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
        require((result.planId != null) == result.installed)
        result.planId?.let { id -> require(MANAGED_PLAN_ID.matches(id)) }
        if (result.installed) {
            DeviceLightManagedPlanValidation.requireValid(phases)
            requireInstalledRuntimeShape(result.runtime, phases)
        } else {
            require(phases.isEmpty())
            require(result.runtime.state == DeviceLightManagedPlanRuntimeState.NOT_INSTALLED)
            require(result.runtime.activePhaseIndex == null)
            require(result.runtime.transitionPermille == null)
            require(result.runtime.nextTransitionEpochDay == null)
        }
        return result
    }

    private fun requireInstalledRuntimeShape(
        runtime: DeviceLightManagedPlanRuntime,
        phases: List<DeviceLightManagedPlanPhase>
    ) {
        when (runtime.state) {
            DeviceLightManagedPlanRuntimeState.NOT_INSTALLED -> error(
                "An installed managed plan cannot report NOT_INSTALLED."
            )
            DeviceLightManagedPlanRuntimeState.NOT_SELECTED -> {
                requireNoActiveRuntimeFields(runtime)
            }
            DeviceLightManagedPlanRuntimeState.RTC_BLOCKED -> {
                require(!runtime.clockReady)
                requireNoActiveRuntimeFields(runtime)
            }
            DeviceLightManagedPlanRuntimeState.BEFORE_PLAN -> {
                require(runtime.clockReady)
                require(runtime.activePhaseIndex == null)
                require(runtime.transitionPermille == null)
                require(runtime.nextTransitionEpochDay == phases.first().validFromEpochDay)
            }
            DeviceLightManagedPlanRuntimeState.ACTIVE -> {
                require(runtime.clockReady)
                val index = requireNotNull(runtime.activePhaseIndex)
                require(index in phases.indices)
                require(runtime.transitionPermille != null)
                require(
                    runtime.nextTransitionEpochDay ==
                        phases[index].validUntilEpochDayExclusive
                )
            }
        }
    }

    private fun requireNoActiveRuntimeFields(runtime: DeviceLightManagedPlanRuntime) {
        require(runtime.activePhaseIndex == null)
        require(runtime.transitionPermille == null)
        require(runtime.nextTransitionEpochDay == null)
    }

    fun parseDelete(data: JSONObject): DeviceLightManagedPlanDeleteResult {
        data.requireLightKeys(DELETE_KEYS, "light.auto.plan.delete.data")
        val planId = data.requireLightText("planId")
        require(MANAGED_PLAN_ID.matches(planId))
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
            planId = planId,
            deleted = data.requireLightBoolean("deleted").also { deleted -> require(deleted) }
        )
    }

    private fun parsePhase(
        data: JSONObject,
        product: DeviceLightProduct,
        index: Int
    ): DeviceLightManagedPlanPhase {
        data.requireLightKeys(PHASE_KEYS, "light.auto.plan.phases[$index]")
        return DeviceLightManagedPlanPhase(
            validFromEpochDay = data.requireLightLong(
                "validFromEpochDay",
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_MIN_EPOCH_DAY,
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_MAX_EPOCH_DAY
            ),
            validUntilEpochDayExclusive = data.requireNullableLightLong(
                "validUntilEpochDayExclusive",
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_MIN_EPOCH_DAY + 1,
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_MAX_END_EPOCH_DAY
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
            rampDurationMs = data.requireLightLong(
                "rampDurationMs",
                0,
                DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY
            ),
            scene = DeviceLightV1JsonParser.parseScene(
                data.requireLightObject("scene"),
                product,
                "light.auto.plan.phases[$index].scene"
            )
        )
    }

    private fun parseRuntime(data: JSONObject): DeviceLightManagedPlanRuntime {
        data.requireLightKeys(RUNTIME_KEYS, "light.auto.plan.runtime")
        val result = DeviceLightManagedPlanRuntime(
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
            nextTransitionEpochDay = data.requireNullableLightLong(
                "nextTransitionEpochDay",
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_MIN_EPOCH_DAY,
                DeviceLightRuntimeContract.Limit.MANAGED_PLAN_MAX_EPOCH_DAY
            )
        )
        require((result.activePhaseIndex != null) == (result.transitionPermille != null))
        return result
    }

    private fun requireBaseOrEventKeys(data: JSONObject, base: Set<String>, label: String) {
        val actual = data.keys().asSequence().toSet()
        require(actual == base || actual == base + "event") {
            "$label keys differ from the firmware contract."
        }
    }

    private fun parseOptionalEvent(data: JSONObject): String? = if (data.has("event")) {
        data.requireLightText("event").also { event ->
            require(event == DeviceLightRuntimeContract.Event.STATUS_CHANGED)
        }
    } else {
        null
    }

    private val DOCUMENT_KEYS = setOf(
        "storageGeneration",
        "revision",
        "installed",
        "planId",
        "initialStartPercent",
        "phaseCount",
        "phases",
        "runtime"
    )
    private val PHASE_KEYS = setOf(
        "validFromEpochDay",
        "validUntilEpochDayExclusive",
        "transitionDays",
        "weekdaysMask",
        "startTimeMs",
        "endTimeMs",
        "rampDurationMs",
        "scene"
    )
    private val RUNTIME_KEYS = setOf(
        "clockReady",
        "state",
        "activePhaseIndex",
        "transitionPermille",
        "nextTransitionEpochDay"
    )
    private val DELETE_KEYS = setOf("revision", "storageGeneration", "planId", "deleted")
}
