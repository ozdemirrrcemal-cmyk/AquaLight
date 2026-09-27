package com.aqua.aqualight.application.aquarium.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaterMeasurementSemanticMatrixTest {
    @Test
    fun everyParameterBasisAndUnitCombinationMatchesTheReviewedAllowlist() {
        WaterParameter.entries.forEach { parameter ->
            WaterMeasurementBasis.entries.forEach { basis ->
                WaterMeasurementUnit.entries.forEach { unit ->
                    val expected = allowedConversions.firstOrNull {
                        it.parameter == parameter && it.basis == basis && it.unit == unit
                    }?.factor
                    assertEquals(
                        "$parameter / $basis / $unit",
                        expected,
                        WaterMeasurementNormalizer.canonicalValue(parameter, 1.0, basis, unit)
                    )
                }
            }
        }
    }

    @Test
    fun sourceKindCannotAcquireCanonicalAuthorityFromAUnitMatch() {
        allowedConversions.forEach { case ->
            WaterMeasurementMethod.entries.filter { it != WaterMeasurementMethod.MANUAL }.forEach { method ->
                assertNull(WaterMeasurementNormalizer.canonicalValueForStoredSource(
                    case.parameter, 1.0,
                    WaterMeasurementSelection(method, null, case.basis, case.unit)
                ))
            }
        }
    }

    @Test
    fun invalidNumbersAndConversionUnderflowNeverProduceCanonicalZero() {
        allowedConversions.forEach { case ->
            listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0).forEach { value ->
                assertNull(WaterMeasurementNormalizer.canonicalValue(case.parameter, value, case.basis, case.unit))
            }
            assertEquals(0.0, requireNotNull(WaterMeasurementNormalizer.canonicalValue(
                case.parameter, 0.0, case.basis, case.unit
            )), 0.0)
        }
        assertNull(WaterMeasurementNormalizer.canonicalValue(
            WaterParameter.TOTAL_ALKALINITY, Double.MIN_VALUE,
            WaterMeasurementBasis.TOTAL_ALKALINITY, WaterMeasurementUnit.PPM_CACO3
        ))
    }

    @Test
    fun aSourceNativeSnapshotDoesNotClaimResolvedCanonicalSemantics() {
        val snapshot = WaterMeasurementSnapshot(
            resultId = WaterMeasurementResultId(1L, WaterParameter.NITRATE),
            parameter = WaterParameter.NITRATE,
            value = 1.0,
            method = WaterMeasurementMethod.TEST_KIT,
            testKitId = WaterMeasurementCatalog.OTHER_TEST_KIT_ID,
            basis = WaterMeasurementBasis.NO3,
            unit = WaterMeasurementUnit.MG_L,
            canonicalValue = null,
            canonicalBasis = WaterMeasurementBasis.NO3,
            canonicalUnit = WaterMeasurementUnit.MG_L
        )
        assertEquals(WaterMeasurementSemanticStatus.SOURCE_NATIVE_UNASSESSED, snapshot.semanticStatus)
        assertEquals(WaterMeasurementSemanticStatus.SOURCE_TYPED, snapshot.copy(
            method = WaterMeasurementMethod.MANUAL, testKitId = null, canonicalValue = 1.0
        ).semanticStatus)
    }

    private data class ConversionCase(
        val parameter: WaterParameter,
        val basis: WaterMeasurementBasis,
        val unit: WaterMeasurementUnit,
        val factor: Double = 1.0
    )

    private val allowedConversions = listOf(
        ConversionCase(WaterParameter.PH, WaterMeasurementBasis.PH, WaterMeasurementUnit.NONE),
        ConversionCase(WaterParameter.NITRATE, WaterMeasurementBasis.NO3, WaterMeasurementUnit.MG_L),
        ConversionCase(WaterParameter.NITRITE, WaterMeasurementBasis.NO2, WaterMeasurementUnit.MG_L),
        ConversionCase(WaterParameter.TOTAL_AMMONIA_NITROGEN, WaterMeasurementBasis.TAN_N, WaterMeasurementUnit.MG_L),
        ConversionCase(WaterParameter.FREE_AMMONIA_NH3, WaterMeasurementBasis.FREE_NH3, WaterMeasurementUnit.MG_L),
        ConversionCase(WaterParameter.GH, WaterMeasurementBasis.GH, WaterMeasurementUnit.PPM_CACO3),
        ConversionCase(WaterParameter.GH, WaterMeasurementBasis.GH, WaterMeasurementUnit.DGH, GH_FACTOR),
        ConversionCase(
            WaterParameter.TOTAL_ALKALINITY, WaterMeasurementBasis.TOTAL_ALKALINITY, WaterMeasurementUnit.MEQ_L
        ),
        ConversionCase(
            WaterParameter.TOTAL_ALKALINITY, WaterMeasurementBasis.TOTAL_ALKALINITY,
            WaterMeasurementUnit.DKH, ALKALINITY_DKH_FACTOR
        ),
        ConversionCase(
            WaterParameter.TOTAL_ALKALINITY, WaterMeasurementBasis.TOTAL_ALKALINITY,
            WaterMeasurementUnit.PPM_CACO3, ALKALINITY_CACO3_FACTOR
        ),
        ConversionCase(WaterParameter.PHOSPHATE, WaterMeasurementBasis.PO4, WaterMeasurementUnit.MG_L),
        ConversionCase(WaterParameter.POTASSIUM, WaterMeasurementBasis.K, WaterMeasurementUnit.MG_L),
        ConversionCase(WaterParameter.CALCIUM, WaterMeasurementBasis.CA, WaterMeasurementUnit.MG_L),
        ConversionCase(WaterParameter.MAGNESIUM, WaterMeasurementBasis.MG, WaterMeasurementUnit.MG_L),
        ConversionCase(WaterParameter.COPPER, WaterMeasurementBasis.CU, WaterMeasurementUnit.MG_L),
        ConversionCase(WaterParameter.DISSOLVED_OXYGEN, WaterMeasurementBasis.O2, WaterMeasurementUnit.MG_L)
    )

    private companion object {
        const val GH_FACTOR = 17.86
        const val ALKALINITY_DKH_FACTOR = 0.358
        const val ALKALINITY_CACO3_FACTOR = 0.02
    }
}
