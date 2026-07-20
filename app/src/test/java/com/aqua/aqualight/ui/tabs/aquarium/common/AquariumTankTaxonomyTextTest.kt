package com.aqua.aqualight.ui.tabs.aquarium.common

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Visible labels may change with language; persisted taxonomy codes must never change. */
class AquariumTankTaxonomyTextTest {
    private val turkishLabels = mapOf(
        R.string.aquarium_tank_type_fish to "Balık",
        R.string.aquarium_tank_type_shrimp to "Karides",
        R.string.aquarium_tank_type_planted to "Bitkili",
        R.string.aquarium_tank_type_marine to "Deniz",
        R.string.aquarium_tank_type_softies to "Yumuşak Mercan",
        R.string.aquarium_tank_type_mixed_reef to "Karma Resif",
        R.string.aquarium_tank_type_sps to "SPS",
        R.string.aquarium_tank_type_coral to "Mercan",
        R.string.aquarium_tank_type_other to "Diğer",
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
    fun translatedTankTypeIsConvertedToStableCode() {
        assertEquals(
            AquariumTankTaxonomy.TYPE_SHRIMP,
            AquariumTankTaxonomyText.canonicalTankType("Karides", ::labelFor)
        )
        assertEquals(
            AquariumTankTaxonomy.TYPE_SHRIMP,
            AquariumTankTaxonomyText.canonicalTankType("Shrimp", ::labelFor)
        )
        assertEquals(
            "Karides",
            AquariumTankTaxonomyText.tankTypeLabel("Shrimp", ::labelFor)
        )
        assertNull(AquariumTankTaxonomyText.canonicalTankType("Bilinmeyen", ::labelFor))
    }

    @Test
    fun presetStyleUsesStableCodeWhileCustomStyleRemainsUserText() {
        assertEquals(
            AquariumTankTaxonomy.STYLE_DUTCH,
            AquariumTankTaxonomyText.canonicalTankStyle("Hollanda", ::labelFor)
        )
        assertEquals(
            "Hollanda",
            AquariumTankTaxonomyText.tankStyleLabel("Dutch", ::labelFor)
        )
        assertEquals(
            "Benim Stilim",
            AquariumTankTaxonomyText.canonicalTankStyle(" Benim Stilim ", ::labelFor)
        )
    }
}
