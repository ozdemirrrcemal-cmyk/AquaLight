package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.databinding.FragmentLivestockHealthEvaluationBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories

internal fun FragmentLivestockHealthEvaluationBinding.renderEvaluationContext(
    fragment: Fragment,
    usedWaterAnalysis: WaterAnalysisSnapshot?,
    latestWaterAnalysis: WaterAnalysisSnapshot?,
    existingEvaluation: Boolean,
    stale: Boolean,
    recorded: Boolean
) {
    val displayedWater = if (existingEvaluation) {
        usedWaterAnalysis
    } else {
        latestWaterAnalysis
    }
    renderWaterContext(
        fragment = fragment,
        displayedWater = displayedWater,
        existingEvaluation = existingEvaluation,
        stale = stale,
        recorded = recorded
    )
    renderEvaluationNotice(
        existingEvaluation = existingEvaluation,
        stale = stale,
        hasLatestWaterAnalysis = latestWaterAnalysis != null
    )
}

private fun FragmentLivestockHealthEvaluationBinding.renderWaterContext(
    fragment: Fragment,
    displayedWater: WaterAnalysisSnapshot?,
    existingEvaluation: Boolean,
    stale: Boolean,
    recorded: Boolean
) {
    tvEvaluationWaterValue.text = displayedWater?.let { water ->
        fragment.getString(
            if (existingEvaluation) {
                R.string.livestock_health_evaluation_water_used_format
            } else {
                R.string.livestock_health_evaluation_water_current_format
            },
            water.healthEvaluationDateTime(fragment)
        )
    } ?: fragment.getString(R.string.livestock_health_evaluation_no_water_analysis)

    tvEvaluationWaterBadge.setText(
        when {
            displayedWater == null -> R.string.livestock_health_old_data_badge
            recorded || (existingEvaluation && stale) ->
                R.string.livestock_health_evaluation_state_recorded
            else -> R.string.livestock_health_evaluation_state_current
        }
    )
    tvEvaluationWaterBadge.setTextColor(
        ContextCompat.getColor(
            fragment.requireContext(),
            when {
                displayedWater == null || stale -> R.color.dialog_icon_warning
                recorded -> R.color.aqua_content_secondary
                else -> R.color.aqua_status_success
            }
        )
    )
}

private fun FragmentLivestockHealthEvaluationBinding.renderEvaluationNotice(
    existingEvaluation: Boolean,
    stale: Boolean,
    hasLatestWaterAnalysis: Boolean
) {
    val showNotice = if (existingEvaluation) stale else !hasLatestWaterAnalysis
    cardEvaluationDataNotice.isVisible = showNotice
    if (!showNotice) return

    tvEvaluationDataNoticeTitle.setText(
        if (existingEvaluation) {
            R.string.livestock_health_evaluation_state_stale
        } else {
            R.string.livestock_health_fresh_measurement_warning_title
        }
    )
    tvEvaluationDataNoticeBody.setText(
        if (existingEvaluation) {
            R.string.livestock_health_evaluation_body_stale
        } else {
            R.string.livestock_health_fresh_measurement_warning
        }
    )
}

internal fun FragmentLivestockHealthEvaluationBinding.renderEvaluationAction(
    existingEvaluation: Boolean,
    stale: Boolean,
    closed: Boolean
) {
    btnViewFollowup.setText(
        when {
            !existingEvaluation -> R.string.livestock_health_evaluation_start_followup
            stale && !closed -> R.string.livestock_health_evaluation_refresh
            else -> R.string.livestock_health_evaluation_return_to_followup
        }
    )
}

private fun WaterAnalysisSnapshot.healthEvaluationDateTime(fragment: Fragment): String =
    LocaleFormatter.formatDate(fragment.requireContext(), measuredAtMillis) +
        " · " +
        LocaleFormatter.formatTime(fragment.requireContext(), measuredAtMillis)

