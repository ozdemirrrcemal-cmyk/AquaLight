package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingCardPrecisionResourceTest {

    @Test
    fun `card amount resources preserve firmware milliliter quantum`() {
        val root = locateRepositoryRoot()
        val english = File(
            root,
            "app/src/main/res/values/device_dosing_card_data_formats.xml"
        ).readText()
        val turkish = File(
            root,
            "app/src/main/res/values-tr/device_dosing_card_data_formats.xml"
        ).readText()

        listOf(english, turkish).forEach { resources ->
            assertTrue(resources.contains("%1$.3f"))
            assertTrue(resources.contains("%2$.3f"))
            assertTrue(resources.contains("%4$.3f/%5$.3f"))
            assertFalse(resources.contains("%.2f"))
        }
    }

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
