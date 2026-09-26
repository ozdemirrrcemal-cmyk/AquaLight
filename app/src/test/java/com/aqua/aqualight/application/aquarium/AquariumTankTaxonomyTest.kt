package com.aqua.aqualight.application.aquarium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumTankTaxonomyTest {

    @Test
    fun everyTankProfileBelongsToExactlyOneWaterEnvironment() {
        val grouped = AquariumTankTaxonomy.waterEnvironmentCodes
            .flatMap(AquariumTankTaxonomy::tankTypeCodesForEnvironment)

        assertEquals(AquariumTankTaxonomy.tankTypeCodes.size, grouped.size)
        assertEquals(AquariumTankTaxonomy.tankTypeCodes, grouped.toSet())

        AquariumTankTaxonomy.tankTypeCodes.forEach { profile ->
            assertTrue(AquariumTankTaxonomy.environmentForTankType(profile) != null)
        }
    }

    @Test
    fun marineProfilesAreExplicitAndCoralIsNotAnAmbiguousProfile() {
        val marineProfiles = AquariumTankTaxonomy.tankTypeCodesForEnvironment(
            AquariumTankTaxonomy.WATER_ENVIRONMENT_MARINE
        )

        assertTrue(AquariumTankTaxonomy.TYPE_MARINE_FISH in marineProfiles)
        assertTrue(AquariumTankTaxonomy.TYPE_SOFT_CORAL_REEF in marineProfiles)
        assertTrue(AquariumTankTaxonomy.TYPE_LPS_REEF in marineProfiles)
        assertTrue(AquariumTankTaxonomy.TYPE_SPS_REEF in marineProfiles)
        assertTrue(AquariumTankTaxonomy.TYPE_MIXED_REEF in marineProfiles)
        assertFalse("Coral" in AquariumTankTaxonomy.tankTypeCodes)
        assertFalse("Marine" in AquariumTankTaxonomy.tankTypeCodes)
        assertNull(AquariumTankTaxonomy.environmentForTankType("Coral"))
    }

    @Test
    fun brackishIsNotSilentlyClassifiedAsFreshwaterOrMarine() {
        assertEquals(
            AquariumTankTaxonomy.WATER_ENVIRONMENT_BRACKISH,
            AquariumTankTaxonomy.environmentForTankType(
                AquariumTankTaxonomy.TYPE_BRACKISH_GENERAL
            )
        )
    }
}
