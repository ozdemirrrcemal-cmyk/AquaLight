package com.aqua.aqualight.data.devices.dosing.v1

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingPrimeHoldStabilityContractTest {

    @Test
    fun `reconciliation separates coherent presentation continuation and authority`() {
        val source = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/dosing/v1/" +
                "DeviceDosingV1StateOwner.kt"
        )
        val invalidate = source.substringAfter("fun invalidate(")
            .substringBefore("fun setLowLevelAlertIntent(")
        val reads = source.substringAfter("private class DefaultDeviceDosingV1StateReadAccess(")
        val lifecycle = source.substringAfter("fun invalidateAll(")
            .substringBefore("private fun prepareDevice(")

        assertTrue(source.contains("val coherentChannel: DeviceDosingChannelSnapshot?"))
        assertTrue(source.contains("val coherentCalibration: DeviceDosingCalibrationSnapshot?"))
        assertTrue(
            source.contains(
                "val committedMutation: DeviceDosingV1CommittedMutationContinuation? = null"
            )
        )
        assertTrue(source.contains("OwnedDosingChannelAuthority.AUTHORITATIVE"))
        assertTrue(source.contains("OwnedDosingChannelAuthority.RECONCILING"))
        assertTrue(source.contains("OwnedDosingChannelAuthority.CONNECTION_STALE"))
        assertTrue(invalidate.contains("committedMutation = preservedContinuation"))
        assertTrue(
            invalidate.contains(
                "revisionHint == null || revisionHint <= continuation.channel.revision"
            )
        )
        assertTrue(lifecycle.contains("OwnedDosingChannelAuthority.CONNECTION_STALE"))
        assertTrue(reads.contains("?.presentationChannel()"))
        assertTrue(reads.contains("?.presentationCalibration()"))
        assertTrue(reads.contains("?.authoritativeChannel()"))
        assertTrue(reads.contains("?.authoritativeCalibration()"))
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
