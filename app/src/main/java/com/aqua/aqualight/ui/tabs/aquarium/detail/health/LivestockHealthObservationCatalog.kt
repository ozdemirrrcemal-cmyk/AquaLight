package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories

internal data class LivestockHealthSymptomOption(
    val key: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
)

internal data class LivestockHealthChoiceOption(
    val id: String,
    @StringRes val labelRes: Int
)

internal object LivestockHealthObservationCatalog {
    const val ONSET_NOW = "onset_now"
    const val ONSET_TODAY = "onset_today"
    const val ONSET_1_3_DAYS = "onset_1_3_days"
    const val ONSET_4_7_DAYS = "onset_4_7_days"
    const val ONSET_OVER_WEEK = "onset_over_week"
    const val ONSET_UNSURE = "onset_unsure"

    const val TREND_NEW = "trend_new"
    const val TREND_INCREASING = "trend_increasing"
    const val TREND_SAME = "trend_same"
    const val TREND_DECREASING = "trend_decreasing"
    const val TREND_UNSURE = "trend_unsure"

    private const val SHRIMP_INACTIVE = "shrimp_inactive"
    private const val SHRIMP_NO_FOOD = "shrimp_no_food"
    private const val SHRIMP_COLOR = "shrimp_color"
    private const val SHRIMP_SHELL = "shrimp_shell"
    private const val SHRIMP_MOVEMENT = "shrimp_movement"

    private const val SNAIL_INACTIVE = "snail_inactive"
    private const val SNAIL_NO_FOOD = "snail_no_food"
    private const val SNAIL_SHELL = "snail_shell"
    private const val SNAIL_SURFACE = "snail_surface"
    private const val SNAIL_COLOR = "snail_color"

    private const val CRUSTACEAN_INACTIVE = "crustacean_inactive"
    private const val CRUSTACEAN_NO_FOOD = "crustacean_no_food"
    private const val CRUSTACEAN_COLOR = "crustacean_color"
    private const val CRUSTACEAN_SHELL = "crustacean_shell"
    private const val CRUSTACEAN_MOVEMENT = "crustacean_movement"

    private const val CORAL_CLOSED = "coral_closed"
    private const val CORAL_TISSUE = "coral_tissue"
    private const val CORAL_COLOR = "coral_color"
    private const val CORAL_DAMAGE = "coral_damage"
    private const val CORAL_MUCUS = "coral_mucus"

    private const val GENERIC_INACTIVE = "generic_inactive"
    private const val GENERIC_NO_FOOD = "generic_no_food"
    private const val GENERIC_COLOR = "generic_color"
    private const val GENERIC_BODY = "generic_body"
    private const val GENERIC_MOVEMENT = "generic_movement"

    private val fish = listOf(
        LivestockHealthSymptomOption(
            LivestockHealthUiText.SYMPTOM_SURFACE,
            R.string.livestock_health_symptom_surface,
            R.drawable.ic_livestock_symptom_surface_24
        ),
        LivestockHealthSymptomOption(
            LivestockHealthUiText.SYMPTOM_APPETITE,
            R.string.livestock_health_symptom_appetite,
            R.drawable.ic_livestock_symptom_appetite_24
        ),
        LivestockHealthSymptomOption(
            LivestockHealthUiText.SYMPTOM_SWIMMING,
            R.string.livestock_health_symptom_swimming,
            R.drawable.ic_livestock_symptom_swimming_24
        ),
        LivestockHealthSymptomOption(
            LivestockHealthUiText.SYMPTOM_SPOT,
            R.string.livestock_health_symptom_spot,
            R.drawable.ic_livestock_symptom_spot_24
        ),
        LivestockHealthSymptomOption(
            LivestockHealthUiText.SYMPTOM_FINS,
            R.string.livestock_health_symptom_fins,
            R.drawable.ic_livestock_symptom_fin_24
        ),
        other()
    )

