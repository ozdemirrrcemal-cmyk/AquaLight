package com.aqua.aqualight.ui.tabs.devices

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicesHeaderTransitionArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `devices header stays owned by devices screen during navigation transition`() {
        val source = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/DevicesFragment.kt"
        ).readText()

        assertTrue(
            source.contains(
                "titleOverride = getString(R.string.screen_title_devices)"
            )
        )
        assertTrue(
            source.contains(
                "deviceTitle = route.title.ifBlank { getString(route.titleRes) }"
            )
        )
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
