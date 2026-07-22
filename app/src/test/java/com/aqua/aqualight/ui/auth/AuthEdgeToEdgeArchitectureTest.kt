package com.aqua.aqualight.ui.auth

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthEdgeToEdgeArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun authBackdropIsFullBleedWhileForegroundOwnsSafeAndImeInsets() {
        val shell = source(
            "app/src/main/java/com/aqua/aqualight/ui/main/AquaAppShellLayout.kt"
        )
        val container = source(
            "app/src/main/java/com/aqua/aqualight/ui/auth/AuthContainerFragment.kt"
        )
        val layout = source("app/src/main/res/layout/fragment_auth_container.xml")
        val activityLayout = source("app/src/main/res/layout/activity_main.xml")

        assertTrue(shell.contains("fun setContentDrawsBehindSystemBars(enabled: Boolean)"))
        assertTrue(shell.contains("val contentInsets = if (contentDrawsBehindSystemBars)"))
        assertTrue(container.contains("setContentDrawsBehindSystemBars(true)"))
        assertTrue(container.contains("safeContent?.updatePadding"))
        assertTrue(container.contains("WindowInsetsCompat.Type.systemBars()"))
        assertTrue(container.contains("WindowInsetsCompat.Type.displayCutout()"))
        assertTrue(container.contains("WindowInsetsCompat.Type.ime()"))
        assertTrue(container.contains("topSystemBarScrim.setInsetScrimHeight"))
        assertTrue(container.contains("bottomSystemBarScrim.setInsetScrimHeight"))

        assertTrue(activityLayout.contains("android:id=\"@+id/appShell\""))
        assertTrue(layout.contains("android:id=\"@+id/authSafeContent\""))
        assertTrue(layout.contains("android:id=\"@+id/authTopSystemBarScrim\""))
        assertTrue(layout.contains("android:id=\"@+id/authBottomSystemBarScrim\""))
        assertTrue(
            layout.indexOf("@+id/videoBackground") <
                layout.indexOf("@+id/authSafeContent")
        )
    }

    @Test
    fun leavingAuthRestoresTheDefaultSystemBarContract() {
        val shell = source(
            "app/src/main/java/com/aqua/aqualight/ui/main/AquaAppShellLayout.kt"
        )
        val container = source(
            "app/src/main/java/com/aqua/aqualight/ui/auth/AuthContainerFragment.kt"
        )

        assertTrue(shell.contains("defaultSystemBarAppearance"))
        assertTrue(shell.contains("R.color.aqua_color_transparent"))
        assertFalse(shell.contains("Color.TRANSPARENT"))
        assertTrue(shell.contains("window.statusBarColor = transparentColor"))
        assertTrue(shell.contains("window.navigationBarColor = transparentColor"))
        assertTrue(shell.contains("window.isStatusBarContrastEnforced = false"))
        assertTrue(shell.contains("window.isNavigationBarContrastEnforced = false"))
        assertTrue(shell.contains("window.statusBarColor = appearance.statusBarColor"))
        assertTrue(
            shell.contains(
                "window.navigationBarColor = appearance.navigationBarColor"
            )
        )
        assertTrue(container.contains("setContentDrawsBehindSystemBars(false)"))
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
