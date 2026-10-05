package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.databinding.FragmentLivestockHealthFollowUpBinding
import com.aqua.aqualight.i18n.LocaleFormatter

internal fun FragmentLivestockHealthFollowUpBinding.renderEvaluationSummary(
    fragment: Fragment,
    record: LivestockObservationSnapshot,
    latestWaterAnalysis: WaterAnalysisSnapshot?,
    onClick: () -> Unit
) {
    val evaluation = record.latestEvaluation
    val closed = record.closedAtMillis != null
    val stale = !closed && record.isEvaluationStale(latestWaterAnalysis)

    tvEvaluationMeta.text = if (evaluation == null) {
        fragment.getString(R.string.livestock_health_evaluation_state_stale)
    } else {
        fragment.getString(
            R.string.livestock_health_evaluation_last_format,
            LocaleFormatter.formatDate(fragment.requireContext(), evaluation.evaluatedAtMillis) +
                " · " +
                LocaleFormatter.formatTime(fragment.requireContext(), evaluation.evaluatedAtMillis)
        )
    }
    tvEvaluationState.setText(
        when {
            closed -> R.string.livestock_health_evaluation_state_recorded
            stale -> R.string.livestock_health_evaluation_state_stale
            else -> R.string.livestock_health_evaluation_state_current
        }
    )
    tvEvaluationState.setTextColor(
        ContextCompat.getColor(
            fragment.requireContext(),
            if (stale) {
                R.color.dialog_icon_warning
            } else {
                R.color.aqua_status_success
            }
        )
    )
    tvEvaluationBody.setText(
        when {
            closed -> R.string.livestock_health_evaluation_body_recorded
            stale -> R.string.livestock_health_evaluation_body_stale
            else -> R.string.livestock_health_evaluation_body_current
        }
    )
    cardEvaluation.contentDescription =
        fragment.getString(R.string.livestock_health_evaluation_open_description)
    cardEvaluation.setOnClickListener { onClick() }
}
