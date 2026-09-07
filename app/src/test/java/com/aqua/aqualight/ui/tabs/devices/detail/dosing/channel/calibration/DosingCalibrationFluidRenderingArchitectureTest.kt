package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingCalibrationFluidRenderingArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun activeTubeUsesContinuousFluidRenderingWithoutChangingTheOutlet() {
        val primitives = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "channel/calibration/DosingCalibrationIllustrationPrimitives.kt"
        )
        assertFalse(primitives.contains("dashPathEffect"))
        assertTrue(primitives.contains("smoothCalibrationFlowWave(flowPhase)"))
    }

    private fun source(relativePath: String): String =
        File(repositoryRoot, relativePath).readText()

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
