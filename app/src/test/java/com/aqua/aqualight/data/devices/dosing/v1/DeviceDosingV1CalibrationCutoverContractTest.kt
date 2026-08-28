package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationConstraints
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1CalibrationCutoverContractTest {
    @Test
    fun `calibration requests pin product duration verification dose and final identity commit`() {
        val fixture = fixture()
        val commands = fixture.getJSONObject("commands")
        val firmwareStart = commands.getJSONObject("start").getJSONObject("request")
        val firmwareVerification = commands.getJSONObject("verify").getJSONObject("request")
        val firmwareConfirm = commands.getJSONObject("confirm").getJSONObject("request")

        // The pinned firmware default remains 5 seconds. AquaLight deliberately requests the
        // shorter product collection window instead of changing the wire contract itself.
        assertEquals(
            DeviceDosingV1Contract.Limit.DEFAULT_CALIBRATION_DURATION_MS,
            firmwareStart.getLong("durationMs")
        )
        val productDuration = DeviceDosingCalibrationConstraints().calibrationRunDurationMs
        assertEquals(3_000L, productDuration)
        assertTrue(
            productDuration in DeviceDosingV1Contract.Limit.MIN_CALIBRATION_DURATION_MS..
                DeviceDosingV1Contract.Limit.MAX_CALIBRATION_DURATION_MS
        )
        assertEquals(
            DeviceDosingV1Contract.Limit.VERIFICATION_DOSE_ML,
            fixture.getDouble("verificationDoseMl"),
            0.0
        )

        val start = DeviceDosingV1CalibrationStartRequest(
            channelKey = CHANNEL,
            durationMillis = productDuration
        ).toJson()
        assertEquals(productDuration, start.getLong("durationMs"))
        val adapterSource = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/dosing/v1/" +
                "DeviceDosingV1CalibrationOperationsAdapter.kt"
        )
        val startOperation = adapterSource.substringAfter("override suspend fun start(")
            .substringBefore("override suspend fun finish(")
        assertTrue(startOperation.contains("durationMillis = constraints.calibrationRunDurationMs"))

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

        val confirm = DeviceDosingV1CalibrationConfirmRequest(
            channelKey = CHANNEL,
            displayName = " Trace Elements "
        ).toJson()
        assertEquals(firmwareConfirm.getString("channelKey"), confirm.getString("channelKey"))
        assertEquals(firmwareConfirm.getString("displayName"), confirm.getString("displayName"))
        assertTrue(
            fixture.getJSONObject("invariants")
                .getBoolean("confirmCommitsDisplayNameWithCalibration")
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

    private fun source(relativePath: String): String = File(repositoryRoot(), relativePath).readText()

    private fun repositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }

    private companion object {
        const val FIXTURE = "aql_dosing_calibration_v1.json"
        val CHANNEL = DeviceDosingV1ChannelKey.from("channel1")
    }
}
