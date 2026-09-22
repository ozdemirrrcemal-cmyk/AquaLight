package com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R

object LivestockCategories {

    const val FISH = "Fish"
    const val SHRIMP = "Shrimp"
    const val SNAIL = "Snail"
    const val CRAB_CRAYFISH = "Crab / Crayfish"
    const val CORAL = "Coral"
    const val OTHER = "Other"

    val all: List<String> = listOf(
        FISH,
        SHRIMP,
        SNAIL,
        CRAB_CRAYFISH,
        CORAL,
        OTHER
    )

    @StringRes
    fun labelRes(
        category: String
    ): Int {
        return when (category) {
            FISH -> R.string.livestock_category_fish
            SHRIMP -> R.string.livestock_category_shrimp
            SNAIL -> R.string.livestock_category_snail
            CRAB_CRAYFISH -> R.string.livestock_category_crab_crayfish
            CORAL -> R.string.livestock_category_coral
            else -> R.string.livestock_category_other
        }
    }

    @DrawableRes
    fun iconRes(
        category: String
    ): Int {
        return when (category) {
            FISH -> R.drawable.ic_life_fish_24
            SHRIMP -> R.drawable.ic_life_shrimp_24
            SNAIL -> R.drawable.ic_life_snail_24
            CRAB_CRAYFISH -> R.drawable.ic_life_crab_24
            CORAL -> R.drawable.ic_life_coral_24
            else -> R.drawable.ic_life_other_24
        }
    }

    @ColorRes
    fun colorRes(
        category: String
    ): Int {
        return when (category) {
            FISH -> R.color.aqua_tank_detail_livestock_form_fragment_color
            SHRIMP -> R.color.aqua_tank_detail_livestock_form_fragment_color_variant_2
            SNAIL -> R.color.aqua_tank_detail_livestock_form_fragment_color_variant_3
            CRAB_CRAYFISH -> R.color.aqua_tank_detail_livestock_form_fragment_color_variant_4
            CORAL -> R.color.aqua_tank_detail_livestock_form_fragment_color_variant_5
            else -> R.color.aqua_tank_detail_livestock_form_fragment_color_variant_6
        }
    }
}
