package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightV1ContractTest {
    @Test
    fun `Light data layer pins the merged firmware main revision`() {
        assertEquals(
            "455298833668537fedc16b851067558815d2cc7b",
            DeviceLightRuntimeContract.PINNED_FIRMWARE_COMMIT
        )
    }

    @Test
    fun `both product status documents use one strict Light V1 parser`() {
        val wrgb = DeviceLightStatusParser.parse(
            DeviceLightRuntimeFixtures.status(DeviceLightProduct.WRGB_PRO_ELITE)
        )
        val rgb = DeviceLightStatusParser.parse(
            DeviceLightRuntimeFixtures.status(DeviceLightProduct.RGB_PRO_SLIM)
        )

        assertEquals(4, wrgb.channels.size)
        assertTrue(wrgb.features.acclimation)
        assertTrue(wrgb.power.available)
        assertTrue(wrgb.color.available)
        assertEquals(3, rgb.channels.size)
        assertFalse(rgb.features.acclimation)
        assertFalse(rgb.power.available)
        assertFalse(rgb.color.available)
        assertNull(rgb.acclimation.state)

        assertTrue(
            runCatching {
                DeviceLightStatusParser.parse(
                    DeviceLightRuntimeFixtures.status().put("moonlight", true)
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                val status = DeviceLightRuntimeFixtures.status(DeviceLightProduct.RGB_PRO_SLIM)
                status.getJSONObject("features").put("acclimation", true)
                DeviceLightStatusParser.parse(status)
            }.isFailure
        )
    }

    @Test
    fun `every Light V1 request serializer emits exact firmware keys and tuple width`() {
        val wrgb = DeviceLightScene.wrgb(10, 20, 30, 40)
        val rgb = DeviceLightScene.rgb(10, 20, 30)
        assertProgramSerializerKeys(wrgb, rgb)
        assertManagedPlanSerializerKeys(wrgb)
        assertCustomAndPreviewSerializerKeys(wrgb, rgb)
    }

    private fun assertProgramSerializerKeys(
        wrgb: DeviceLightScene,
        rgb: DeviceLightScene
    ) {
        assertKeys(DeviceLightControlSetPayload(DeviceLightMode.AUTO).toJson(), "mode")
        assertKeys(DeviceLightManualSetPayload(wrgb).toJson(), "scene")
        assertKeys(wrgb.toJson(), "redPercent", "greenPercent", "bluePercent", "whitePercent")
        assertKeys(rgb.toJson(), "redPercent", "greenPercent", "bluePercent")

        assertKeys(autoCreate(wrgb).toJson(), *AUTO_CREATE_FIELDS)
        assertKeys(autoUpdate(wrgb).toJson(), *AUTO_UPDATE_FIELDS)
        assertKeys(
            DeviceLightAutoProgramEnabledSetPayload(4, "ap-00000001", true).toJson(),
            "expectedRevision",
            "programId",
            "enabled"
        )
        assertKeys(
            DeviceLightAutoProgramDeletePayload(4, "ap-00000001").toJson(),
            "expectedRevision",
            "programId"
        )
    }

    private fun assertManagedPlanSerializerKeys(wrgb: DeviceLightScene) {
        val managedPhase = DeviceLightManagedPlanPhase(
            validFromEpochDay = 20_000,
            validUntilEpochDayExclusive = null,
            transitionDays = 7,
            weekdaysMask = 127,
            startTimeMs = 57_600_000,
            endTimeMs = 79_200_000,
            rampDurationMs = 3_600_000,
            scene = wrgb
        )
        val managedApply = DeviceLightManagedPlanApplyPayload(
            expectedRevision = 2,
            expectedStorageGeneration = 4,
            planId = null,
            initialStartPercent = 60,
            phases = listOf(managedPhase)
        ).toJson()
        assertKeys(
            managedApply,
            "expectedRevision",
            "expectedStorageGeneration",
            "planId",
            "initialStartPercent",
            "phases"
        )
        assertTrue(managedApply.isNull("planId"))
        assertKeys(
            managedApply.getJSONArray("phases").getJSONObject(0),
            "validFromEpochDay",
            "validUntilEpochDayExclusive",
            "transitionDays",
            "weekdaysMask",
            "startTimeMs",
            "endTimeMs",
            "rampDurationMs",
            "scene"
        )
        assertKeys(
            DeviceLightManagedPlanDeletePayload(2, 4, "lp-00000001").toJson(),
            "expectedRevision",
            "expectedStorageGeneration",
            "planId"
        )
    }

    private fun assertCustomAndPreviewSerializerKeys(
        wrgb: DeviceLightScene,
        rgb: DeviceLightScene
    ) {
        val wrgbCustom = DeviceLightCustomInstallPayload(
            expectedRevision = 2,
            weekdaysMask = 127,
            points = listOf(DeviceLightCustomPoint(0, wrgb))
        ).toJson()
        val rgbCustom = DeviceLightCustomInstallPayload(
            expectedRevision = 2,
            weekdaysMask = 127,
            points = listOf(DeviceLightCustomPoint(0, rgb))
        ).toJson()
        assertKeys(wrgbCustom, "expectedRevision", "weekdaysMask", "points")
        assertEquals(5, wrgbCustom.getJSONArray("points").getJSONArray(0).length())
        assertEquals(4, rgbCustom.getJSONArray("points").getJSONArray(0).length())

        assertKeys(
            DeviceLightAcclimationStartPayload(1, 50, 30).toJson(),
            "expectedRevision",
            "startPercent",
            "durationDays"
        )
        assertKeys(DeviceLightAcclimationStopPayload(1).toJson(), "expectedRevision")
        assertKeys(
            DeviceLightPreviewSetPayload.Scene(wrgb, durationMs = 3_000).toJson(),
            "scene",
            "durationMs"
        )
        assertKeys(
            DeviceLightPreviewSetPayload.VirtualTime(43_200_000).toJson(),
            "virtualTimeMs"
        )
    }

    @Test
    fun `RGB Slim acclimation commands fail closed before gateway`() = runBlocking {
        val gateway = RejectingGateway()
        val stateStore = DeviceLightRuntimeStateStore()
        stateStore.beginGeneration(DEVICE_UID, GENERATION)
        stateStore.recordStatus(
            DEVICE_UID,
            GENERATION,
            DeviceLightStatusParser.parse(
                DeviceLightRuntimeFixtures.status(DeviceLightProduct.RGB_PRO_SLIM)
            )
        )
        val repository = DeviceLightRuntimeRepository(gateway, stateStore)

        val status = repository.requestAcclimationStatus(DEVICE_UID)
        val start = repository.startAcclimation(
            DEVICE_UID,
            DeviceLightAcclimationStartPayload(0, 50, 30)
        )
        val stop = repository.stopAcclimation(
            DEVICE_UID,
            DeviceLightAcclimationStopPayload(0)
        )

        assertTrue(status is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertTrue(start is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertTrue(stop is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertEquals(0, gateway.calls)
    }

    @Test
    fun `structured Light firmware errors retain every contract field`() {
        val error = DeviceRuntimeCommandOutcome.FirmwareError(
            deviceUid = DEVICE_UID,
            module = "light",
            action = "auto.program.create",
            messageId = "error-1",
            generation = GENERATION,
            statusCode = 409,
            code = "conflict",
            field = "",
            message = "AUTO program overlaps",
            structuredDataJson = """
                {
                  "reason":"AUTO_PROGRAM_OVERLAP",
                  "actualRevision":7,
                  "conflict":{
                    "withProgramId":"ap-00000001",
                    "occurrenceWeekdayMask":64,
                    "overlapStartTimeMs":3600000,
                    "overlapEndTimeMs":7200000,
                    "existing":{"weekdaysMask":64,"startTimeMs":0,"endTimeMs":7200000},
                    "candidate":{"weekdaysMask":64,"startTimeMs":3600000,"endTimeMs":10800000}
                  },
                  "additionalConflictCount":2
                }
            """.trimIndent()
        )

        val parsed = error.lightV1Data()
        assertEquals(DeviceLightErrorReason.AUTO_PROGRAM_OVERLAP, parsed.reason)
        assertEquals(7L, parsed.actualRevision)
        assertEquals("ap-00000001", parsed.conflict?.withProgramId)
        assertEquals(2, parsed.additionalConflictCount)
    }

    @Test
    fun `managed plan response is decoded strictly with runtime authority`() {
        val response = managedPlanResponse()

        val parsed = DeviceLightMutationParser.ManagedPlan.parse(
            response,
            DeviceLightProduct.WRGB_PRO_ELITE
        )

        assertEquals(4L, parsed.storageGeneration)
        assertEquals(8L, parsed.revision)
        assertEquals("lp-00000001", parsed.planId)
        assertEquals(2, parsed.phases.size)
        assertEquals(DeviceLightManagedPlanRuntimeState.ACTIVE, parsed.runtime.state)
        assertEquals(0, parsed.runtime.activePhaseIndex)
        assertEquals(600, parsed.runtime.transitionPermille)
        assertTrue(
            runCatching {
                DeviceLightMutationParser.ManagedPlan.parse(
                    JSONObject(response.toString()).put("unexpected", true),
                    DeviceLightProduct.WRGB_PRO_ELITE
                )
            }.isFailure
        )
    }

    @Test
    fun `managed plan stale storage error preserves storage authority`() {
        val stale = DeviceRuntimeCommandOutcome.FirmwareError(
            deviceUid = DEVICE_UID,
            module = "light",
            action = "auto.plan.apply",
            messageId = "error-plan-1",
            generation = GENERATION,
            statusCode = 409,
            code = "conflict",
            field = "expectedStorageGeneration",
            message = "Storage generation changed",
            structuredDataJson = """
                {"reason":"STALE_STORAGE_GENERATION","actualStorageGeneration":9}
            """.trimIndent()
        ).lightV1Data()
        val missing = DeviceRuntimeCommandOutcome.FirmwareError(
            deviceUid = DEVICE_UID,
            module = "light",
            action = "auto.plan.delete",
            messageId = "error-plan-2",
            generation = GENERATION,
            statusCode = 404,
            code = "not_found",
            field = "planId",
            message = "Managed plan not found",
            structuredDataJson = """{"reason":"AUTO_PLAN_NOT_FOUND"}"""
        ).lightV1Data()

        assertEquals(DeviceLightErrorReason.STALE_STORAGE_GENERATION, stale.reason)
        assertEquals(9L, stale.actualStorageGeneration)
        assertEquals(DeviceLightErrorReason.AUTO_PLAN_NOT_FOUND, missing.reason)
        assertNull(missing.actualRevision)
    }

    @Test
    fun `WebSocket registry contains firmware Light V1 and no removed Light commands`() {
        val commands = AqlWsContract.authenticatedCommandKeys()
        val expected = DeviceLightRuntimeContract.Action.COMMON_V1 +
            DeviceLightRuntimeContract.Action.ACCLIMATION_V1 +
            setOf(
                DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_STATUS_GET,
                DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET,
                "thermal.status.get",
                "thermal.config.apply"
            )
        assertEquals(
            expected.mapTo(linkedSetOf()) { "${DeviceLightRuntimeContract.MODULE}.$it" },
            commands.filterTo(linkedSetOf()) { it.startsWith("light.") }
        )
        assertFalse("light.channel.regime.set" in commands)
        assertFalse("light.program.apply" in commands)
        assertFalse("light.program.delete" in commands)
    }

    private class RejectingGateway : DeviceRuntimeCommandGateway {
        var calls = 0

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            calls += 1
            error("Unsupported command reached the gateway.")
        }
    }

    private fun autoCreate(scene: DeviceLightScene) = DeviceLightAutoProgramCreatePayload(
        expectedRevision = 3,
        enabled = true,
        weekdaysMask = 127,
        startTimeMs = 28_800_000,
        endTimeMs = 64_800_000,
        rampDurationMs = 1_800_000,
        scene = scene
    )

    private fun managedPlanResponse(): JSONObject = JSONObject(
        """
        {
          "storageGeneration":4,
          "revision":8,
          "installed":true,
          "planId":"lp-00000001",
          "initialStartPercent":60,
          "phaseCount":2,
          "phases":[
            {
              "validFromEpochDay":20000,
              "validUntilEpochDayExclusive":20021,
              "transitionDays":7,
              "weekdaysMask":127,
              "startTimeMs":57600000,
              "endTimeMs":79200000,
              "rampDurationMs":3600000,
              "scene":{"redPercent":40,"greenPercent":32,"bluePercent":35,"whitePercent":46}
            },
            {
              "validFromEpochDay":20021,
              "validUntilEpochDayExclusive":null,
              "transitionDays":7,
              "weekdaysMask":127,
              "startTimeMs":55800000,
              "endTimeMs":79200000,
              "rampDurationMs":3600000,
              "scene":{"redPercent":45,"greenPercent":36,"bluePercent":39,"whitePercent":52}
            }
          ],
          "runtime":{
            "clockReady":true,
            "state":"ACTIVE",
            "activePhaseIndex":0,
            "transitionPermille":600,
            "nextTransitionEpochDay":20001
          }
        }
        """.trimIndent()
    )

    private fun autoUpdate(scene: DeviceLightScene) = DeviceLightAutoProgramUpdatePayload(
        expectedRevision = 3,
        programId = "ap-00000001",
        weekdaysMask = 127,
        startTimeMs = 28_800_000,
        endTimeMs = 64_800_000,
        rampDurationMs = 1_800_000,
        scene = scene
    )

    private fun assertKeys(json: JSONObject, vararg expected: String) {
        assertEquals(expected.toSet(), json.keySet())
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIGHT-V1")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
        val AUTO_CREATE_FIELDS = arrayOf(
            "expectedRevision", "enabled", "weekdaysMask", "startTimeMs", "endTimeMs",
            "rampDurationMs", "scene"
        )
        val AUTO_UPDATE_FIELDS = arrayOf(
            "expectedRevision", "programId", "weekdaysMask", "startTimeMs", "endTimeMs",
            "rampDurationMs", "scene"
        )
    }
}
