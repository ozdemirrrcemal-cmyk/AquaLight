package com.aqua.aqualight.data.devices.dosing.v1

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingPrimeHoldStabilityContractTest {

    @Test
    fun `transient dosing invalidation keeps presentation stable but authoritative reads fail closed`() {
        val source = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/dosing/v1/" +
                "DeviceDosingV1StateOwner.kt"
        )
        val invalidate = source.substringAfter("fun invalidate(")
            .substringBefore("fun setLowLevelAlertIntent(")
        val reads = source.substringAfter("private class DefaultDeviceDosingV1StateReadAccess(")

        assertTrue(invalidate.contains("channel = current?.channel"))
        assertTrue(invalidate.contains("calibration = current?.calibration"))
        assertTrue(reads.contains("?.presentationChannel()"))
        assertTrue(reads.contains("?.presentationCalibration()"))
        assertTrue(reads.contains("?.authoritativeChannel()"))
        assertTrue(reads.contains("?.authoritativeCalibration()"))
        assertTrue(
            reads.contains(
                "state.authority == OwnedDosingChannelAuthority.AUTHORITATIVE"
            )
        )
    }

    @Test
    fun `prime hold locks parent scrolling until the user releases the control`() {
        val screen = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "calibration/DeviceDosingCalibrationScreen.kt"
        )

        assertTrue(
            screen.contains(
                "userScrollEnabled = state.step != DeviceDosingCalibrationStep.PRIME || " +
                    "!state.isPumpActive"
            )
        )
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
