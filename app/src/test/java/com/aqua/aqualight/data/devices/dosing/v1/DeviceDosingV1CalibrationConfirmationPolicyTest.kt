package com.aqua.aqualight.data.devices.dosing.v1

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1CalibrationConfirmationPolicyTest {

    @Test
    fun `pending verification blocks standalone name editing but keeps calibration workflow open`() {
        val global = DeviceDosingV1StatusParser.parseGlobal(DeviceDosingV1TestFixtures.globalStatus())
        val detail = DeviceDosingV1StatusParser.parseChannelDetail(
            DeviceDosingV1TestFixtures.channelDetail().also { channel ->
                channel.getJSONObject("calibration")
                    .put("state", "pendingVerification")
                    .put("verificationDoseStarted", true)
                    .put("verificationDoseComplete", true)
                    .put("pendingDoseMsPerMl", 1_250)
            }
        )

        val controls = DeviceDosingV1ChannelSnapshotMapper.controls(detail, global)

        assertFalse(controls.displayNameEditable)
        assertTrue(controls.calibrationEditable)
    }

    @Test
    fun `final calibration identity commit is not gated by standalone display name editing`() {
        val source = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/dosing/v1/" +
                "DeviceDosingV1CalibrationOperationsAdapter.kt"
        )
        val confirm = source.substringAfter("override suspend fun confirm(")
            .substringBefore("override suspend fun cancel(")

        assertTrue(confirm.contains("requireCalibrationMutation(baseline.controls.calibrationEditable)"))
        assertFalse(confirm.contains("displayNameEditable"))
        assertTrue(confirm.contains("adapter.repository.confirmCalibration("))
        assertTrue(confirm.contains("DeviceDosingV1CalibrationConfirmRequest("))
    }

    private fun source(relativePath: String): String = File(repositoryRoot(), relativePath).readText()

    private fun repositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
