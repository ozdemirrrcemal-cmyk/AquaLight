package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingChannelStatusPolicyTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `root card exposes a status pill only while calibration is required`() {
        val card = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCard.kt"
        )
        val models = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCardModels.kt"
        )

        assertTrue(
            card.contains(
                "val isNotConfigured = state.visualState == " +
                    "DosingChannelVisualState.NOT_CONFIGURED"
            )
        )
        assertTrue(card.contains("statusLabel?.let"))
        assertTrue(card.contains("R.string.device_dosing_channel_not_configured"))
        assertFalse(card.contains("stringResource(state.visualState.labelRes)"))
        assertFalse(card.contains("state.visualState.statusColor"))

        assertTrue(models.contains("DeviceDosingChannelDestination.DETAIL -> " +
            "DosingChannelVisualState.CALIBRATED"))
        assertFalse(models.contains("DosingChannelVisualState.READY"))
        assertFalse(models.contains("DosingChannelVisualState.SCHEDULED"))
        assertFalse(models.contains("DosingChannelVisualState.DOSING"))
        assertFalse(models.contains("DosingChannelVisualState.ERROR"))
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
}