    private val shrimp = listOf(
        LivestockHealthSymptomOption(
            SHRIMP_INACTIVE,
            R.string.livestock_health_symptom_shrimp_inactive,
            R.drawable.ic_life_shrimp_24
        ),
        LivestockHealthSymptomOption(
            SHRIMP_NO_FOOD,
            R.string.livestock_health_symptom_shrimp_no_food,
            R.drawable.ic_livestock_symptom_appetite_24
        ),
        LivestockHealthSymptomOption(
            SHRIMP_COLOR,
            R.string.livestock_health_symptom_shrimp_color,
            R.drawable.ic_livestock_symptom_spot_24
        ),
        LivestockHealthSymptomOption(
            SHRIMP_SHELL,
            R.string.livestock_health_symptom_shrimp_shell,
            R.drawable.ic_livestock_symptom_fin_24
        ),
        LivestockHealthSymptomOption(
            SHRIMP_MOVEMENT,
            R.string.livestock_health_symptom_shrimp_movement,
            R.drawable.ic_livestock_symptom_swimming_24
        ),
        other()
    )

    private val snail = listOf(
        LivestockHealthSymptomOption(
            SNAIL_INACTIVE,
            R.string.livestock_health_symptom_snail_inactive,
            R.drawable.ic_life_snail_24
        ),
        LivestockHealthSymptomOption(
            SNAIL_NO_FOOD,
            R.string.livestock_health_symptom_snail_no_food,
            R.drawable.ic_livestock_symptom_appetite_24
        ),
        LivestockHealthSymptomOption(
            SNAIL_SHELL,
            R.string.livestock_health_symptom_snail_shell,
            R.drawable.ic_life_snail_24
        ),
        LivestockHealthSymptomOption(
            SNAIL_SURFACE,
            R.string.livestock_health_symptom_snail_surface,
            R.drawable.ic_livestock_symptom_surface_24
        ),
        LivestockHealthSymptomOption(
            SNAIL_COLOR,
            R.string.livestock_health_symptom_snail_color,
            R.drawable.ic_livestock_symptom_spot_24
        ),
        other()
    )

    private val crustacean = listOf(
        LivestockHealthSymptomOption(
            CRUSTACEAN_INACTIVE,
            R.string.livestock_health_symptom_crustacean_inactive,
            R.drawable.ic_life_crab_24
        ),
        LivestockHealthSymptomOption(
            CRUSTACEAN_NO_FOOD,
            R.string.livestock_health_symptom_crustacean_no_food,
            R.drawable.ic_livestock_symptom_appetite_24
        ),
        LivestockHealthSymptomOption(
            CRUSTACEAN_COLOR,
            R.string.livestock_health_symptom_crustacean_color,
            R.drawable.ic_livestock_symptom_spot_24
        ),
        LivestockHealthSymptomOption(
            CRUSTACEAN_SHELL,
            R.string.livestock_health_symptom_crustacean_shell,
            R.drawable.ic_livestock_symptom_fin_24
        ),
        LivestockHealthSymptomOption(
            CRUSTACEAN_MOVEMENT,
            R.string.livestock_health_symptom_crustacean_movement,
            R.drawable.ic_livestock_symptom_swimming_24
        ),
        other()
    )

    private val coral = listOf(
        LivestockHealthSymptomOption(
            CORAL_CLOSED,
            R.string.livestock_health_symptom_coral_closed,
            R.drawable.ic_life_coral_24
        ),
        LivestockHealthSymptomOption(
            CORAL_TISSUE,
            R.string.livestock_health_symptom_coral_tissue,
            R.drawable.ic_livestock_symptom_fin_24
        ),
        LivestockHealthSymptomOption(
            CORAL_COLOR,
            R.string.livestock_health_symptom_coral_color,
            R.drawable.ic_livestock_symptom_spot_24
        ),
        LivestockHealthSymptomOption(
            CORAL_DAMAGE,
            R.string.livestock_health_symptom_coral_damage,
            R.drawable.ic_livestock_symptom_spot_24
        ),
        LivestockHealthSymptomOption(
            CORAL_MUCUS,
            R.string.livestock_health_symptom_coral_mucus,
            R.drawable.ic_livestock_symptom_surface_24
        ),
        other()
    )

