package com.aqua.aqualight.ui.common.devicevisual.dosing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingSharedDeviceVisualArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `operation facade stays hose free while identity owns hoses`() {
        val operationFacade = source(DOSING_PUMP_ROOT + "DosingPumpDeviceCompose.kt")
        val identityVisual = source(SHARED_DOSING_VISUAL_ROOT + "DosingDeviceIdentityVisual.kt")

        assertFalse(operationFacade.contains("drawIdentityHoses"))
        assertFalse(operationFacade.contains("HOSE_"))
        assertTrue(identityVisual.contains("drawIdentityHoses"))
        assertTrue(identityVisual.contains("DosingPumpHeadVisual"))
    }

    @Test
    fun `channel cards use pump head instead of numeric badge`() {
        val composeHeader = source(DOSING_CARD_ROOT + "DosingChannelCardHeader.kt")
        val tankBinder = source(TANK_DEVICE_ROOT + "DosingDeviceSpotlightCardBinder.kt")

        assertTrue(composeHeader.contains("DosingPumpHeadVisual"))
        assertFalse(composeHeader.contains("channelNumber.toString()"))
        assertTrue(tankBinder.contains("showPumpHead"))
        assertFalse(tankBinder.contains("tvChannelBadge.text"))
    }

    @Test
    fun `shared visuals contain presentation only dependencies`() {
        File(repositoryRoot, SHARED_DOSING_VISUAL_ROOT)
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .forEach { file ->
                val content = file.readText()
                assertFalse(content.contains("com.aqua.aqualight.data."))
                assertFalse(content.contains("com.aqua.aqualight.platform."))
                assertFalse(content.contains("com.aqua.aqualight.application.devices.dosing."))
                assertFalse(content.contains("@Suppress("))
                assertFalse(content.contains("@file:Suppress("))
            }
    }

    @Test
    fun `legacy dosing feature facade remains in guarded feature package`() {
        assertTrue(File(repositoryRoot, DOSING_PUMP_ROOT + "DosingPumpDeviceCompose.kt").isFile)
        assertTrue(File(repositoryRoot, DOSING_PUMP_ROOT + "DosingPumpIndicatorDrawing.kt").isFile)
        assertTrue(File(repositoryRoot, DOSING_PUMP_ROOT + "DosingPumpPalette.kt").isFile)
        assertTrue(File(repositoryRoot, DOSING_PUMP_ROOT + "DosingPumpSection.kt").isFile)
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
        const val SHARED_DOSING_VISUAL_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/common/devicevisual/dosing/"
        const val DOSING_PUMP_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/presentation/pump/"
        const val DOSING_CARD_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/presentation/card/"
        const val TANK_DEVICE_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/devices/"
    }
}
