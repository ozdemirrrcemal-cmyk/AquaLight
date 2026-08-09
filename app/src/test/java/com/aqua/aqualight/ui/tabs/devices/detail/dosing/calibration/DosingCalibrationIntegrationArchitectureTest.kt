package com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingCalibrationIntegrationArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `calibration integration contains no suppression annotations`() {
        INTEGRATION_SOURCE_FILES.forEach { relativePath ->
            val content = source(relativePath)
            FORBIDDEN_SUPPRESSION_TOKENS.forEach { token ->
                assertFalse(
                    "$relativePath must not suppress static analysis with $token",
                    content.contains(token)
                )
            }
        }
    }

    @Test
    fun `owner factory no longer relies on unchecked cast or method suppression`() {
        val factory = source(OWNER_FACTORY)

        assertFalse(factory.contains("UNCHECKED_CAST"))
        assertFalse(factory.contains("LongMethod"))
        assertFalse(factory.contains("CyclomaticComplexMethod"))
        assertTrue(factory.contains("modelClass.cast(viewModel)"))
        assertTrue(factory.contains("OwnerViewModelBindings("))
    }

    @Test
    fun `firmware adapter uses runtime contract and pending calibration for verification`() {
        val adapter = source(CALIBRATION_ADAPTER)

        assertTrue(adapter.contains("DeviceDosingRuntimeContract.Limit.DEFAULT_CALIBRATION_DURATION_MS"))
        assertTrue(adapter.contains("DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_MEASURED_ML"))
        assertTrue(adapter.contains("DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_MEASURED_ML"))
        assertTrue(adapter.contains("DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_ML"))
        assertTrue(adapter.contains("usePendingCalibration = true"))
        assertFalse(adapter.contains("amountMl = 4.0"))
    }

    private fun source(relativePath: String): String = File(repositoryRoot, relativePath).readText()

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }

    private companion object {
        const val CALIBRATION_ADAPTER =
            "app/src/main/java/com/aqua/aqualight/data/devices/" +
                "DefaultDeviceDosingCalibrationOperations.kt"
        const val OWNER_FACTORY =
            "app/src/main/java/com/aqua/aqualight/composition/OwnerViewModelFactory.kt"

        val INTEGRATION_SOURCE_FILES = listOf(
            "app/src/main/java/com/aqua/aqualight/application/devices/" +
                "DeviceDosingCalibrationOperations.kt",
            CALIBRATION_ADAPTER,
            "app/src/main/java/com/aqua/aqualight/composition/OwnerViewModelFactory.kt",
            "app/src/main/java/com/aqua/aqualight/composition/OwnerViewModelBindings.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingPumpDeviceCompose.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingPumpIndicatorDrawing.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/calibration/" +
                "DosingCalibrationPumpSafety.kt"
        )

        val FORBIDDEN_SUPPRESSION_TOKENS = listOf(
            "@file:Suppress(",
            "@Suppress(",
            "@SuppressLint("
        )
    }
}
