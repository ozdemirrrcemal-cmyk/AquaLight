package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories

internal data class LivestockHealthEvaluationCheck(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @DrawableRes val iconRes: Int
)

/**
 * Presentation-only checklist ordering.
 *
 * It deliberately does not infer a diagnosis or cause. The future analysis engine can replace
 * or enrich this catalog without changing the evaluation screen structure.
 */
internal object LivestockHealthEvaluationCatalog {

    private val fishSurface = check(
        R.string.livestock_health_check_fish_surface_title,
        R.string.livestock_health_check_fish_surface_body,
        R.drawable.ic_water_test_wave_24
    )
    private val fishBody = check(
        R.string.livestock_health_check_fish_body_title,
        R.string.livestock_health_check_fish_body_body,
        R.drawable.ic_health_observation_fin_gill_24
    )
    private val fishOthers = check(
        R.string.livestock_health_check_fish_others_title,
        R.string.livestock_health_check_fish_others_body,
        R.drawable.ic_health_livestock_24
    )
    private val fishFeed = check(
        R.string.livestock_health_check_fish_feed_title,
        R.string.livestock_health_check_fish_feed_body,
        R.drawable.ic_health_observation_appetite_24
    )

    private val shrimpMolt = check(
        R.string.livestock_health_check_shrimp_molt_title,
        R.string.livestock_health_check_shrimp_molt_body,
        R.drawable.ic_health_observation_molt_24
    )
    private val shrimpActivity = check(
        R.string.livestock_health_check_shrimp_activity_title,
        R.string.livestock_health_check_shrimp_activity_body,
        R.drawable.ic_health_observation_balance_24
    )
    private val shrimpBody = check(
        R.string.livestock_health_check_shrimp_body_title,
        R.string.livestock_health_check_shrimp_body_body,
        R.drawable.ic_health_observation_color_24
    )
    private val shrimpFeed = check(
        R.string.livestock_health_check_shrimp_feed_title,
        R.string.livestock_health_check_shrimp_feed_body,
        R.drawable.ic_health_observation_appetite_24
    )

    private val snailGrip = check(
        R.string.livestock_health_check_snail_grip_title,
        R.string.livestock_health_check_snail_grip_body,
        R.drawable.ic_health_observation_grip_24
    )
    private val snailShell = check(
        R.string.livestock_health_check_snail_shell_title,
        R.string.livestock_health_check_snail_shell_body,
        R.drawable.ic_health_observation_shell_damage_24
    )
    private val snailBody = check(
        R.string.livestock_health_check_snail_body_title,
        R.string.livestock_health_check_snail_body_body,
        R.drawable.ic_health_observation_retracted_24
    )
    private val snailFeed = check(
        R.string.livestock_health_check_snail_feed_title,
        R.string.livestock_health_check_snail_feed_body,
        R.drawable.ic_health_observation_appetite_24
    )

    private val crustaceanMolt = check(
        R.string.livestock_health_check_crustacean_molt_title,
        R.string.livestock_health_check_crustacean_molt_body,
        R.drawable.ic_health_observation_molt_24
    )
    private val crustaceanLimb = check(
        R.string.livestock_health_check_crustacean_limb_title,
        R.string.livestock_health_check_crustacean_limb_body,
        R.drawable.ic_health_observation_limb_damage_24
    )
    private val crustaceanActivity = check(
        R.string.livestock_health_check_crustacean_activity_title,
        R.string.livestock_health_check_crustacean_activity_body,
        R.drawable.ic_health_observation_balance_24
    )
    private val crustaceanFeed = check(
        R.string.livestock_health_check_crustacean_feed_title,
        R.string.livestock_health_check_crustacean_feed_body,
        R.drawable.ic_health_observation_appetite_24
    )

    private val coralPolyp = check(
        R.string.livestock_health_check_coral_polyp_title,
        R.string.livestock_health_check_coral_polyp_body,
        R.drawable.ic_health_observation_polyp_24
    )
    private val coralTissue = check(
        R.string.livestock_health_check_coral_tissue_title,
        R.string.livestock_health_check_coral_tissue_body,
        R.drawable.ic_health_observation_tissue_24
    )
    private val coralColor = check(
        R.string.livestock_health_check_coral_color_title,
        R.string.livestock_health_check_coral_color_body,
        R.drawable.ic_health_observation_color_24
    )
    private val coralEnvironment = check(
        R.string.livestock_health_check_coral_environment_title,
        R.string.livestock_health_check_coral_environment_body,
        R.drawable.ic_water_test_wave_24
    )

