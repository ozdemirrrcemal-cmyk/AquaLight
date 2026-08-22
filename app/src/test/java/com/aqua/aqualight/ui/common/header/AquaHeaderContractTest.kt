package com.aqua.aqualight.ui.common.header

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AquaHeaderContractTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `header title is an explicit compile time contract`() {
        val configSource = source(
            "app/src/main/java/com/aqua/aqualight/ui/common/header/AquaHeaderConfig.kt"
        )
        val bindingSource = source(
            "app/src/main/java/com/aqua/aqualight/ui/common/header/AquaHeaderBindingExt.kt"
        )

        assertTrue(configSource.contains("val title: String,"))
        assertFalse(configSource.contains("titleOverride"))
        assertTrue(bindingSource.contains("config: AquaHeaderConfig\n"))
        assertFalse(bindingSource.contains("config: AquaHeaderConfig ="))
        assertTrue(bindingSource.contains("tvTitle.text = config.title"))
    }

    @Test
    fun `header title never depends on global navigation destination`() {
        val bindingSource = source(
            "app/src/main/java/com/aqua/aqualight/ui/common/header/AquaHeaderBindingExt.kt"
        )

        assertFalse(bindingSource.contains("currentDestination"))
        assertFalse(bindingSource.contains("AquaHeaderTitleOwner"))
        assertFalse(bindingSource.contains("getTag("))
        assertFalse(bindingSource.contains("setTag("))
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
