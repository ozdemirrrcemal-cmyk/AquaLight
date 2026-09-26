package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy

private data class TaxonomyChoice(
    val code: String,
    @StringRes val labelRes: Int
)

private val waterEnvironmentChoices = listOf(
    TaxonomyChoice(
        AquariumTankTaxonomy.WATER_ENVIRONMENT_FRESHWATER,
        R.string.aquarium_water_environment_freshwater
    ),
    TaxonomyChoice(
        AquariumTankTaxonomy.WATER_ENVIRONMENT_BRACKISH,
        R.string.aquarium_water_environment_brackish
    ),
    TaxonomyChoice(
        AquariumTankTaxonomy.WATER_ENVIRONMENT_MARINE,
        R.string.aquarium_water_environment_marine
    )
)

private val tankTypeChoices = listOf(
    TaxonomyChoice(
        AquariumTankTaxonomy.TYPE_FRESHWATER_FISH,
        R.string.aquarium_tank_profile_freshwater_fish
    ),
    TaxonomyChoice(AquariumTankTaxonomy.TYPE_PLANTED, R.string.aquarium_tank_profile_planted),
    TaxonomyChoice(AquariumTankTaxonomy.TYPE_SHRIMP, R.string.aquarium_tank_profile_shrimp),
    TaxonomyChoice(
        AquariumTankTaxonomy.TYPE_BRACKISH_GENERAL,
        R.string.aquarium_tank_profile_brackish_general
    ),
    TaxonomyChoice(AquariumTankTaxonomy.TYPE_MARINE_FISH, R.string.aquarium_tank_profile_marine_fish),
    TaxonomyChoice(
        AquariumTankTaxonomy.TYPE_SOFT_CORAL_REEF,
        R.string.aquarium_tank_profile_soft_coral_reef
    ),
    TaxonomyChoice(AquariumTankTaxonomy.TYPE_LPS_REEF, R.string.aquarium_tank_profile_lps_reef),
    TaxonomyChoice(AquariumTankTaxonomy.TYPE_SPS_REEF, R.string.aquarium_tank_profile_sps_reef),
    TaxonomyChoice(AquariumTankTaxonomy.TYPE_MIXED_REEF, R.string.aquarium_tank_profile_mixed_reef),
    TaxonomyChoice(
        AquariumTankTaxonomy.TYPE_OTHER_FRESHWATER,
        R.string.aquarium_tank_profile_other
    ),
    TaxonomyChoice(
        AquariumTankTaxonomy.TYPE_OTHER_BRACKISH,
        R.string.aquarium_tank_profile_other
    ),
    TaxonomyChoice(AquariumTankTaxonomy.TYPE_OTHER_MARINE, R.string.aquarium_tank_profile_other)
)

private val tankStyleChoices = listOf(
    TaxonomyChoice(
        AquariumTankTaxonomy.STYLE_NATURE_AQUARIUM,
        R.string.aquarium_text_nature_aquarium
    ),
    TaxonomyChoice(AquariumTankTaxonomy.STYLE_IWAGUMI, R.string.aquarium_style_iwagumi),
    TaxonomyChoice(AquariumTankTaxonomy.STYLE_DUTCH, R.string.aquarium_style_dutch),
    TaxonomyChoice(AquariumTankTaxonomy.STYLE_JUNGLE, R.string.aquarium_style_jungle),
    TaxonomyChoice(AquariumTankTaxonomy.STYLE_BIOTOPE, R.string.aquarium_style_biotope),
    TaxonomyChoice(AquariumTankTaxonomy.STYLE_BLACKWATER, R.string.aquarium_style_blackwater),
    TaxonomyChoice(AquariumTankTaxonomy.STYLE_FOREST, R.string.aquarium_style_forest),
    TaxonomyChoice(AquariumTankTaxonomy.STYLE_MOUNTAIN, R.string.aquarium_style_mountain),
    TaxonomyChoice(AquariumTankTaxonomy.STYLE_ISLAND, R.string.aquarium_style_island)
)

internal class AquariumTankTaxonomyTextResolver(
    private val labelFor: (Int) -> String
) {
    fun canonicalWaterEnvironment(value: String): String? =
        canonical(value, waterEnvironmentChoices)

    fun waterEnvironmentLabel(value: String): String =
        label(value, waterEnvironmentChoices)

    fun canonicalTankType(value: String): String? =
        canonical(value, tankTypeChoices)

    fun tankTypeLabel(value: String): String =
        label(value, tankTypeChoices)

    fun canonicalTankStyle(value: String): String {
        val trimmed = value.trim()
        return canonical(trimmed, tankStyleChoices) ?: trimmed
    }

    fun tankStyleLabel(value: String): String =
        label(value, tankStyleChoices)

    private fun canonical(
        value: String,
        choices: List<TaxonomyChoice>
    ): String? {
        val trimmed = value.trim()
        val codeMatch = choices.firstOrNull { choice ->
            choice.code.equals(trimmed, ignoreCase = true)
        }?.code
        val labelMatch = choices
            .filter { choice ->
                labelFor(choice.labelRes).trim().equals(trimmed, ignoreCase = true)
            }
            .singleOrNull()
            ?.code

        return if (trimmed.isEmpty()) null else codeMatch ?: labelMatch
    }

    private fun label(
        value: String,
        choices: List<TaxonomyChoice>
    ): String {
        val trimmed = value.trim()
        val choice = choices.firstOrNull { choice ->
            choice.code.equals(trimmed, ignoreCase = true)
        }
        return if (trimmed.isEmpty()) "" else choice?.let { labelFor(it.labelRes) } ?: trimmed
    }
}

object AquariumTankTaxonomyText {
    fun canonicalWaterEnvironment(context: Context, value: String): String? =
        AquariumTankTaxonomyTextResolver { resId -> context.getString(resId) }
            .canonicalWaterEnvironment(value)

    fun waterEnvironmentLabel(context: Context, value: String): String =
        AquariumTankTaxonomyTextResolver { resId -> context.getString(resId) }
            .waterEnvironmentLabel(value)

    fun canonicalTankType(context: Context, value: String): String? =
        AquariumTankTaxonomyTextResolver { resId -> context.getString(resId) }
            .canonicalTankType(value)

    fun tankTypeLabel(context: Context, value: String): String =
        AquariumTankTaxonomyTextResolver { resId -> context.getString(resId) }
            .tankTypeLabel(value)

    fun canonicalTankStyle(context: Context, value: String): String =
        AquariumTankTaxonomyTextResolver { resId -> context.getString(resId) }
            .canonicalTankStyle(value)

    fun tankStyleLabel(context: Context, value: String): String =
        AquariumTankTaxonomyTextResolver { resId -> context.getString(resId) }
            .tankStyleLabel(value)
}
