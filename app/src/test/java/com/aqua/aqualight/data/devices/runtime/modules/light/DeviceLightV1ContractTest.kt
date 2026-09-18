package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
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
            "7df97ce807ebb1e90ff63cc36206d6ce479a62fc",
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
    fun `graph parser preserves firmware time and channel tuples for both products`() {
        DeviceLightProduct.entries.forEach { product ->
            val graph = DeviceLightMutationParser.Graph.parseGraph(
                DeviceLightRuntimeFixtures.graph(product, DeviceLightMode.AUTO),
                product
            )

            assertEquals(DeviceLightMode.AUTO, graph.mode)
            assertEquals(43_200_000L, graph.nowTimeMs)
            assertEquals(listOf(0L, 43_200_000L, 86_400_000L), graph.points.map { it.timeMs })
            assertTrue(graph.points.all { point ->
                point.channelPermille.size == product.channelCount
            })
        }
    }

    @Test
    fun `graph parser rejects invented or incoherent schedule data`() {
        val wrongTupleWidth = DeviceLightRuntimeFixtures.graph(
            mode = DeviceLightMode.AUTO
        ).also { graph ->
            graph.getJSONArray("points").getJSONArray(0).put(100)
        }
        val manualWithSchedule = DeviceLightRuntimeFixtures.graph().also { graph ->
            graph.put("reason", "OK")
            graph.put("hasScheduleToday", true)
            graph.getJSONArray("points").put(JSONArray(listOf(0, 0, 0, 0, 0)))
        }

        assertTrue(
            runCatching {
                DeviceLightMutationParser.Graph.parseGraph(
                    wrongTupleWidth,
                    DeviceLightProduct.WRGB_PRO_ELITE
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                DeviceLightMutationParser.Graph.parseGraph(
                    manualWithSchedule,
                    DeviceLightProduct.WRGB_PRO_ELITE
                )
            }.isFailure
        )
    }

    @Test
    fun `every Light V1 request serializer emits exact firmware keys and tuple width`() {
        val wrgb = DeviceLightScene.wrgb(10, 20, 30, 40)
        val rgb = DeviceLightScene.rgb(10, 20, 30)
        assertBasicLightSerializers(wrgb, rgb)
        assertProgramSerializers(wrgb)
        assertCustomInstallSerializers(wrgb, rgb)
        assertPreviewSerializers(wrgb)
    }

    private fun assertBasicLightSerializers(wrgb: DeviceLightScene, rgb: DeviceLightScene) {
        assertKeys(DeviceLightControlSetPayload(DeviceLightMode.AUTO).toJson(), "mode")
        assertKeys(DeviceLightManualSetPayload(wrgb).toJson(), "scene")
        assertKeys(wrgb.toJson(), "redPercent", "greenPercent", "bluePercent", "whitePercent")
        assertKeys(rgb.toJson(), "redPercent", "greenPercent", "bluePercent")
    }

    private fun assertProgramSerializers(wrgb: DeviceLightScene) {
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

    private fun assertCustomInstallSerializers(
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
    }

    private fun assertPreviewSerializers(wrgb: DeviceLightScene) {
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
        val customDay = DeviceLightPreviewSetPayload.CustomDay(
            listOf(
                DeviceLightCustomPoint(0, wrgb),
                DeviceLightCustomPoint(60_000, DeviceLightScene.wrgb(50, 60, 70, 80))
            )
        ).toJson()
        assertKeys(customDay, "playback", "points")
        assertEquals(
            DeviceLightRuntimeContract.Playback.CUSTOM_DAY,
            customDay.getString(DeviceLightRuntimeContract.Field.PLAYBACK)
        )
        assertEquals(5, customDay.getJSONArray("points").getJSONArray(0).length())
    }

    @Test
    fun `preview response accepts the full custom day duration`() {
        val result = DeviceLightMutationParser.parsePreviewSet(
            JSONObject()
                .put("active", true)
                .put("remainingMs", 24_000)
                .put("event", DeviceLightRuntimeContract.Event.STATUS_CHANGED)
        )

        assertEquals(24_000L, result.remainingMs)
    }

    @Test
    fun `RGB Slim acclimation commands fail closed before gateway`() = runBlocking {
        val gateway = RejectingGateway()
        val stateOwner = DeviceLightRuntimeStateOwner()
        stateOwner.beginGeneration(DEVICE_UID, GENERATION)
        stateOwner.recordStatus(
            DEVICE_UID,
            GENERATION,
            DeviceLightStatusParser.parse(
                DeviceLightRuntimeFixtures.status(DeviceLightProduct.RGB_PRO_SLIM)
            )
        )
        val repository = DeviceLightRuntimeRepository(gateway, stateOwner)

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
            code = "CONFLICT",
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
        assertEquals(DeviceLightErrorReason.Known.AUTO_PROGRAM_OVERLAP, parsed.reason)
        assertEquals(7L, parsed.actualRevision)
        assertEquals("ap-00000001", parsed.conflict?.withProgramId)
        assertEquals(2, parsed.additionalConflictCount)
    }

    @Test
    fun `firmware INVALID_VALUE fallback is a known Light error reason`() {
        val error = lightFirmwareError(
            structuredDataJson = """{"reason":"INVALID_VALUE"}"""
        )

        assertEquals(DeviceLightErrorReason.Known.INVALID_VALUE, error.lightV1Data().reason)
    }

    @Test
    fun `unknown Light error reason is retained without rejecting future fields`() {
        val error = lightFirmwareError(
            structuredDataJson =
                """{"reason":"SENSOR_CALIBRATION_FAILED","sensorIndex":2}"""
        )

        val reason = error.lightV1Data().reason
        assertEquals(
            DeviceLightErrorReason.Unknown("SENSOR_CALIBRATION_FAILED"),
            reason
        )
        assertEquals(
            "SENSOR_CALIBRATION_FAILED",
            (reason as DeviceLightErrorReason.Unknown).rawValue
        )
        assertEquals(
            2,
            JSONObject(error.structuredDataJson).getInt("sensorIndex")
        )
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

    private fun lightFirmwareError(
        structuredDataJson: String
    ) = DeviceRuntimeCommandOutcome.FirmwareError(
        deviceUid = DEVICE_UID,
        module = DeviceLightRuntimeContract.MODULE,
        action = DeviceLightRuntimeContract.Action.AUTO_PROGRAM_UPDATE,
        messageId = "error-forward-compatibility",
        generation = GENERATION,
        statusCode = 422,
        code = "INVALID_VALUE",
        field = "data",
        message = "invalid Light V1 candidate",
        structuredDataJson = structuredDataJson
    )

    private fun autoCreate(scene: DeviceLightScene) = DeviceLightAutoProgramCreatePayload(
        expectedRevision = 3,
        enabled = true,
        weekdaysMask = 127,
        startTimeMs = 28_800_000,
        endTimeMs = 64_800_000,
        rampDurationMs = 1_800_000,
        scene = scene
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
