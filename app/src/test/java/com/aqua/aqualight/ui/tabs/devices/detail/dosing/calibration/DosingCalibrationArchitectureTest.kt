package com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingCalibrationArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `calibration presentation contains no static-analysis suppression`() {
        CALIBRATION_SOURCE_FILES.forEach { relativePath ->
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
    fun `calibration strings are owned only by dosing resource files`() {
        val defaultDosingStrings = source(DEFAULT_DOSING_STRINGS)
        val turkishDosingStrings = source(TURKISH_DOSING_STRINGS)

        CALIBRATION_STRING_NAMES.forEach { name ->
            val declaration = "name=\"$name\""
            assertTrue("Missing default Dosing string: $name", defaultDosingStrings.contains(declaration))
            assertTrue("Missing Turkish Dosing string: $name", turkishDosingStrings.contains(declaration))
        }

        val misplacedFiles = File(repositoryRoot, "app/src/main/res")
            .walkTopDown()
            .filter(File::isFile)
            .filter { file -> file.extension == "xml" && file.name != DOSING_STRINGS_FILE_NAME }
            .filter { file ->
                val content = file.readText()
                CALIBRATION_STRING_NAMES.any { name -> content.contains("name=\"$name\"") }
            }
            .map(File::getPath)
            .toList()

        assertTrue(
            "Dosing calibration strings leaked into non-Dosing resources: $misplacedFiles",
            misplacedFiles.isEmpty()
        )
    }

    @Test
    fun `calibration UI stays behind application boundary and reuses central pump visual`() {
        val fragment = source(CALIBRATION_ROOT + "DeviceDosingCalibrationFragment.kt")
        val viewModel = source(CALIBRATION_ROOT + "DeviceDosingCalibrationViewModel.kt")
        val screen = source(CALIBRATION_ROOT + "DosingCalibrationScreen.kt")
        val applicationContract = source(
            "app/src/main/java/com/aqua/aqualight/application/devices/" +
                "DeviceDosingCalibrationOperations.kt"
        )

        assertTrue(viewModel.contains("DeviceDosingCalibrationOperations"))
        assertFalse(viewModel.contains("DeviceDosingRuntimeRepository"))
        assertFalse(viewModel.contains("DeviceRuntimeCommandOutcome"))
        assertFalse(fragment.contains("DeviceDosingRuntimeRepository"))
        assertTrue(screen.contains("DosingPumpDevice("))
        assertFalse(screen.contains("Image("))
        assertFalse(screen.contains("painterResource("))
        assertTrue(applicationContract.contains("interface DeviceDosingCalibrationOperations"))
    }

    @Test
    fun `calibration illustrations are native state-driven drawings`() {
        val drawing = source(CALIBRATION_ROOT + "DosingCalibrationDrawing.kt")
        val illustration = source(CALIBRATION_ROOT + "DosingCalibrationIllustration.kt")

        assertTrue(drawing.contains("DrawScope.drawCalibrationBottle"))
        assertTrue(drawing.contains("DrawScope.drawCalibrationCylinder"))
        assertTrue(drawing.contains("DrawScope.drawCalibrationTube"))
        assertTrue(illustration.contains("Animatable"))
        assertTrue(illustration.contains("rememberInfiniteTransition"))
        assertTrue(illustration.contains("DosingCalibrationOperation.PRIMING"))
        assertTrue(illustration.contains("DosingCalibrationOperation.VERIFYING"))
        assertFalse(illustration.contains("Lottie"))
    }

    @Test
    fun `wizard has exactly six named calibration steps`() {
        val models = source(CALIBRATION_ROOT + "DosingCalibrationModels.kt")

        listOf(
            "NAME(",
            "PRIME(",
            "CALIBRATION_DOSE(",
            "MEASURE(",
            "VERIFY_DOSE(",
            "CONFIRM("
        ).forEach { step -> assertTrue(models.contains(step)) }
        assertTrue(models.contains("const val COUNT = 6"))
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
        const val CALIBRATION_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/calibration/"
        const val DEFAULT_DOSING_STRINGS = "app/src/main/res/values/device_dosing_strings.xml"
        const val TURKISH_DOSING_STRINGS = "app/src/main/res/values-tr/device_dosing_strings.xml"
        const val DOSING_STRINGS_FILE_NAME = "device_dosing_strings.xml"

        val CALIBRATION_SOURCE_FILES = listOf(
            CALIBRATION_ROOT + "DeviceDosingCalibrationFragment.kt",
            CALIBRATION_ROOT + "DeviceDosingCalibrationViewModel.kt",
            CALIBRATION_ROOT + "DosingCalibrationModels.kt",
            CALIBRATION_ROOT + "DosingCalibrationScreen.kt",
            CALIBRATION_ROOT + "DosingCalibrationSteps.kt",
            CALIBRATION_ROOT + "DosingCalibrationDrawing.kt",
            CALIBRATION_ROOT + "DosingCalibrationIllustration.kt"
        )

        val FORBIDDEN_SUPPRESSION_TOKENS = listOf(
            "@file:Suppress(",
            "@Suppress(",
            "@SuppressLint("
        )

        val CALIBRATION_STRING_NAMES = listOf(
            "device_dosing_pump_state_selected",
            "device_dosing_calibration_title",
            "device_dosing_calibration_loading",
            "device_dosing_calibration_step_format",
            "device_dosing_calibration_name_title",
            "device_dosing_calibration_name_description",
            "device_dosing_calibration_prime_title",
            "device_dosing_calibration_prime_description",
            "device_dosing_calibration_dose_title",
            "device_dosing_calibration_dose_description",
            "device_dosing_calibration_measure_title",
            "device_dosing_calibration_measure_description",
            "device_dosing_calibration_verify_title",
            "device_dosing_calibration_verify_description",
            "device_dosing_calibration_confirm_title",
            "device_dosing_calibration_confirm_description",
            "device_dosing_calibration_liquid_name_label",
            "device_dosing_calibration_liquid_name_placeholder",
            "device_dosing_calibration_measured_volume_label",
            "device_dosing_calibration_verification_volume_label",
            "device_dosing_calibration_volume_placeholder",
            "device_dosing_calibration_ml_suffix",
            "device_dosing_calibration_continue",
            "device_dosing_calibration_hold_to_prime",
            "device_dosing_calibration_priming_active",
            "device_dosing_calibration_start_dose",
            "device_dosing_calibration_dose_running",
            "device_dosing_calibration_calculate",
            "device_dosing_calibration_run_verification",
            "device_dosing_calibration_verification_running",
            "device_dosing_calibration_confirm_action",
            "device_dosing_calibration_recalibrate_action",
            "device_dosing_calibration_error_unavailable",
            "device_dosing_calibration_error_connection",
            "device_dosing_calibration_error_name_required",
            "device_dosing_calibration_error_command",
            "device_dosing_calibration_error_measurement",
            "device_dosing_calibration_error_verification_volume"
        )
    }
}
