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
    fun everyUserVisibleTankMeasurementUsesTheSharedBoundaries() {
        val requiredFormatterSurfaces = listOf(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/create/steps/" +
                "TankInfoFragment.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/" +
                "TankDetailTankFragment.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/settings/" +
                "TankSettingsBasicFragment.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/" +
                "AquariumTankAdapter.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/careprofile/" +
                "CareProfileCalculator.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/export/" +
                "TankPdfExporter.kt"
        )

        requiredFormatterSurfaces.forEach { relativePath ->
            val source = File(repositoryRoot, relativePath).readText()
            assertTrue(
                "$relativePath must use AquariumDimensionFormatter.",
                source.contains("AquariumDimensionFormatter.")
            )
        }

        val editor = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/common/bottomsheet/" +
                "TankSettingsEditorBottomSheet.kt"
        ).readText()
        assertTrue(editor.contains("AquariumDimensionInputPolicy.parseCentimeters("))
        assertTrue(editor.contains("AquariumDimensionInputPolicy.convert("))
    }

    @Test
    fun measurementSurfacesCannotRestoreRawParsingOrLegacyIntegerFormats() {
        val productionRoot = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui"
        )
        val measurementFiles = productionRoot.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "kt" }
            .filter { file ->
                val source = file.readText()
                source.contains("widthCm") ||
                    source.contains("lengthCm") ||
                    source.contains("heightCm") ||
                    source.contains("sizeUnit") ||
                    source.contains("volumeUnit")
            }
            .toList()

        val forbiddenFragments = listOf(
            "toDoubleOrNull()",
            "DecimalFormat(",
            "NumberFormat.getNumberInstance(",
            ".replace(',', '.')",
            ".replace('.', ',')",
            "R.string.aquarium_tank_size_card_format",
            "R.string.tank_pdf_size_format"
        )

        val violations = measurementFiles.flatMap { file ->
            val source = file.readText()
            forbiddenFragments
                .filter(source::contains)
                .map { fragment ->
                    "${file.relativeTo(repositoryRoot).invariantSeparatorsPath}: $fragment"
                }
        }

        assertTrue(
            "Measurement code bypasses the commercial locale boundary: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun tankEditorKeepsTheExistingDecimalKeyboardFlow() {
        val layout = File(
            repositoryRoot,
            "app/src/main/res/layout/content_sheet_tank_size.xml"
        ).readText()
        val decimalInputs = Regex("android:inputType=\"numberDecimal\"")
            .findAll(layout)
            .count()

        assertEquals(3, decimalInputs)
        assertFalse(layout.contains("numberSigned"))
    }

    @Test
    fun pdfUsesSharedDimensionAndVolumePolicies() {
        val pdf = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/export/" +
                "TankPdfExporter.kt"
        ).readText()

        assertTrue(pdf.contains("AquariumDimensionFormatter.labeledSizeText("))
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
