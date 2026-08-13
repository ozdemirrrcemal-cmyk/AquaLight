package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingCalibrationFixtureTest {
    @Test
    fun `shared firmware fixture locks transactional calibration contract`() {
        val fixture = JSONObject(
            requireNotNull(javaClass.classLoader?.getResourceAsStream(FIXTURE_NAME)) {
                "Missing shared Dosing calibration fixture: $FIXTURE_NAME"
            }.bufferedReader().use { it.readText() }
        )
        val commands = fixture.getJSONObject("commands")
        val invariants = fixture.getJSONObject("invariants")

        assertEquals(DeviceDosingRuntimeContract.SCHEMA, fixture.getString("schema"))
        assertEquals("calibration", fixture.getString("workflow"))
        assertEquals(
            DeviceDosingRuntimeContract.Limit.VERIFICATION_DOSE_ML,
            fixture.getDouble("verificationDoseMl"),
            0.0
        )
        assertEquals(
            DeviceDosingRuntimeContract.Action.CALIBRATION_START,
            commands.getJSONObject("start").getString("name").removePrefix("dosing.")
        )
        assertEquals(
            DeviceDosingRuntimeContract.Literal.CALIBRATION_STATE_PENDING_VERIFICATION,
            commands.getJSONObject("finish").getString("responseState")
        )
        assertTrue(commands.getJSONObject("confirm").getBoolean("advancesChannelRevision"))
        assertTrue(commands.getJSONObject("confirm").getBoolean("requiresCompletedVerificationDose"))
        assertFalse(invariants.getBoolean("finishMutatesConfirmedCalibration"))
        assertTrue(invariants.getBoolean("verificationUsesPendingCalibrationOnly"))
        assertTrue(invariants.getBoolean("confirmCommitsCoefficientAndTimestampTogether"))
        assertTrue(invariants.getBoolean("failedPersistencePreservesPendingSession"))
        assertTrue(invariants.getBoolean("cancelPreservesPreviousConfirmedCalibration"))
    }

    private companion object {
        const val FIXTURE_NAME = "aql_dosing_calibration_v1.json"
    }
}
