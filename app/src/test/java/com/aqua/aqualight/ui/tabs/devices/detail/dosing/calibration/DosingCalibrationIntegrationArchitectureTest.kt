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
    fun `owner factory keeps calibration binding in central composition root`() {
        val factory = source(OWNER_FACTORY)

        assertTrue(factory.contains("val graph = ownerGraphResolver.requireActive()"))
        assertTrue(factory.contains("DeviceDosingCalibrationViewModel::class.java"))
        assertTrue(factory.contains("DefaultDeviceDosingCalibrationOperations("))
    }

    @Test
    fun `verification uses fixed four milliliter policy and pending firmware calibration`() {
        val adapter = source(CALIBRATION_ADAPTER)
        val models = source(CALIBRATION_MODELS)
        val viewModel = source(CALIBRATION_VIEW_MODEL)
        val steps = source(CALIBRATION_STEPS)

        assertTrue(adapter.contains("DeviceDosingRuntimeContract.Limit.DEFAULT_CALIBRATION_DURATION_MS"))
        assertTrue(adapter.contains("DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_MEASURED_ML"))
        assertTrue(adapter.contains("DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_MEASURED_ML"))
        assertTrue(adapter.contains("DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_ML"))
        assertTrue(adapter.contains("usePendingCalibration = true"))
        assertTrue(models.contains("const val VERIFICATION_DOSE_ML = 4.0"))
        assertFalse(models.contains("verificationMlInput"))
        assertFalse(models.contains("VerificationVolumeChanged"))
        assertTrue(viewModel.contains("amountMl = state.verificationDoseMl"))
        assertFalse(steps.contains("device_dosing_calibration_verification_volume_label"))
    }

    @Test
    fun `pump and channel card share one calibration navigation callback`() {
        val root = source(DOSING_ROOT_FRAGMENT)
        val catalog = source(DOSING_CATALOG_SCREEN)
        val card = source(DOSING_CHANNEL_CARD)

        assertTrue(root.contains("onChannelClick = ::openCalibration"))
        assertTrue(
            root.contains("actionDeviceDosingRootFragmentToDeviceDosingCalibrationFragment")
        )
        assertTrue(catalog.contains("onChannelClick(channel.wireKey)"))
        assertTrue(catalog.contains("channel.channelNumber == channelNumber"))
        assertTrue(card.contains(".clickable(onClick = onClick)"))
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
        const val CALIBRATION_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/calibration/"
        const val CALIBRATION_MODELS = CALIBRATION_ROOT + "DosingCalibrationModels.kt"
        const val CALIBRATION_VIEW_MODEL = CALIBRATION_ROOT + "DeviceDosingCalibrationViewModel.kt"
        const val CALIBRATION_STEPS = CALIBRATION_ROOT + "DosingCalibrationSteps.kt"
        const val DOSING_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/"
        const val DOSING_ROOT_FRAGMENT = DOSING_ROOT + "DeviceDosingRootFragment.kt"
        const val DOSING_CATALOG_SCREEN = DOSING_ROOT + "DosingCatalogScreen.kt"
        const val DOSING_CHANNEL_CARD = DOSING_ROOT + "DosingChannelCard.kt"

        val INTEGRATION_SOURCE_FILES = listOf(
            "app/src/main/java/com/aqua/aqualight/application/devices/" +
                "DeviceDosingCalibrationOperations.kt",
            CALIBRATION_ADAPTER,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingPumpDeviceCompose.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingPumpIndicatorDrawing.kt",
            CALIBRATION_ROOT + "DosingCalibrationPumpSafety.kt"
        )

        val FORBIDDEN_SUPPRESSION_TOKENS = listOf(
            "@file:Suppress(",
            "@Suppress(",
            "@SuppressLint("
        )
    }
}