    private val generic = listOf(
        LivestockHealthSymptomOption(
            GENERIC_INACTIVE,
            R.string.livestock_health_symptom_generic_inactive,
            R.drawable.ic_health_livestock_24
        ),
        LivestockHealthSymptomOption(
            GENERIC_NO_FOOD,
            R.string.livestock_health_symptom_generic_no_food,
            R.drawable.ic_livestock_symptom_appetite_24
        ),
        LivestockHealthSymptomOption(
            GENERIC_COLOR,
            R.string.livestock_health_symptom_generic_color,
            R.drawable.ic_livestock_symptom_spot_24
        ),
        LivestockHealthSymptomOption(
            GENERIC_BODY,
            R.string.livestock_health_symptom_generic_body,
            R.drawable.ic_livestock_symptom_fin_24
        ),
        LivestockHealthSymptomOption(
            GENERIC_MOVEMENT,
            R.string.livestock_health_symptom_generic_movement,
            R.drawable.ic_livestock_symptom_swimming_24
        ),
        other()
    )

    private val allSymptoms by lazy {
        (fish + shrimp + snail + crustacean + coral + generic)
            .distinctBy(LivestockHealthSymptomOption::key)
    }

    fun symptomsFor(category: String?): List<LivestockHealthSymptomOption> =
        when (category) {
            LivestockCategories.FISH -> fish
            LivestockCategories.SHRIMP -> shrimp
            LivestockCategories.SNAIL -> snail
            LivestockCategories.CRAB_CRAYFISH -> crustacean
            LivestockCategories.CORAL -> coral
            else -> generic
        }

    @StringRes
    fun symptomLabelRes(key: String): Int =
        allSymptoms.firstOrNull { option -> option.key == key }?.labelRes
            ?: R.string.livestock_health_symptom_other

    fun onsetOptions(): List<LivestockHealthChoiceOption> = listOf(
        LivestockHealthChoiceOption(ONSET_NOW, R.string.livestock_health_when_now),
        LivestockHealthChoiceOption(ONSET_TODAY, R.string.livestock_health_when_today),
        LivestockHealthChoiceOption(ONSET_1_3_DAYS, R.string.livestock_health_onset_1_3_days),
        LivestockHealthChoiceOption(ONSET_4_7_DAYS, R.string.livestock_health_onset_4_7_days),
        LivestockHealthChoiceOption(ONSET_OVER_WEEK, R.string.livestock_health_onset_over_week),
        LivestockHealthChoiceOption(ONSET_UNSURE, R.string.livestock_health_choice_unsure)
    )

    fun trendOptions(): List<LivestockHealthChoiceOption> = listOf(
        LivestockHealthChoiceOption(TREND_NEW, R.string.livestock_health_trend_new),
        LivestockHealthChoiceOption(
            TREND_INCREASING,
            R.string.livestock_health_status_increased
        ),
        LivestockHealthChoiceOption(TREND_SAME, R.string.livestock_health_status_same),
        LivestockHealthChoiceOption(
            TREND_DECREASING,
            R.string.livestock_health_status_decreased
        ),
        LivestockHealthChoiceOption(TREND_UNSURE, R.string.livestock_health_choice_unsure)
    )

    @StringRes
    fun onsetLabelRes(id: String): Int =
        onsetOptions().firstOrNull { option -> option.id == id }?.labelRes
            ?: R.string.livestock_health_when_today

    @StringRes
    fun trendLabelRes(id: String): Int =
        trendOptions().firstOrNull { option -> option.id == id }?.labelRes
            ?: R.string.livestock_health_trend_new

    private fun other() = LivestockHealthSymptomOption(
        LivestockHealthUiText.SYMPTOM_OTHER,
        R.string.livestock_health_symptom_other,
        R.drawable.ic_livestock_symptom_more_24
    )
}
