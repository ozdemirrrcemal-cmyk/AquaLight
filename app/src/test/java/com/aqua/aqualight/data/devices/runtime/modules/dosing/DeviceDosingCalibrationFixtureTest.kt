package com.aqua.aqualight.data.devices.runtime.modules.dosing

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingCalibrationFixtureTest {

    @Test
    fun `shared firmware fixture locks pending verification workflow`() {
        val fixture = JSONObject(fixtureFile().readText())
        val commands = fixture.getJSONObject("commands")
        val invariants = fixture.getJSONObject("invariants")

        assertEquals("aqualight.dosing.v1", fixture.getString("schema"))
        assertEquals(4.0, fixture.getDouble("verificationDoseMl"), 0.0)
        assertEquals("dosing.calibration.start", commands.getJSONObject("start").getString("name"))
        assertEquals("dosing.calibration.finish", commands.getJSONObject("finish").getString("name"))
        assertEquals("dosing.dose.now", commands.getJSONObject("verify").getString("name"))
        assertEquals("dosing.calibration.confirm", commands.getJSONObject("confirm").getString("name"))
        assertTrue(commands.getJSONObject("confirm").getBoolean("advancesChannelRevision"))
        assertFalse(invariants.getBoolean("finishMutatesConfirmedCalibration"))
        assertTrue(invariants.getBoolean("verificationUsesPendingCalibrationOnly"))
        assertTrue(invariants.getBoolean("confirmCommitsCoefficientAndTimestampTogether"))
        assertFalse(invariants.getBoolean("calibrationAndVerificationCountTowardDailyUserDose"))
        assertTrue(invariants.getBoolean("verificationReservoirAccountingUsesPendingCoefficient"))
    }

    private fun fixtureFile(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            val fixture = File(candidate, "protocol/fixtures/aql_dosing_calibration_v1.json")
            if (fixture.isFile) return fixture
            candidate = candidate.parentFile
        }
        error("Cannot locate shared Dosing calibration fixture.")
    }
}
