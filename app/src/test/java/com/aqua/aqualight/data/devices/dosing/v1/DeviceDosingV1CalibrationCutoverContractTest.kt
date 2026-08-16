package com.aqua.aqualight.data.devices.dosing.v1

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1CalibrationCutoverContractTest {
    @Test
    fun `stage 8 requests pin the firmware calibration duration and verification dose`() {
        val fixture = fixture()
        val commands = fixture.getJSONObject("commands")
        val firmwareStart = commands.getJSONObject("start").getJSONObject("request")
        val firmwareVerification = commands.getJSONObject("verify").getJSONObject("request")

        assertEquals(
            DeviceDosingV1Contract.Limit.DEFAULT_CALIBRATION_DURATION_MS,
            firmwareStart.getLong("durationMs")
        )
        assertEquals(
            DeviceDosingV1Contract.Limit.VERIFICATION_DOSE_ML,
            fixture.getDouble("verificationDoseMl"),
            0.0
        )

        val start = DeviceDosingV1CalibrationStartRequest(
            channelKey = CHANNEL,
            durationMillis = DeviceDosingV1Contract.Limit.DEFAULT_CALIBRATION_DURATION_MS
        ).toJson()
        assertEquals(
            DeviceDosingV1Contract.Limit.DEFAULT_CALIBRATION_DURATION_MS,
            start.getLong("durationMs")
        )

        val verification = DeviceDosingV1DoseNowRequest(
            channelKey = CHANNEL,
            amount = DeviceDosingV1Amount.fromMilliliters(
                DeviceDosingV1Contract.Limit.VERIFICATION_DOSE_ML
            ),
            usePendingCalibration = true
        ).toJson()
        assertEquals(
            DeviceDosingV1Contract.Limit.VERIFICATION_DOSE_ML,
            verification.getDouble("amountMl"),
            0.0
        )
        assertTrue(verification.getBoolean("usePendingCalibration"))
        assertEquals(
            firmwareVerification.getDouble("amountMl"),
            verification.getDouble("amountMl"),
            0.0
        )
    }

    @Test
    fun `firmware restart explicitly discards pending calibration session`() {
        assertTrue(
            fixture().getJSONObject("invariants")
                .getBoolean("firmwareRestartDiscardsPendingSession")
        )
    }

    private fun fixture(): JSONObject = JSONObject(
        requireNotNull(javaClass.classLoader?.getResourceAsStream(FIXTURE)) {
            "Missing fixture resource: $FIXTURE"
        }.use { input -> input.readBytes().toString(Charsets.UTF_8) }
    )

    private companion object {
        const val FIXTURE = "aql_dosing_calibration_v1.json"
        val CHANNEL = DeviceDosingV1ChannelKey.from("channel1")
    }
}
