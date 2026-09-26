package com.aqua.aqualight.ui.tabs.aquarium.common

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Visible labels may change with language; persisted taxonomy codes must never change. */
class AquariumTankTaxonomyTextTest {
    private val turkishLabels = mapOf(
        R.string.aquarium_water_environment_freshwater to "Tatlı Su",
        R.string.aquarium_water_environment_brackish to "Acı Su",
        R.string.aquarium_water_environment_marine to "Deniz",
        R.string.aquarium_tank_profile_freshwater_fish to "Balık / Genel",
        R.string.aquarium_tank_profile_planted to "Bitkili",
        R.string.aquarium_tank_profile_shrimp to "Karides",
        R.string.aquarium_tank_profile_brackish_general to "Acı Su",
        R.string.aquarium_tank_profile_marine_fish to "Balık / FOWLR",
        R.string.aquarium_tank_profile_soft_coral_reef to "Yumuşak Mercan",
        R.string.aquarium_tank_profile_lps_reef to "LPS Resif",
        R.string.aquarium_tank_profile_sps_reef to "SPS Resif",
        R.string.aquarium_tank_profile_mixed_reef to "Karma Resif",
        R.string.aquarium_tank_profile_other to "Diğer",
        R.string.aquarium_text_nature_aquarium to "Doğa Akvaryumu",
        R.string.aquarium_style_iwagumi to "Iwagumi",
        R.string.aquarium_style_dutch to "Hollanda",
        R.string.aquarium_style_jungle to "Jungle",
        R.string.aquarium_style_biotope to "Biyotop",
        R.string.aquarium_style_blackwater to "Blackwater",
        R.string.aquarium_style_forest to "Orman",
        R.string.aquarium_style_mountain to "Dağ",
        R.string.aquarium_style_island to "Ada"
    )

    private fun labelFor(resId: Int): String = requireNotNull(turkishLabels[resId])

    @Test
    fun translatedEnvironmentAndTankProfileUseStableCodes() {
        assertEquals(
            AquariumTankTaxonomy.WATER_ENVIRONMENT_BRACKISH,
            AquariumTankTaxonomyTextResolver.canonicalWaterEnvironment(
                "Acı Su",
                ::labelFor
            )
        )
        assertEquals(
            AquariumTankTaxonomy.TYPE_SHRIMP,
            AquariumTankTaxonomyTextResolver.canonicalTankType("Karides", ::labelFor)
        )
        assertEquals(
            AquariumTankTaxonomy.TYPE_SHRIMP,
            AquariumTankTaxonomyTextResolver.canonicalTankType("Shrimp", ::labelFor)
        )
        assertEquals(
            "Karides",
            AquariumTankTaxonomyTextResolver.tankTypeLabel("Shrimp", ::labelFor)
        )
        assertNull(
            AquariumTankTaxonomyTextResolver.canonicalTankType(
                "Bilinmeyen",
                ::labelFor
            )
        )
    }

    @Test
    fun ambiguousOtherLabelCannotBecomeAnArbitraryStableProfile() {
        assertNull(
            AquariumTankTaxonomyTextResolver.canonicalTankType(
                "Diğer",
                ::labelFor
            )
        )
        assertEquals(
            "Diğer",
            AquariumTankTaxonomyTextResolver.tankTypeLabel(
                AquariumTankTaxonomy.TYPE_OTHER_MARINE,
                ::labelFor
            )
        )
    }

    @Test
    fun presetStyleUsesStableCodeWhileCustomStyleRemainsUserText() {
        assertEquals(
            AquariumTankTaxonomy.STYLE_DUTCH,
            AquariumTankTaxonomyTextResolver.canonicalTankStyle(
                "Hollanda",
                ::labelFor
            )
        )
        assertEquals(
            "Hollanda",
            AquariumTankTaxonomyTextResolver.tankStyleLabel("Dutch", ::labelFor)
        )
        assertEquals(
            "Benim Stilim",
            AquariumTankTaxonomyTextResolver.canonicalTankStyle(
                " Benim Stilim ",
                ::labelFor
            )
        )
    }
}