    private val generic = listOf(
        check(
            R.string.livestock_health_check_generic_activity_title,
            R.string.livestock_health_check_generic_activity_body,
            R.drawable.ic_health_observation_inactive_24
        ),
        check(
            R.string.livestock_health_check_generic_body_title,
            R.string.livestock_health_check_generic_body_body,
            R.drawable.ic_health_observation_shell_damage_24
        ),
        check(
            R.string.livestock_health_check_generic_others_title,
            R.string.livestock_health_check_generic_others_body,
            R.drawable.ic_health_livestock_24
        ),
        check(
            R.string.livestock_health_check_generic_feed_title,
            R.string.livestock_health_check_generic_feed_body,
            R.drawable.ic_health_observation_appetite_24
        )
    )

    fun checksFor(
        category: String?,
        symptomKey: String
    ): List<LivestockHealthEvaluationCheck> = when (category) {
        LivestockCategories.FISH -> orderFish(symptomKey)
        LivestockCategories.SHRIMP -> orderShrimp(symptomKey)
        LivestockCategories.SNAIL -> orderSnail(symptomKey)
        LivestockCategories.CRAB_CRAYFISH -> orderCrustacean(symptomKey)
        LivestockCategories.CORAL -> orderCoral(symptomKey)
        else -> generic
    }

    private fun orderFish(symptomKey: String): List<LivestockHealthEvaluationCheck> =
        when (symptomKey) {
            LivestockHealthUiText.SYMPTOM_APPETITE ->
                listOf(fishFeed, fishSurface, fishBody, fishOthers)
            LivestockHealthUiText.SYMPTOM_SPOT,
            LivestockHealthUiText.SYMPTOM_FINS ->
                listOf(fishBody, fishSurface, fishOthers, fishFeed)
            else ->
                listOf(fishSurface, fishBody, fishOthers, fishFeed)
        }

    private fun orderShrimp(symptomKey: String): List<LivestockHealthEvaluationCheck> =
        when (symptomKey) {
            LivestockHealthObservationCatalog.SHRIMP_MOLT ->
                listOf(shrimpMolt, shrimpBody, shrimpActivity, shrimpFeed)
            LivestockHealthObservationCatalog.SHRIMP_NO_FOOD ->
                listOf(shrimpFeed, shrimpActivity, shrimpBody, shrimpMolt)
            LivestockHealthObservationCatalog.SHRIMP_COLOR ->
                listOf(shrimpBody, shrimpMolt, shrimpActivity, shrimpFeed)
            else ->
                listOf(shrimpActivity, shrimpMolt, shrimpBody, shrimpFeed)
        }

    private fun orderSnail(symptomKey: String): List<LivestockHealthEvaluationCheck> =
        when (symptomKey) {
            LivestockHealthObservationCatalog.SNAIL_SHELL ->
                listOf(snailShell, snailGrip, snailBody, snailFeed)
            LivestockHealthObservationCatalog.SNAIL_RETRACTED ->
                listOf(snailBody, snailGrip, snailShell, snailFeed)
            LivestockHealthObservationCatalog.SNAIL_NO_FOOD ->
                listOf(snailFeed, snailGrip, snailBody, snailShell)
            else ->
                listOf(snailGrip, snailShell, snailBody, snailFeed)
        }

    private fun orderCrustacean(symptomKey: String): List<LivestockHealthEvaluationCheck> =
        when (symptomKey) {
            LivestockHealthObservationCatalog.CRUSTACEAN_MOLT ->
                listOf(crustaceanMolt, crustaceanLimb, crustaceanActivity, crustaceanFeed)
            LivestockHealthObservationCatalog.CRUSTACEAN_LIMB ->
                listOf(crustaceanLimb, crustaceanMolt, crustaceanActivity, crustaceanFeed)
            LivestockHealthObservationCatalog.CRUSTACEAN_NO_FOOD ->
                listOf(crustaceanFeed, crustaceanActivity, crustaceanMolt, crustaceanLimb)
            else ->
                listOf(crustaceanActivity, crustaceanMolt, crustaceanLimb, crustaceanFeed)
        }

    private fun orderCoral(symptomKey: String): List<LivestockHealthEvaluationCheck> =
        when (symptomKey) {
            LivestockHealthObservationCatalog.CORAL_TISSUE,
            LivestockHealthObservationCatalog.CORAL_DAMAGE ->
                listOf(coralTissue, coralColor, coralEnvironment, coralPolyp)
            LivestockHealthObservationCatalog.CORAL_COLOR ->
                listOf(coralColor, coralTissue, coralEnvironment, coralPolyp)
            LivestockHealthObservationCatalog.CORAL_CLOSED ->
                listOf(coralPolyp, coralEnvironment, coralTissue, coralColor)
            else ->
                listOf(coralEnvironment, coralPolyp, coralTissue, coralColor)
        }

    private fun check(
        @StringRes titleRes: Int,
        @StringRes bodyRes: Int,
        @DrawableRes iconRes: Int
    ) = LivestockHealthEvaluationCheck(titleRes, bodyRes, iconRes)
}
