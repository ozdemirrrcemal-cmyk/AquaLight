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

internal data class LivestockEvaluationFallbackState(
    val symptomKey: String,
    val affectedCount: Int,
    val otherObservation: String
)

internal data class LivestockEvaluationScreenState(
    val livestock: AquariumLivestock?,
    val record: LivestockObservationSnapshot?,
    val fallback: LivestockEvaluationFallbackState,
    val latestWaterAnalysis: WaterAnalysisSnapshot?,
    val existingEvaluation: Boolean
)

private data class LivestockEvaluationContextState(
    val usedWaterAnalysis: WaterAnalysisSnapshot?,
    val latestWaterAnalysis: WaterAnalysisSnapshot?,
    val existingEvaluation: Boolean,
    val stale: Boolean,
    val recorded: Boolean
)

private data class LivestockEvaluationSummaryState(
    val symptomKey: String,
    val affectedCount: Int,
    val totalCount: Int?,
    val otherObservation: String
)

private fun FragmentLivestockHealthEvaluationBinding.renderEvaluationContext(
    fragment: Fragment,
    state: LivestockEvaluationContextState
) {
    val displayedWater = if (state.existingEvaluation) {
        state.usedWaterAnalysis
    } else {
        state.latestWaterAnalysis
    }
    renderWaterContext(
        fragment = fragment,
        displayedWater = displayedWater,
        state = state
    )
    renderEvaluationNotice(state)
}

private fun FragmentLivestockHealthEvaluationBinding.renderWaterContext(
    fragment: Fragment,
    displayedWater: WaterAnalysisSnapshot?,
    state: LivestockEvaluationContextState
) {
    tvEvaluationWaterValue.text = displayedWater?.let { water ->
        fragment.getString(
            if (state.existingEvaluation) {
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
            state.recorded || (state.existingEvaluation && state.stale) ->
                R.string.livestock_health_evaluation_state_recorded
            else -> R.string.livestock_health_evaluation_state_current
        }
    )
    tvEvaluationWaterBadge.setTextColor(
        ContextCompat.getColor(
            fragment.requireContext(),
            when {
                displayedWater == null || state.stale -> R.color.dialog_icon_warning
                state.recorded -> R.color.aqua_content_secondary
                else -> R.color.aqua_status_success
            }
        )
    )
}

private fun FragmentLivestockHealthEvaluationBinding.renderEvaluationNotice(
    state: LivestockEvaluationContextState
) {
    val showNotice = if (state.existingEvaluation) {
        state.stale
    } else {
        state.latestWaterAnalysis == null
    }
    cardEvaluationDataNotice.isVisible = showNotice
    if (!showNotice) return

    tvEvaluationDataNoticeTitle.setText(
        if (state.existingEvaluation) {
            R.string.livestock_health_evaluation_state_stale
        } else {
            R.string.livestock_health_fresh_measurement_warning_title
        }
    )
    tvEvaluationDataNoticeBody.setText(
        if (state.existingEvaluation) {
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
    state: LivestockEvaluationScreenState
) {
    val symptomKey = state.record?.symptomKeys?.firstOrNull()
        ?: state.fallback.symptomKey
    renderEvaluationSummary(
        fragment = fragment,
        livestock = state.livestock,
        state = LivestockEvaluationSummaryState(
            symptomKey = symptomKey,
            affectedCount = state.record?.currentAffectedCount
                ?: state.fallback.affectedCount,
            totalCount = state.record?.totalCount,
            otherObservation = state.record?.otherObservation
                ?: state.fallback.otherObservation
        )
    )
    bindEvaluationChecks(
        LivestockHealthEvaluationCatalog.checksFor(
            category = state.livestock?.category,
            symptomKey = symptomKey
        )
    )

    val stale = state.record?.let { snapshot ->
        snapshot.closedAtMillis == null &&
            snapshot.isEvaluationStale(state.latestWaterAnalysis)
    } ?: false
    renderEvaluationContext(
        fragment = fragment,
        state = LivestockEvaluationContextState(
            usedWaterAnalysis = state.record?.latestEvaluation?.waterAnalysis,
            latestWaterAnalysis = state.latestWaterAnalysis,
            existingEvaluation = state.existingEvaluation,
            stale = stale,
            recorded = state.record?.closedAtMillis != null
        )
    )
    renderEvaluationAction(
        existingEvaluation = state.existingEvaluation,
        stale = stale,
        closed = state.record?.closedAtMillis != null
    )
    btnViewFollowup.isEnabled = state.livestock != null
}

private fun FragmentLivestockHealthEvaluationBinding.renderEvaluationSummary(
    fragment: Fragment,
    livestock: AquariumLivestock?,
    state: LivestockEvaluationSummaryState
) {
    val resolvedTotal = state.totalCount
        ?: livestock?.quantity?.coerceAtLeast(1)
        ?: state.affectedCount.coerceAtLeast(1)
    val symptomLabel = LivestockHealthUiText.observationLabel(
        fragment = fragment,
        symptomKey = state.symptomKey,
        otherObservation = state.otherObservation
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
        state.affectedCount.coerceIn(1, resolvedTotal),
        resolvedTotal,
        symptomLabel
    )
}

private const val LIVESTOCK_EVALUATION_CHECK_COUNT = 4
