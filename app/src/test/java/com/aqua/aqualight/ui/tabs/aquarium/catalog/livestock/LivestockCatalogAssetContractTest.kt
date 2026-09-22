package com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivestockCatalogAssetContractTest {

    private val repositoryRoot: File = locateRepositoryRoot()
    private val assetFile = File(
        repositoryRoot,
        "app/src/main/assets/livestock_catalog.jsonl"
    )

    @Test
    fun commercialCoreContainsTheExpectedCategoryCoverage() {
        val lines = assetFile.readLines()
            .filter(String::isNotBlank)

        assertEquals(687, lines.size)
        assertEquals(426, lines.count { line -> line.contains("\"category\":\"Fish\"") })
        assertEquals(44, lines.count { line -> line.contains("\"category\":\"Shrimp\"") })
        assertEquals(34, lines.count { line -> line.contains("\"category\":\"Snail\"") })
        assertEquals(35, lines.count { line -> line.contains("\"category\":\"Crab / Crayfish\"") })
        assertEquals(103, lines.count { line -> line.contains("\"category\":\"Coral\"") })
        assertEquals(45, lines.count { line -> line.contains("\"category\":\"Other\"") })
    }

    @Test
    fun commercialCoreDoesNotEmbedResearchSourcesOrUrls() {
        val raw = assetFile.readText()

        assertFalse(raw.contains("http://", ignoreCase = true))
        assertFalse(raw.contains("https://", ignoreCase = true))
        assertFalse(raw.contains("sourceUrl", ignoreCase = true))
        assertFalse(raw.contains("sources", ignoreCase = true))
    }

    @Test
    fun representativeCommercialEntriesRemainPresent() {
        val raw = assetFile.readText()

        assertTrue(raw.contains("Paracheirodon innesi"))
        assertTrue(raw.contains("Caridina multidentata"))
        assertTrue(raw.contains("Pomacea diffusa"))
        assertTrue(raw.contains("Cambarellus patzcuarensis"))
        assertTrue(raw.contains("Euphyllia glabrescens"))
        assertTrue(raw.contains("Ambystoma mexicanum"))
    }

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile

        repeat(8) {
            val current = candidate ?: return@repeat
            if (
                File(current, "settings.gradle").isFile ||
                File(current, "settings.gradle.kts").isFile
            ) {
                return current
            }
            candidate = current.parentFile
        }

        error("Unable to locate repository root from ${System.getProperty("user.dir")}")
    }
}