internal fun FragmentLivestockHealthEvaluationBinding.bindEvaluationChecks(
    checks: List<LivestockHealthEvaluationCheck>
) {
    require(checks.size == LIVESTOCK_EVALUATION_CHECK_COUNT) {
        "Livestock health evaluation requires exactly $LIVESTOCK_EVALUATION_CHECK_COUNT checks."
    }

    val icons = listOf(ivCheckOne, ivCheckTwo, ivCheckThree, ivCheckFour)
    val titles = listOf(tvCheckOneTitle, tvCheckTwoTitle, tvCheckThreeTitle, tvCheckFourTitle)
    val bodies = listOf(tvCheckOneBody, tvCheckTwoBody, tvCheckThreeBody, tvCheckFourBody)

    checks.forEachIndexed { index, check ->
        icons[index].setImageResource(check.iconRes)
        titles[index].setText(check.titleRes)
        bodies[index].setText(check.bodyRes)
    }
}

internal fun FragmentLivestockHealthEvaluationBinding.renderEvaluationScreen(
    fragment: Fragment,
    livestock: AquariumLivestock?,
    record: LivestockObservationSnapshot?,
    fallbackSymptomKey: String,
    fallbackAffectedCount: Int,
    draftOtherObservation: String,
    latestWaterAnalysis: WaterAnalysisSnapshot?,
    existingEvaluation: Boolean
) {
    val symptomKey = record?.symptomKeys?.firstOrNull() ?: fallbackSymptomKey
    renderEvaluationSummary(
        fragment = fragment,
        livestock = livestock,
        symptomKey = symptomKey,
        affectedCount = record?.currentAffectedCount ?: fallbackAffectedCount,
        totalCount = record?.totalCount,
        otherObservation = record?.otherObservation ?: draftOtherObservation
    )
    bindEvaluationChecks(
        LivestockHealthEvaluationCatalog.checksFor(
            category = livestock?.category,
            symptomKey = symptomKey
        )
    )

    val stale = record?.let { snapshot ->
        snapshot.closedAtMillis == null &&
            snapshot.isEvaluationStale(latestWaterAnalysis)
    } ?: false
    renderEvaluationContext(
        fragment = fragment,
        usedWaterAnalysis = record?.latestEvaluation?.waterAnalysis,
        latestWaterAnalysis = latestWaterAnalysis,
        existingEvaluation = existingEvaluation,
        stale = stale,
        recorded = record?.closedAtMillis != null
    )
    renderEvaluationAction(
        existingEvaluation = existingEvaluation,
        stale = stale,
        closed = record?.closedAtMillis != null
    )
    btnViewFollowup.isEnabled = livestock != null
}

private fun FragmentLivestockHealthEvaluationBinding.renderEvaluationSummary(
    fragment: Fragment,
    livestock: AquariumLivestock?,
    symptomKey: String,
    affectedCount: Int,
    totalCount: Int?,
    otherObservation: String
) {
    val resolvedTotal = totalCount
        ?: livestock?.quantity?.coerceAtLeast(1)
        ?: affectedCount.coerceAtLeast(1)
    val symptomLabel = LivestockHealthUiText.observationLabel(
        fragment = fragment,
        symptomKey = symptomKey,
        otherObservation = otherObservation
    )
    val name = livestock?.name?.ifBlank {
        fragment.getString(R.string.aquarium_unnamed_livestock)
    } ?: fragment.getString(R.string.aquarium_unnamed_livestock)

    ivEvaluationLivestockIcon.setImageResource(
        LivestockCategories.iconRes(livestock?.category.orEmpty())
    )
    tvEvaluationSummary.text = fragment.resources.getQuantityString(
        R.plurals.livestock_health_evaluation_summary_format,
        resolvedTotal,
        name,
        affectedCount.coerceIn(1, resolvedTotal),
        resolvedTotal,
        symptomLabel
    )
}

private const val LIVESTOCK_EVALUATION_CHECK_COUNT = 4
