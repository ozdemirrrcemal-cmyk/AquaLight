package com.aqua.aqualight.ui.tabs.aquarium.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumMeasurementArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun onlyTheSharedCalculatorOwnsThreeDimensionMultiplication() {
        val productionRoot = File(repositoryRoot, "app/src/main/java")
        val directVolumePattern = Regex(
            """(?:[A-Za-z0-9_]+\.)?widthCm(?:\.to(?:Double|Long)\(\))?\s*\*\s*(?:[A-Za-z0-9_]+\.)?lengthCm(?:\.to(?:Double|Long)\(\))?\s*\*\s*(?:[A-Za-z0-9_]+\.)?heightCm"""
        )
        val owners = productionRoot.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "kt" }
            .filter { directVolumePattern.containsMatchIn(it.readText()) }
            .mapTo(linkedSetOf()) { it.relativeTo(repositoryRoot).invariantSeparatorsPath }

        assertEquals(
            setOf(
                "app/src/main/java/com/aqua/aqualight/application/aquarium/" +
                    "AquariumVolumeCalculator.kt"
            ),
            owners
        )
    }

    @Test
    fun tankEditorAndPdfCannotBypassLocaleAndMeasurementBoundaries() {
        val editor = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/common/bottomsheet/" +
                "TankSettingsEditorBottomSheet.kt"
        ).readText()
        val pdf = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/export/" +
                "TankPdfExporter.kt"
        ).readText()

        assertTrue(editor.contains("AquariumDimensionInputPolicy.parseCentimeters("))
        assertTrue(editor.contains("AquariumDimensionInputPolicy.convert("))
        assertFalse(editor.contains("toDoubleOrNull()"))
        assertFalse(editor.contains("DecimalFormat("))

        assertTrue(pdf.contains("AquariumVolumeCalculator.grossLiters("))
        assertTrue(pdf.contains("LocaleFormatter.formatDecimal("))
        assertFalse(pdf.contains("DecimalFormat("))
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
