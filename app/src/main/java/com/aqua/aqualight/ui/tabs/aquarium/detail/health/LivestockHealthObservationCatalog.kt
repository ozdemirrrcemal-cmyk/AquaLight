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

    const val SHRIMP_INACTIVE = "shrimp_inactive"
    const val SHRIMP_NO_FOOD = "shrimp_no_food"
    const val SHRIMP_MOLT = "shrimp_molt"
    const val SHRIMP_COLOR = "shrimp_color"
    const val SHRIMP_BALANCE = "shrimp_balance"

    const val SNAIL_INACTIVE = "snail_inactive"
    const val SNAIL_RETRACTED = "snail_retracted"
    const val SNAIL_NO_FOOD = "snail_no_food"
    const val SNAIL_SHELL = "snail_shell"
    const val SNAIL_GRIP = "snail_grip"

    const val CRUSTACEAN_INACTIVE = "crustacean_inactive"
    const val CRUSTACEAN_NO_FOOD = "crustacean_no_food"
    const val CRUSTACEAN_MOLT = "crustacean_molt"
    const val CRUSTACEAN_LIMB = "crustacean_limb"
    const val CRUSTACEAN_BALANCE = "crustacean_balance"

    const val CORAL_CLOSED = "coral_closed"
    const val CORAL_TISSUE = "coral_tissue"
    const val CORAL_COLOR = "coral_color"
    const val CORAL_MUCUS = "coral_mucus"
    const val CORAL_DAMAGE = "coral_damage"

    const val GENERIC_INACTIVE = "generic_inactive"
    const val GENERIC_NO_FOOD = "generic_no_food"
    const val GENERIC_COLOR = "generic_color"
    const val GENERIC_BODY = "generic_body"
    const val GENERIC_MOVEMENT = "generic_movement"

    private val fish = listOf(
        symptom(
            LivestockHealthUiText.SYMPTOM_SURFACE,
            R.string.livestock_health_symptom_surface,
            R.drawable.ic_health_observation_breathing_24
        ),
        symptom(
            LivestockHealthUiText.SYMPTOM_APPETITE,
            R.string.livestock_health_symptom_appetite,
            R.drawable.ic_health_observation_appetite_24
        ),
        symptom(
            LivestockHealthUiText.SYMPTOM_SWIMMING,
            R.string.livestock_health_symptom_swimming,
            R.drawable.ic_health_observation_balance_24
        ),
        symptom(
            LivestockHealthUiText.SYMPTOM_SPOT,
            R.string.livestock_health_symptom_spot,
            R.drawable.ic_health_observation_lesion_24
        ),
        symptom(
            LivestockHealthUiText.SYMPTOM_FINS,
            R.string.livestock_health_symptom_fins,
            R.drawable.ic_health_observation_fin_gill_24
        ),
        other()
    )

    private val shrimp = listOf(
        symptom(
            SHRIMP_INACTIVE,
            R.string.livestock_health_symptom_shrimp_inactive,
            R.drawable.ic_health_observation_inactive_24
        ),
        symptom(
            SHRIMP_NO_FOOD,
            R.string.livestock_health_symptom_shrimp_no_food,
            R.drawable.ic_health_observation_appetite_24
        ),
        symptom(
            SHRIMP_MOLT,
            R.string.livestock_health_symptom_shrimp_molt,
            R.drawable.ic_health_observation_molt_24
        ),
        symptom(
            SHRIMP_COLOR,
            R.string.livestock_health_symptom_shrimp_color,
            R.drawable.ic_health_observation_color_24
        ),
        symptom(
            SHRIMP_BALANCE,
            R.string.livestock_health_symptom_shrimp_balance,
            R.drawable.ic_health_observation_balance_24
        ),
        other()
    )

    private val snail = listOf(
        symptom(
            SNAIL_INACTIVE,
            R.string.livestock_health_symptom_snail_inactive,
            R.drawable.ic_health_observation_inactive_24
        ),
        symptom(
            SNAIL_RETRACTED,
            R.string.livestock_health_symptom_snail_retracted,
            R.drawable.ic_health_observation_retracted_24
        ),
        symptom(
            SNAIL_NO_FOOD,
            R.string.livestock_health_symptom_snail_no_food,
            R.drawable.ic_health_observation_appetite_24
        ),
        symptom(
            SNAIL_SHELL,
            R.string.livestock_health_symptom_snail_shell,
            R.drawable.ic_health_observation_shell_damage_24
        ),
        symptom(
            SNAIL_GRIP,
            R.string.livestock_health_symptom_snail_grip,
            R.drawable.ic_health_observation_grip_24
        ),
        other()
    )

    private val crustacean = listOf(
        symptom(
            CRUSTACEAN_INACTIVE,
            R.string.livestock_health_symptom_crustacean_inactive,
            R.drawable.ic_health_observation_inactive_24
        ),
        symptom(
            CRUSTACEAN_NO_FOOD,
            R.string.livestock_health_symptom_crustacean_no_food,
            R.drawable.ic_health_observation_appetite_24
        ),
        symptom(
            CRUSTACEAN_MOLT,
            R.string.livestock_health_symptom_crustacean_molt,
            R.drawable.ic_health_observation_molt_24
        ),
        symptom(
            CRUSTACEAN_LIMB,
            R.string.livestock_health_symptom_crustacean_limb,
            R.drawable.ic_health_observation_limb_damage_24
        ),
        symptom(
            CRUSTACEAN_BALANCE,
            R.string.livestock_health_symptom_crustacean_balance,
            R.drawable.ic_health_observation_balance_24
        ),
        other()
    )

    private val coral = listOf(
        symptom(
            CORAL_CLOSED,
            R.string.livestock_health_symptom_coral_closed,
            R.drawable.ic_health_observation_polyp_24
        ),
        symptom(
            CORAL_TISSUE,
            R.string.livestock_health_symptom_coral_tissue,
            R.drawable.ic_health_observation_tissue_24
        ),
        symptom(
            CORAL_COLOR,
            R.string.livestock_health_symptom_coral_color,
            R.drawable.ic_health_observation_color_24
        ),
        symptom(
            CORAL_MUCUS,
            R.string.livestock_health_symptom_coral_mucus,
            R.drawable.ic_health_observation_mucus_24
        ),
        symptom(
            CORAL_DAMAGE,
            R.string.livestock_health_symptom_coral_damage,
            R.drawable.ic_health_observation_coral_damage_24
        ),
        other()
    )

    private val generic = listOf(
        symptom(
            GENERIC_INACTIVE,
            R.string.livestock_health_symptom_generic_inactive,
            R.drawable.ic_health_observation_inactive_24
        ),
        symptom(
            GENERIC_NO_FOOD,
            R.string.livestock_health_symptom_generic_no_food,
            R.drawable.ic_health_observation_appetite_24
        ),
        symptom(
            GENERIC_COLOR,
            R.string.livestock_health_symptom_generic_color,
            R.drawable.ic_health_observation_color_24
        ),
        symptom(
            GENERIC_BODY,
            R.string.livestock_health_symptom_generic_body,
            R.drawable.ic_health_observation_shell_damage_24
        ),
        symptom(
            GENERIC_MOVEMENT,
            R.string.livestock_health_symptom_generic_movement,
            R.drawable.ic_health_observation_balance_24
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
        requireNotNull(allSymptoms.firstOrNull { option -> option.key == key }) {
            "Unknown livestock symptom: $key"
        }.labelRes

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
        requireNotNull(onsetOptions().firstOrNull { option -> option.id == id }) {
            "Unknown livestock onset: $id"
        }.labelRes

    @StringRes
    fun trendLabelRes(id: String): Int =
        requireNotNull(trendOptions().firstOrNull { option -> option.id == id }) {
            "Unknown livestock trend: $id"
        }.labelRes

    private fun symptom(
        key: String,
        @StringRes labelRes: Int,
        @DrawableRes iconRes: Int
    ) = LivestockHealthSymptomOption(key, labelRes, iconRes)

    private fun other() = symptom(
        LivestockHealthUiText.SYMPTOM_OTHER,
        R.string.livestock_health_symptom_other,
        R.drawable.ic_livestock_symptom_more_24
    )
}
