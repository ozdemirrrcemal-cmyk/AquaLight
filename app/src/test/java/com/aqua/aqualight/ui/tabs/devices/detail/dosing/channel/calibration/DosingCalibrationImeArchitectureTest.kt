package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingCalibrationImeArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun calibrationFormKeepsTheActivityStableAndOwnsImeInsets() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        val screen = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "channel/calibration/DeviceDosingCalibrationScreen.kt"
        )

        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize\""))
        assertTrue(screen.contains(".imePadding()"))
        assertTrue(screen.contains("WindowInsets.isImeVisible"))
        assertTrue(screen.contains("formBringIntoViewRequester.bringIntoView()"))
        assertTrue(screen.contains("Modifier.bringIntoViewRequester"))
        assertTrue(screen.contains(".weight(1f)"))
        assertTrue(screen.indexOf("CalibrationPump(") < screen.indexOf("LazyColumn("))
        assertFalse(screen.contains("animateScrollToItem(CALIBRATION_FORM_ITEM_INDEX)"))
    }

    @Test
    fun imeDoneDismissesTheKeyboardWithoutSubmittingEitherEditableStep() {
        val field = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "channel/calibration/CalibrationTextFieldModel.kt"
        )
        val controls = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "channel/calibration/DosingCalibrationStepControls.kt"
        )

        assertTrue(field.contains("imeAction = ImeAction.Done"))
        assertTrue(field.contains("KeyboardActions(onDone = { focusManager.clearFocus() })"))
        assertFalse(field.contains("onImeDone"))
        assertFalse(controls.contains("onImeDone"))
        assertEquals(EDITABLE_STEP_COUNT, controls.split("onClick = onSubmit").size - 1)
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

    private companion object {
        const val EDITABLE_STEP_COUNT = 2
    }
}
