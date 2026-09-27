package com.aqua.aqualight.application.aquarium.health

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterMethodProfileTest {
    @Test
    fun profileRequiresExactIdentityMatrixModeAndPrimaryEvidence() {
        val profile = WaterMethodProfileFixture.profile()
        assertThrows(IllegalArgumentException::class.java) { profile.copy(matrices = emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { profile.copy(modes = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { profile.copy(evidence = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { profile.product.copy(model = "") }
        assertThrows(IllegalArgumentException::class.java) { profile.key.copy(productId = "Localized Name") }
        assertThrows(IllegalArgumentException::class.java) { profile.key.copy(revision = 0) }
    }

    @Test
    fun evidenceRequiresDocumentRevisionAndChronologicalDates() {
        val evidence = WaterMethodProfileFixture.profile().evidence.single()
        assertThrows(IllegalArgumentException::class.java) { evidence.copy(documentRevision = " ") }
        assertThrows(IllegalArgumentException::class.java) { evidence.copy(url = "file:///manual.pdf") }
        assertThrows(IllegalArgumentException::class.java) { evidence.copy(url = "https:///manual.pdf") }
        assertThrows(IllegalArgumentException::class.java) {
            evidence.copy(publishedOn = evidence.retrievedOn.plusDays(1))
        }
    }

    @Test
    fun concurrentTanAndDirectAmmoniaAreSeparateOutputsWithoutAnExclusivePicker() {
        val tan = WaterMethodProfileFixture.output().copy(
            id = "total_ammonia",
            semantic = WaterMethodSourceSemantic(
                WaterParameter.TOTAL_AMMONIA_NITROGEN, WaterMeasurementBasis.TAN_N,
                WaterMeasurementUnit.MG_L, WaterMethodAnalyticalScope.NAMED_PARAMETER
            )
        )
        val free = tan.copy(id = "free_ammonia", semantic = tan.semantic.copy(
            parameter = WaterParameter.FREE_AMMONIA_NH3, basis = WaterMeasurementBasis.FREE_NH3
        ))
        val profile = WaterMethodProfileFixture.profile().copy(
            modes = listOf(mode("standard", listOf(tan, free)))
        )
        assertFalse(profile.requiresModeSelection)
        assertTrue(profile.copy(modes = listOf(
            mode("total_only", listOf(tan)), mode("free_only", listOf(free))
        )).requiresModeSelection)
    }

    @Test
    fun duplicateModesAndOverlappingConcurrentParametersAreRejected() {
        val profile = WaterMethodProfileFixture.profile()
        val mode = profile.modes.single()
        assertThrows(IllegalArgumentException::class.java) { profile.copy(modes = listOf(mode, mode)) }
        assertThrows(IllegalArgumentException::class.java) { mode.copy(outputs = mode.outputs + mode.outputs) }
        assertThrows(IllegalArgumentException::class.java) {
            mode.copy(outputs = mode.outputs + mode.outputs.single().copy(id = "other_channel"))
        }
    }

    @Test
    fun legacyAmmoniaAndMismatchedChemicalBasisAreNotPublishable() {
        val semantic = WaterMethodProfileFixture.output().semantic
        assertThrows(IllegalArgumentException::class.java) { semantic.copy(basis = WaterMeasurementBasis.FE) }
        assertThrows(IllegalArgumentException::class.java) {
            semantic.copy(parameter = WaterParameter.AMMONIA_AMMONIUM, basis = WaterMeasurementBasis.NH3_NH4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            semantic.copy(parameter = WaterParameter.IRON, basis = WaterMeasurementBasis.FE)
        }
    }

    @Test
    fun reportingRangesLimitsAndPrecisionMustBeInternallyConsistent() {
        val scale = WaterMethodProfileFixture.output().scale
        assertThrows(IllegalArgumentException::class.java) {
            scale.range.copy(maximum = scale.range.minimum)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WaterMethodDetectionLimits("2".toBigDecimal(), "1".toBigDecimal())
        }
        assertThrows(IllegalArgumentException::class.java) {
            scale.copy(limits = WaterMethodDetectionLimits("101".toBigDecimal(), null))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WaterMethodPrecision.Increment("0".toBigDecimal())
        }
        assertThrows(IllegalArgumentException::class.java) {
            WaterMethodPrecision.ComparatorScale(listOf("1".toBigDecimal(), "1.0".toBigDecimal()))
        }
        assertThrows(IllegalArgumentException::class.java) { scale.copy(qualifiers = emptySet()) }
    }

    private fun mode(id: String, outputs: List<WaterMethodOutput>) =
        WaterMethodMode(id, setOf(WaterSampleMatrix.FRESHWATER), outputs)
}

/** Synthetic schema fixture; never shipped as a selectable scientific product. */
internal object WaterMethodProfileFixture {
    fun profile() = WaterMethodProfile(
        key = WaterMethodProfileKey("schema_fixture_nitrate", 1),
        product = WaterMethodProduct("Schema fixture", "Exact nitrate model", "Standard variant"),
        matrices = setOf(WaterSampleMatrix.FRESHWATER),
        modes = listOf(WaterMethodMode("standard", setOf(WaterSampleMatrix.FRESHWATER), listOf(output()))),
        evidence = listOf(WaterMethodEvidence(
            kind = WaterMethodEvidenceKind.MANUFACTURER_MANUAL,
            url = "https://example.invalid/schema-fixture/manual",
            documentRevision = "fixture-1",
            section = "Result scale",
            retrievedOn = LocalDate.parse("2026-09-27")
        ))
    )

    fun output() = WaterMethodOutput(
        id = "nitrate",
        semantic = WaterMethodSourceSemantic(
            WaterParameter.NITRATE, WaterMeasurementBasis.NO3, WaterMeasurementUnit.MG_L,
            WaterMethodAnalyticalScope.NAMED_PARAMETER
        ),
        scale = WaterMethodResultScale(
            range = WaterMethodNumericRange("0".toBigDecimal(), "100".toBigDecimal()),
            precision = WaterMethodPrecision.Increment("1".toBigDecimal()),
            limits = WaterMethodDetectionLimits("1".toBigDecimal(), "2".toBigDecimal()),
            qualifiers = setOf(WaterResultQualifier.EXACT, WaterResultQualifier.LESS_THAN)
        )
    )
}
