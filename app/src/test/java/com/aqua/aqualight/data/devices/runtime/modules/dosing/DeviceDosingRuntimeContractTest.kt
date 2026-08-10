package com.aqua.aqualight.data.devices.runtime.modules.dosing

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingRuntimeContractTest {
    @Test
    fun `Dosing action catalog contains all eleven authenticated commands`() {
        assertEquals(
            setOf(
                "status.get",
                "config.apply",
                "prime.start",
                "prime.stop",
                "calibration.start",
                "calibration.finish",
                "calibration.confirm",
                "calibration.cancel",
                "dose.now",
                "dose.stop",
                "reservoir.refill"
            ),
            setOf(
                DeviceDosingRuntimeContract.Action.STATUS_GET,
                DeviceDosingRuntimeContract.Action.CONFIG_APPLY,
                DeviceDosingRuntimeContract.Action.PRIME_START,
                DeviceDosingRuntimeContract.Action.PRIME_STOP,
                DeviceDosingRuntimeContract.Action.CALIBRATION_START,
                DeviceDosingRuntimeContract.Action.CALIBRATION_FINISH,
                DeviceDosingRuntimeContract.Action.CALIBRATION_CONFIRM,
                DeviceDosingRuntimeContract.Action.CALIBRATION_CANCEL,
                DeviceDosingRuntimeContract.Action.DOSE_NOW,
                DeviceDosingRuntimeContract.Action.DOSE_STOP,
                DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL
            )
        )
    }

    @Test
    fun `status parser accepts exact Dosing snapshot`() {
        val status = DeviceDosingStatusParser.parse(DeviceDosingRuntimeFixtures.status())

        assertTrue(status.supported)
        assertEquals(2, status.channelCount)
        assertEquals("ml", status.unit)
        assertEquals("Nutrients", status.channels.first().displayName)
        assertEquals(80.0, status.channels.first().dosing.reservoirRemainingPercent, 0.0)
        assertEquals(10.0, status.schedules.single().amountMl, 0.0)
        assertTrue(status.schedules.single().runtimeEnabled)
        assertTrue(status.runtime.supportsCalibrationWorkflow)
        assertTrue(status.runtime.supportsCalibrationSessionState)
        assertEquals(
            DeviceDosingCalibrationState.IDLE,
            status.channels.first().dosing.calibration.state
        )
    }

    @Test
    fun `status parser rejects extra fields aliases and fabricated reservoir status`() {
        val extra = DeviceDosingRuntimeFixtures.status().put("unexpected", true)
        val alias = DeviceDosingRuntimeFixtures.status()
        alias.getJSONArray("channels").getJSONObject(0).put("regime", "schedule")
        val fabricated = DeviceDosingRuntimeFixtures.status()
        fabricated.getJSONArray("channels").getJSONObject(0)
            .getJSONObject("dosing")
            .put("reservoirStatus", "ok")

        assertTrue(runCatching { DeviceDosingStatusParser.parse(extra) }.isFailure)
        assertTrue(runCatching { DeviceDosingStatusParser.parse(alias) }.isFailure)
        assertTrue(runCatching { DeviceDosingStatusParser.parse(fabricated) }.isFailure)
    }

    @Test
    fun `status parser rejects malformed derived schedule and reservoir values`() {
        val badTime = DeviceDosingRuntimeFixtures.status()
        badTime.getJSONArray("schedules").getJSONObject(0).put("startTime", "8:00")
        val badPercent = DeviceDosingRuntimeFixtures.status()
        badPercent.getJSONArray("channels").getJSONObject(0)
            .getJSONObject("dosing")
            .put("reservoirRemainingPercent", 75.0)
        val badWeekdays = DeviceDosingRuntimeFixtures.status()
        badWeekdays.getJSONArray("schedules").getJSONObject(0)
            .put("weekdays", JSONArray(listOf(true, false)))

        assertTrue(runCatching { DeviceDosingStatusParser.parse(badTime) }.isFailure)
        assertTrue(runCatching { DeviceDosingStatusParser.parse(badPercent) }.isFailure)
        assertTrue(runCatching { DeviceDosingStatusParser.parse(badWeekdays) }.isFailure)
    }

    @Test
    fun `config payload preserves omitted fields and empty full schedule replacement`() {
        val deleteAll = DeviceDosingConfigApplyPayload(
            schedules = emptyList(),
            save = true
        ).toJson()

        assertEquals(setOf("schedules", "save"), deleteAll.keys().asSequence().toSet())
        assertEquals(0, deleteAll.getJSONArray("schedules").length())
        assertFalse(deleteAll.has("channels"))
    }

    @Test
    fun `channel config normalizes identity display name and exact dosing fields`() {
        val encoded = DeviceDosingChannelConfig(
            channelKey = " CHANNEL1 ",
            displayName = " Nutrient Pump ",
            dosing = DeviceDosingChannelDosingConfig(
                doseMsPerMl = 1_250L,
                reservoirTrackingEnabled = true,
                reservoirCapacityMl = 750.0
            )
        ).toJson()

        assertEquals(setOf("channelKey", "displayName", "dosing"), encoded.keys().asSequence().toSet())
        assertEquals("channel1", encoded.getString("channelKey"))
        assertEquals("Nutrient Pump", encoded.getString("displayName"))
        assertEquals(
            setOf("doseMsPerMl", "reservoirTrackingEnabled", "reservoirCapacityMl"),
            encoded.getJSONObject("dosing").keys().asSequence().toSet()
        )
    }

    @Test
    fun `manual dose and calibration payloads reject firmware unsafe values`() {
        val invalidDose = runCatching {
            DeviceDosingDoseNowPayload("channel1", amountMl = 1_000.01)
        }
        val invalidDuration = runCatching {
            DeviceDosingCalibrationStartPayload("channel1", durationMs = 999L)
        }
        val invalidMeasurement = runCatching {
            DeviceDosingCalibrationFinishPayload("channel1", measuredMl = 0.049)
        }

        assertTrue(invalidDose.isFailure)
        assertTrue(invalidDuration.isFailure)
        assertTrue(invalidMeasurement.isFailure)
    }

    @Test
    fun `enabled Dosing schedule requires an active weekday and repeat`() {
        val invalid = runCatching {
            DeviceDosingScheduleConfig(
                enabled = true,
                name = "Invalid",
                channelKey = "channel1",
                weekdays = List(DOSING_WEEKDAY_COUNT) { false },
                startTimeMs = 0L,
                repeatCount = 0,
                amountMl = 1.0
            )
        }

        assertTrue(invalid.isFailure)
    }
}
