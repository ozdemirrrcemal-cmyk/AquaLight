package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.water.WaterHazardSeverity
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentCoverage
import com.aqua.aqualight.i18n.LocaleFormatter

internal object WaterAssessmentPresentation {
    fun entry(context: Context, record: WaterAnalysisSnapshot): String {
        val result = record.assessment ?: return context.getString(R.string.water_assessment_not_available)
        val adverse = result.findings.count { it.severity != null && it.severity != WaterHazardSeverity.NONE }
        return if (result.coverage == WaterAssessmentCoverage.NONE) {
            context.getString(R.string.water_assessment_no_comparison)
        } else {
            context.getString(R.string.water_assessment_entry_summary, adverse,
                result.conflicts.size + result.habitatConflicts.size)
        }
    }

    fun summary(context: Context, record: WaterAnalysisSnapshot): String {
        val result = record.assessment ?: return context.getString(R.string.water_assessment_not_available)
        val evaluated = result.findings.count { it.direction != null }
        val adverse = result.findings.count { it.severity != null && it.severity != WaterHazardSeverity.NONE }
        return context.getString(R.string.water_assessment_catalog_summary, evaluated, result.findings.size,
            adverse, result.conflicts.size + result.habitatConflicts.size)
    }

    fun detail(context: Context, record: WaterAnalysisSnapshot): String = buildString {
        append(summary(context, record))
        record.assessment?.let { assessment ->
            WaterAssessmentFindingPresentation.lines(context, assessment).forEach { append("\n\n"); append(it) }
        }
        record.contextCapturedAtMillis?.let { capturedAt ->
            append('\n')
            append(context.getString(R.string.water_assessment_context_at_entry,
                LocaleFormatter.formatDate(context, capturedAt), LocaleFormatter.formatTime(context, capturedAt)))
        }
    }
}
