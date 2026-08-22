package com.aqua.aqualight.ui.common.header

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AquaHeaderTitleOwnerTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `shared header binding stores one destination owner per header view`() {
        val bindingSource = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/common/header/AquaHeaderBindingExt.kt"
        ).readText()

        assertTrue(
            bindingSource.contains(
                "getTag(R.id.aqua_header_title_owner) as? AquaHeaderTitleOwner"
            )
        )
        assertTrue(
            bindingSource.contains("setTag(R.id.aqua_header_title_owner, owner)")
        )
        assertTrue(bindingSource.contains("val resolvedTitle = titleOwner.resolve("))
    }

    @Test
    fun `outgoing screen keeps its own title after navigation advances`() {
        val owner = AquaHeaderTitleOwner()

        assertEquals(
            "Devices",
            owner.resolve(
                titleOverride = null,
                currentDestinationTitle = "Devices"
            )
        )
        assertEquals(
            "Devices",
            owner.resolve(
                titleOverride = null,
                currentDestinationTitle = "Dosing"
            )
        )
    }

    @Test
    fun `temporary override does not replace captured destination title`() {
        val owner = AquaHeaderTitleOwner()
        owner.resolve(titleOverride = null, currentDestinationTitle = "Devices")

        assertEquals(
            "2 selected",
            owner.resolve(
                titleOverride = "2 selected",
                currentDestinationTitle = "Dosing"
            )
        )
        assertEquals(
            "Devices",
            owner.resolve(
                titleOverride = null,
                currentDestinationTitle = "Dosing"
            )
        )
    }

    @Test
    fun `explicit title remains available before destination label is ready`() {
        val owner = AquaHeaderTitleOwner()

        assertEquals(
            "Dose Pro 4",
            owner.resolve(
                titleOverride = "Dose Pro 4",
                currentDestinationTitle = ""
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
