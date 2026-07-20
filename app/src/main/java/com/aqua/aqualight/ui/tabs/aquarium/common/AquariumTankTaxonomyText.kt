package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy

object AquariumTankTaxonomyText {
    private data class Choice(val code: String, @StringRes val labelRes: Int)

    private val tankTypes = listOf(
        Choice(AquariumTankTaxonomy.TYPE_FISH, R.string.aquarium_tank_type_fish),
        Choice(AquariumTankTaxonomy.TYPE_SHRIMP, R.string.aquarium_tank_type_shrimp),
        Choice(AquariumTankTaxonomy.TYPE_PLANTED, R.string.aquarium_tank_type_planted),
        Choice(AquariumTankTaxonomy.TYPE_MARINE, R.string.aquarium_tank_type_marine),
        Choice(AquariumTankTaxonomy.TYPE_SOFTIES, R.string.aquarium_tank_type_softies),
        Choice(AquariumTankTaxonomy.TYPE_MIXED_REEF, R.string.aquarium_tank_type_mixed_reef),
        Choice(AquariumTankTaxonomy.TYPE_SPS, R.string.aquarium_tank_type_sps),
        Choice(AquariumTankTaxonomy.TYPE_CORAL, R.string.aquarium_tank_type_coral),
        Choice(AquariumTankTaxonomy.TYPE_OTHER, R.string.aquarium_tank_type_other)
    )

    private val presetStyles = listOf(
        Choice(AquariumTankTaxonomy.STYLE_NATURE_AQUARIUM, R.string.aquarium_text_nature_aquarium),
        Choice(AquariumTankTaxonomy.STYLE_IWAGUMI, R.string.aquarium_style_iwagumi),
        Choice(AquariumTankTaxonomy.STYLE_DUTCH, R.string.aquarium_style_dutch),
        Choice(AquariumTankTaxonomy.STYLE_JUNGLE, R.string.aquarium_style_jungle),
        Choice(AquariumTankTaxonomy.STYLE_BIOTOPE, R.string.aquarium_style_biotope),
        Choice(AquariumTankTaxonomy.STYLE_BLACKWATER, R.string.aquarium_style_blackwater),
        Choice(AquariumTankTaxonomy.STYLE_FOREST, R.string.aquarium_style_forest),
        Choice(AquariumTankTaxonomy.STYLE_MOUNTAIN, R.string.aquarium_style_mountain),
        Choice(AquariumTankTaxonomy.STYLE_ISLAND, R.string.aquarium_style_island)
    )

    fun canonicalTankType(context: Context, value: String): String? =
        canonical(value, tankTypes) { context.getString(it) }

    fun tankTypeLabel(context: Context, value: String): String =
        label(value, tankTypes) { context.getString(it) }

    fun canonicalTankStyle(context: Context, value: String): String {
        val trimmed = value.trim()
        return canonical(trimmed, presetStyles) { context.getString(it) } ?: trimmed
    }

    fun tankStyleLabel(context: Context, value: String): String =
        label(value, presetStyles) { context.getString(it) }

    internal fun canonicalTankType(value: String, labelFor: (Int) -> String): String? =
        canonical(value, tankTypes, labelFor)

    internal fun canonicalTankStyle(value: String, labelFor: (Int) -> String): String {
        val trimmed = value.trim()
        return canonical(trimmed, presetStyles, labelFor) ?: trimmed
    }

    internal fun tankTypeLabel(value: String, labelFor: (Int) -> String): String =
        label(value, tankTypes, labelFor)

    internal fun tankStyleLabel(value: String, labelFor: (Int) -> String): String =
        label(value, presetStyles, labelFor)

    private fun canonical(
        value: String,
        choices: List<Choice>,
        labelFor: (Int) -> String
    ): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return choices.firstOrNull { choice ->
            choice.code.equals(trimmed, ignoreCase = true) ||
                labelFor(choice.labelRes).trim().equals(trimmed, ignoreCase = true)
        }?.code
    }

    private fun label(
        value: String,
        choices: List<Choice>,
        labelFor: (Int) -> String
    ): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        val choice = choices.firstOrNull { choice ->
            choice.code.equals(trimmed, ignoreCase = true) ||
                labelFor(choice.labelRes).trim().equals(trimmed, ignoreCase = true)
        }
        return choice?.let { labelFor(it.labelRes) } ?: trimmed
    }
}
