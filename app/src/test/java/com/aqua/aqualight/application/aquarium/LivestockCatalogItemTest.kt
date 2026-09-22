package com.aqua.aqualight.application.aquarium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivestockCatalogItemTest {

    private val requirements = LivestockWaterRequirements(
        temperatureC = LivestockParameterRange(20.0, 26.0),
        ph = LivestockParameterRange(5.0, 7.5)
    )

    @Test
    fun displayNameUsesTurkishNameOnlyForTurkishLocaleWhenAvailable() {
        val item = catalogItem(
            commonName = "Harlequin Rasbora",
            turkishName = "Harlequin Rasbora TR"
        )

        assertEquals("Harlequin Rasbora TR", item.displayName("tr"))
        assertEquals("Harlequin Rasbora TR", item.displayName("TR"))
        assertEquals("Harlequin Rasbora", item.displayName("en"))
    }

    @Test
    fun displayNameFallsBackToCommonNameWhenTurkishNameIsBlankOrMissing() {
        assertEquals(
            "Neon Tetra",
            catalogItem(
                commonName = "Neon Tetra",
                turkishName = " "
            ).displayName("tr")
        )
        assertEquals(
            "Neon Tetra",
            catalogItem(
                commonName = "Neon Tetra",
                turkishName = null
            ).displayName("tr")
        )
    }

    @Test
    fun matchesSearchesCatalogIdentityFieldsCaseInsensitivelyAndTrimsQuery() {
        val item = catalogItem(
            commonName = "Neon Tetra",
            turkishName = "Neon Tetra",
            scientificName = "Paracheirodon innesi",
            recordType = "Species",
            waterGroup = "Freshwater"
        )

        assertTrue(item.matches(""))
        assertTrue(item.matches("   "))
        assertTrue(item.matches("neon"))
        assertTrue(item.matches("INNESI"))
        assertTrue(item.matches("species"))
        assertTrue(item.matches(" fresh "))
        assertFalse(item.matches("marine"))
    }

    @Test
    fun parameterSummaryIncludesOnlyAvailableNonBlankCatalogValues() {
        val full = catalogItem(
            temperatureC = "20–26",
            ph = "5.0–7.5",
            specificGravity = "1.023–1.026"
        )
        val partial = catalogItem(
            temperatureC = " ",
            ph = "6.5–7.5",
            specificGravity = null
        )

        assertEquals(
            "20–26 °C • pH 5.0–7.5 • SG 1.023–1.026",
            full.parameterSummary()
        )
        assertEquals("pH 6.5–7.5", partial.parameterSummary())
    }

    @Test
    fun waterRequirementHelpersExposeMeasuredCoverageAndWarningModeParsing() {
        assertTrue(requirements.hasAnyMeasuredRequirement)
        assertFalse(LivestockWaterRequirements().hasAnyMeasuredRequirement)
        assertEquals(
            LivestockWarningMode.HARD,
            LivestockWarningMode.fromCatalogValue(" hard ")
        )
        assertEquals(
            LivestockWarningMode.INFORMATIONAL,
            LivestockWarningMode.fromCatalogValue("INFORMATIONAL")
        )
        assertEquals(
            LivestockWarningMode.SOFT,
            LivestockWarningMode.fromCatalogValue("unknown")
        )
    }

    private fun catalogItem(
        commonName: String = "Neon Tetra",
        turkishName: String? = "Neon Tetra",
        scientificName: String? = "Paracheirodon innesi",
        recordType: String? = "Species",
        waterGroup: String? = "Freshwater",
        temperatureC: String? = "20–26",
        ph: String? = "5.0–7.5",
        specificGravity: String? = null
    ): LivestockCatalogItem {
        return LivestockCatalogItem(
            id = "catalog-neon-tetra",
            category = AquariumLivestockTaxonomy.FISH,
            commonName = commonName,
            turkishName = turkishName,
            scientificName = scientificName,
            recordType = recordType,
            waterGroup = waterGroup,
            temperatureC = temperatureC,
            ph = ph,
            specificGravity = specificGravity,
            waterRequirements = requirements
        )
    }
}
