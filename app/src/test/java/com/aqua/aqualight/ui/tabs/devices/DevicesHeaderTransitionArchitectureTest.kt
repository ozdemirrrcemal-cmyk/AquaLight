package com.aqua.aqualight.ui.tabs.devices

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicesHeaderTransitionArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `devices header stays owned by devices screen during navigation transition`() {
        val fragment = source("app/src/main/java/com/aqua/aqualight/ui/tabs/devices/DevicesFragment.kt")

        assertTrue(
            fragment.contains(
                "titleOverride = getString(R.string.screen_title_devices)"
            )
        )
        assertTrue(
            fragment.contains(
                "deviceTitle = route.title.ifBlank { getString(route.titleRes) }"
            )
        )
    }

    @Test
    fun `device preparation uses central loading without card busy mutation`() {
        val fragment = source("app/src/main/java/com/aqua/aqualight/ui/tabs/devices/DevicesFragment.kt")
        val viewModel = source("app/src/main/java/com/aqua/aqualight/ui/tabs/devices/DevicesViewModel.kt")

        assertTrue(fragment.contains("show = state.isPreparingDeviceMenu"))
        assertTrue(fragment.contains("binding.root.postOnAnimation"))
        assertTrue(viewModel.contains("controlSurfacePreparationOperations.prepare("))
        assertFalse(viewModel.contains("isBusy = operation.openingDeviceUid"))
    }

    @Test
    fun `dosing root has one initial refresh owner`() {
        val fragment = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/root/" +
                "DeviceDosingRootFragment.kt"
        )
        val viewModel = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/root/" +
                "DeviceDosingRootViewModel.kt"
        )

        assertFalse(fragment.contains("override fun onStart()"))
        assertTrue(viewModel.contains("consumeFreshPreparation("))
        assertTrue(viewModel.contains("CoroutineStart.UNDISPATCHED"))
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
