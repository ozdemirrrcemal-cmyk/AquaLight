package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context

object AquariumTankTaxonomyText {

    fun canonicalWaterEnvironment(context: Context, value: String): String? =
        AquariumTankTaxonomyTextResolver.canonicalWaterEnvironment(value) {
            context.getString(it)
        }

    fun waterEnvironmentLabel(context: Context, value: String): String =
        AquariumTankTaxonomyTextResolver.waterEnvironmentLabel(value) {
            context.getString(it)
        }

    fun canonicalTankType(context: Context, value: String): String? =
        AquariumTankTaxonomyTextResolver.canonicalTankType(value) {
            context.getString(it)
        }

    fun tankTypeLabel(context: Context, value: String): String =
        AquariumTankTaxonomyTextResolver.tankTypeLabel(value) {
            context.getString(it)
        }

    fun canonicalTankStyle(context: Context, value: String): String =
        AquariumTankTaxonomyTextResolver.canonicalTankStyle(value) {
            context.getString(it)
        }

    fun tankStyleLabel(context: Context, value: String): String =
        AquariumTankTaxonomyTextResolver.tankStyleLabel(value) {
            context.getString(it)
        }
}
