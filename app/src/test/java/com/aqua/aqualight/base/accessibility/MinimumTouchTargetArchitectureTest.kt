package com.aqua.aqualight.base.accessibility

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinimumTouchTargetArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun oneWindowHostOwnsReplacementAndStaleCleanup() {
        val installer = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/base/accessibility/" +
                "MinimumTouchTargetInstaller.kt"
        ).readText()
        val runtime = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/base/accessibility/" +
                "AccessibilityRuntimeInstaller.kt"
        ).readText()

        assertTrue(installer.contains("findViewById<View>(android.R.id.content)"))
        assertTrue(installer.contains("aqua_minimum_touch_target_delegate_owner"))
        assertTrue(installer.contains("host.touchDelegate = null"))
        assertTrue(installer.contains("MinimumTouchTargetSelector.selectIndex("))
        assertFalse(installer.contains("entries.firstOrNull"))
        assertTrue(runtime.contains("override fun onFragmentViewDestroyed("))

        val delegateOwners = File(repositoryRoot, "app/src/main/java")
            .walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "kt" }
            .filter { it.readText().contains(".touchDelegate =") }
            .mapTo(linkedSetOf()) { it.relativeTo(repositoryRoot).invariantSeparatorsPath }

        assertEquals(
            setOf(
                "app/src/main/java/com/aqua/aqualight/base/accessibility/" +
                    "MinimumTouchTargetInstaller.kt"
            ),
            delegateOwners
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
