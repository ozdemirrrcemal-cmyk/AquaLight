package com.aqua.aqualight.application.aquarium.health

import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterTankMeasurementPolicyTest {
    @Test
    fun everyCanonicalTankTypeHasOneNonoverlappingMeasurementScope() {
        assertEquals(12, AquariumTankTaxonomy.tankTypeCodes.size)
        AquariumTankTaxonomy.tankTypeCodes.forEach { tankType ->
            val scope = WaterTankMeasurementPolicy.scopeFor(tankType)
            assertNotNull(tankType, scope)
            requireNotNull(scope)
            assertTrue(scope.recommended.isNotEmpty())
            assertTrue(scope.recommended.intersect(scope.additional.toSet()).isEmpty())
        }
    }

    @Test
    fun unknownTankTypeDoesNotInheritFreshwaterRules() {
        assertNull(WaterTankMeasurementPolicy.scopeFor(""))
        assertNull(WaterTankMeasurementPolicy.scopeFor("Freshwater"))
    }

    @Test
    fun everyLegacyParameterIsExplicitlyVisibleInAtLeastOneTankScope() {
        val visible = AquariumTankTaxonomy.tankTypeCodes.flatMap { type ->
            val scope = requireNotNull(WaterTankMeasurementPolicy.scopeFor(type))
            scope.recommended + scope.additional
        }.toSet()
        assertEquals(WaterParameter.entries.toSet(), visible)
    }
}
