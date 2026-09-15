package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightManagedPlanContractTest {

    @Test
    fun `apply serializer contains both atomic preconditions and exact phase shape`() {
        val payload = DeviceLightManagedPlanApplyPayload(
            expectedRevision = 7,
            expectedStorageGeneration = 11,
            planId = null,
            initialStartPercent = 50,
            phases = listOf(phase(validFrom = 20_000, validUntil = null))
        ).toJson()

        assertKeys(
            payload,
            "expectedRevision",
            "expectedStorageGeneration",
            "planId",
            "initialStartPercent",
            "phases"
        )
        assertTrue(payload.isNull("planId"))
        assertKeys(
            payload.getJSONArray("phases").getJSONObject(0),
            "validFromEpochDay",
            "validUntilEpochDayExclusive",
            "transitionDays",
            "weekdaysMask",
            "startTimeMs",
            "endTimeMs",
            "rampDurationMs",
            "scene"
        )
    }

    @Test
    fun `strict Get parser accepts installed plan and rejects unknown fields`() {
        val parsed = DeviceLightManagedPlanParser.parseDocument(
            installedDocument(),
            DeviceLightProduct.WRGB_PRO_ELITE
        )

        assertTrue(parsed.installed)
        assertEquals("lp-00000001", parsed.planId)
        assertEquals(2, parsed.phases.size)
        assertEquals(DeviceLightManagedPlanRuntimeState.ACTIVE, parsed.runtime.state)
        assertEquals(1, parsed.runtime.activePhaseIndex)

        val malformed = installedDocument().put("androidOnlyField", true)
        assertTrue(
            runCatching {
                DeviceLightManagedPlanParser.parseDocument(
                    malformed,
                    DeviceLightProduct.WRGB_PRO_ELITE
                )
            }.isFailure
        )
    }

    @Test
    fun `strict Get parser accepts exact uninstalled representation`() {
        val parsed = DeviceLightManagedPlanParser.parseDocument(
            JSONObject()
                .put("storageGeneration", 4)
                .put("revision", 0)
                .put("installed", false)
                .put("planId", JSONObject.NULL)
                .put("initialStartPercent", 100)
                .put("phaseCount", 0)
                .put("phases", JSONArray())
                .put(
                    "runtime",
                    JSONObject()
                        .put("clockReady", true)
                        .put("state", "NOT_INSTALLED")
                        .put("activePhaseIndex", JSONObject.NULL)
                        .put("transitionPermille", JSONObject.NULL)
                        .put("nextTransitionEpochDay", JSONObject.NULL)
                ),
            DeviceLightProduct.WRGB_PRO_ELITE
        )

        assertFalse(parsed.installed)
        assertNull(parsed.planId)
        assertTrue(parsed.phases.isEmpty())
    }

    @Test
    fun `strict Get parser rejects impossible managed runtime shapes`() {
        val phases = listOf(phase(20_000, 20_030), phase(20_030, null))
        val beforePlan = installedDocument(phases).apply {
            getJSONObject("runtime")
                .put("state", "BEFORE_PLAN")
                .put("activePhaseIndex", JSONObject.NULL)
                .put("transitionPermille", JSONObject.NULL)
                .put("nextTransitionEpochDay", phases.first().validFromEpochDay)
        }
        val parsed = DeviceLightManagedPlanParser.parseDocument(
            beforePlan,
            DeviceLightProduct.WRGB_PRO_ELITE
        )
        assertEquals(DeviceLightManagedPlanRuntimeState.BEFORE_PLAN, parsed.runtime.state)

        val wrongBoundary = JSONObject(beforePlan.toString()).apply {
            getJSONObject("runtime").put("nextTransitionEpochDay", 20_001)
        }
        assertTrue(parseFails(wrongBoundary))

        val blockedWithClock = installedDocument(phases).apply {
            getJSONObject("runtime")
                .put("state", "RTC_BLOCKED")
                .put("activePhaseIndex", JSONObject.NULL)
                .put("transitionPermille", JSONObject.NULL)
                .put("nextTransitionEpochDay", JSONObject.NULL)
        }
        assertTrue(parseFails(blockedWithClock))

        val activeWrongEnd = installedDocument(phases).apply {
            getJSONObject("runtime").put("nextTransitionEpochDay", 20_031)
        }
        assertTrue(parseFails(activeWrongEnd))
    }

    @Test
    fun `managed graph projection keeps plan id and phase index`() {
        val graph = DeviceLightMutationParser.Graph.parseGraph(
            JSONObject()
                .put("mode", "AUTO")
                .put("available", true)
                .put("reason", "OK")
                .put("sourceRevision", 8)
                .put("schedulerGeneration", 12)
                .put("localDate", "2026-09-15")
                .put("currentWeekdayMask", 32)
                .put("nowTimeMs", 43_200_000)
                .put("basis", "MANAGED_PLAN")
                .put("channelScale", 1_000)
                .put("hasScheduleToday", true)
                .put(
                    "points",
                    JSONArray().put(
                        JSONArray(listOf(36_000_000, 0, 0, 0, 0))
                    )
                )
                .put("autoSpans", JSONArray())
                .put(
                    "planSpans",
                    JSONArray().put(
                        JSONArray(listOf(36_000_000, 64_800_000, "lp-00000001", 1))
                    )
                ),
            DeviceLightProduct.WRGB_PRO_ELITE
        )

        assertEquals(DeviceLightGraphBasis.MANAGED_PLAN, graph.basis)
        assertTrue(graph.autoSpans.isEmpty())
        assertEquals("lp-00000001", graph.planSpans.single().planId)
        assertEquals(1, graph.planSpans.single().phaseIndex)
    }

    @Test
    fun `stale storage generation retains exact authoritative value`() {
        val error = DeviceRuntimeCommandOutcome.FirmwareError(
            deviceUid = DeviceUid("AQL-LIGHT-PLAN"),
            module = DeviceLightRuntimeContract.MODULE,
            action = DeviceLightRuntimeContract.Action.AUTO_PLAN_APPLY,
            messageId = "message-1",
            generation = DeviceRuntimeConnectionGeneration(1),
            statusCode = 409,
            code = "conflict",
            field = "expectedStorageGeneration",
            message = "stale storage generation",
            structuredDataJson =
                """{"reason":"STALE_STORAGE_GENERATION","actualStorageGeneration":19}"""
        )

        val parsed = error.lightV1Data()
        assertEquals(DeviceLightErrorReason.STALE_STORAGE_GENERATION, parsed.reason)
        assertEquals(19L, parsed.actualStorageGeneration)
        assertNull(parsed.actualRevision)
    }

    @Test
    fun `maximum compact apply and Get data stay within firmware ceiling`() {
        val phases = List(DeviceLightRuntimeContract.Limit.MANAGED_PLAN_PHASE_CAPACITY) { index ->
            val start = DeviceLightRuntimeContract.Limit.MANAGED_PLAN_MAX_EPOCH_DAY - 7 + index
            phase(
                validFrom = start,
                validUntil = if (index == 7) null else start + 1,
                transitionDays = 0,
                scene = DeviceLightScene.wrgb(100, 100, 100, 100)
            )
        }
        val apply = DeviceLightManagedPlanApplyPayload(
            expectedRevision = DeviceLightRuntimeContract.Limit.UINT32_MAX,
            expectedStorageGeneration = DeviceLightRuntimeContract.Limit.UINT32_MAX,
            planId = "lp-ffffffff",
            initialStartPercent = 100,
            phases = phases
        ).toJson()
        val response = installedDocument(phases)

        assertTrue(apply.compactSize() <= DECODED_DATA_LIMIT_BYTES)
        assertTrue(response.compactSize() <= DECODED_DATA_LIMIT_BYTES)
    }

    private fun installedDocument(
        phases: List<DeviceLightManagedPlanPhase> = listOf(
            phase(20_000, 20_030),
            phase(20_030, null)
        )
    ): JSONObject = JSONObject()
        .put("storageGeneration", 11)
        .put("revision", 8)
        .put("installed", true)
        .put("planId", "lp-00000001")
        .put("initialStartPercent", 50)
        .put("phaseCount", phases.size)
        .put(
            "phases",
            JSONArray().also { array -> phases.forEach { item -> array.put(item.toJson()) } }
        )
        .put(
            "runtime",
            JSONObject()
                .put("clockReady", true)
                .put("state", "ACTIVE")
                .put("activePhaseIndex", phases.lastIndex)
                .put("transitionPermille", 1_000)
                .put("nextTransitionEpochDay", JSONObject.NULL)
        )

    private fun phase(
        validFrom: Long,
        validUntil: Long?,
        transitionDays: Int = 7,
        scene: DeviceLightScene = DeviceLightScene.wrgb(60, 55, 65, 70)
    ) = DeviceLightManagedPlanPhase(
        validFromEpochDay = validFrom,
        validUntilEpochDayExclusive = validUntil,
        transitionDays = transitionDays,
        weekdaysMask = 127,
        startTimeMs = 36_000_000,
        endTimeMs = 64_800_000,
        rampDurationMs = 3_600_000,
        scene = scene
    )

    private fun JSONObject.compactSize(): Int =
        toString().toByteArray(StandardCharsets.UTF_8).size

    private fun parseFails(document: JSONObject): Boolean = runCatching {
        DeviceLightManagedPlanParser.parseDocument(
            document,
            DeviceLightProduct.WRGB_PRO_ELITE
        )
    }.isFailure

    private fun assertKeys(json: JSONObject, vararg keys: String) {
        assertEquals(keys.toSet(), json.keySet())
    }

    private companion object {
        const val DECODED_DATA_LIMIT_BYTES = 4_096
    }
}
