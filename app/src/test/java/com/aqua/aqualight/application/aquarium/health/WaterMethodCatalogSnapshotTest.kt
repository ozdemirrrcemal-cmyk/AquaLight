package com.aqua.aqualight.application.aquarium.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterMethodCatalogSnapshotTest {
    @Test
    fun exactRevisionIsRequiredAndDuplicatePublicationIsRejected() {
        val profile = WaterMethodProfileFixture.profile()
        val catalog = catalog(profile)
        assertEquals(profile, catalog.resolve(profile.key))
        assertNull(catalog.resolve(profile.key.copy(revision = 2)))
        assertThrows(IllegalArgumentException::class.java) {
            WaterMethodCatalogSnapshot(1, listOf(profile, profile), setOf(profile.key))
        }
    }

    @Test
    fun updatesRetainOriginalRevisionAndRetirementOnlyAffectsNewSelection() {
        val original = WaterMethodProfileFixture.profile()
        val updated = original.copy(key = original.key.copy(revision = 2))
        val next = catalog(original).successor(2, listOf(original, updated), setOf(updated.key))
        assertEquals(original, next.resolve(original.key))
        assertEquals(updated, next.selectableFor(WaterParameter.NITRATE, WaterSampleMatrix.FRESHWATER).single())
        val retired = catalog(original).successor(2, listOf(original), emptySet())
        assertEquals(original, retired.resolve(original.key))
        assertTrue(retired.selectableFor(WaterParameter.NITRATE, WaterSampleMatrix.FRESHWATER).isEmpty())
    }

    @Test
    fun publishedRevisionCannotBeRewrittenOrRemoved() {
        val original = WaterMethodProfileFixture.profile()
        val changed = original.copy(evidence = original.evidence.map { it.copy(documentRevision = "changed") })
        assertThrows(IllegalArgumentException::class.java) {
            catalog(original).successor(2, listOf(changed), setOf(changed.key))
        }
        assertThrows(IllegalArgumentException::class.java) {
            catalog(original).successor(2, emptyList(), emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            catalog(original).successor(1, listOf(original), setOf(original.key))
        }
    }

    @Test
    fun stableProductIdCannotSwitchModelAndOnlyOneRevisionIsSelectable() {
        val original = WaterMethodProfileFixture.profile()
        val updated = original.copy(key = original.key.copy(revision = 2))
        assertThrows(IllegalArgumentException::class.java) {
            WaterMethodCatalogSnapshot(2, listOf(original, updated), setOf(original.key, updated.key))
        }
        val differentModel = updated.copy(product = updated.product.copy(model = "Different exact model"))
        assertThrows(IllegalArgumentException::class.java) {
            WaterMethodCatalogSnapshot(2, listOf(original, differentModel), setOf(differentModel.key))
        }
    }

    @Test
    fun unsupportedMatrixAndParameterNeverSelectAProfile() {
        val profile = WaterMethodProfileFixture.profile()
        assertTrue(catalog(profile).selectableFor(WaterParameter.NITRATE, WaterSampleMatrix.MARINE).isEmpty())
        assertTrue(catalog(profile).selectableFor(WaterParameter.IRON, WaterSampleMatrix.FRESHWATER).isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            WaterMethodCatalogSnapshot(1, listOf(profile), setOf(profile.key.copy(revision = 2)))
        }
    }

    @Test
    fun publishedCollectionsAreIsolatedFromCallersAndCannotBeMutated() {
        val source = WaterMethodProfileFixture.profile()
        val matrices = source.matrices.toMutableSet()
        val modes = source.modes.toMutableList()
        val keys = mutableSetOf(source.key)
        val profiles = mutableListOf(source.copy(matrices = matrices, modes = modes))
        val catalog = WaterMethodCatalogSnapshot(1, profiles, keys)
        matrices.clear()
        modes.clear()
        keys.clear()
        profiles.clear()
        val frozen = requireNotNull(catalog.resolve(source.key))
        assertEquals(source, frozen)
        assertEquals(setOf(source.key), catalog.selectableKeys)
        assertThrows(UnsupportedOperationException::class.java) {
            (frozen.modes as MutableList).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (frozen.modes.single().outputs as MutableList).clear()
        }
    }

    @Test
    fun comparatorAndQualifierCollectionsAlsoRemainFrozen() {
        val values = mutableListOf("0".toBigDecimal(), "10".toBigDecimal())
        val qualifiers = mutableSetOf(WaterResultQualifier.EXACT)
        val output = WaterMethodProfileFixture.output().let { value ->
            value.copy(scale = value.scale.copy(
                precision = WaterMethodPrecision.ComparatorScale(values), qualifiers = qualifiers
            ))
        }
        val profile = WaterMethodProfileFixture.profile().let { value ->
            value.copy(modes = listOf(value.modes.single().copy(outputs = listOf(output))))
        }
        val frozen = requireNotNull(catalog(profile).resolve(profile.key)).modes.single().outputs.single().scale
        values.clear()
        qualifiers.clear()
        assertEquals(2, (frozen.precision as WaterMethodPrecision.ComparatorScale).values.size)
        assertEquals(setOf(WaterResultQualifier.EXACT), frozen.qualifiers)
    }

    @Test
    fun aResultModeCannotBorrowTheWaterMatrixOfAnotherMode() {
        val source = WaterMethodProfileFixture.profile()
        val marineOutput = WaterMethodProfileFixture.output().let { output ->
            output.copy(semantic = output.semantic.copy(
                parameter = WaterParameter.NITRITE, basis = WaterMeasurementBasis.NO2
            ))
        }
        val profile = source.copy(
            matrices = setOf(WaterSampleMatrix.FRESHWATER, WaterSampleMatrix.MARINE),
            modes = source.modes + WaterMethodMode(
                "marine", setOf(WaterSampleMatrix.MARINE), listOf(marineOutput)
            )
        )
        assertTrue(catalog(profile).selectableFor(WaterParameter.NITRITE, WaterSampleMatrix.FRESHWATER).isEmpty())
        assertEquals(listOf(profile), catalog(profile).selectableFor(WaterParameter.NITRITE, WaterSampleMatrix.MARINE))
    }

    private fun catalog(profile: WaterMethodProfile) =
        WaterMethodCatalogSnapshot(1, listOf(profile), setOf(profile.key))
}
