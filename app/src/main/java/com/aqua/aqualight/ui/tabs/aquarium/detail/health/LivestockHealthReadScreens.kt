package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.LivestockHealthSymptom
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader

internal fun LivestockHealthFragment.renderAssessment(current: AquariumTankSnapshot, content: LinearLayout) {
        val record = current.healthObservations.firstOrNull { it.id == args.observationId }
        if (record == null) { missing(content); return }
        val review = LivestockHealthAssessmentUi(requireContext())
        content.addView(review.highlight(
            getString(R.string.livestock_health_assessment_attention),
            getString(R.string.livestock_health_assessment_observation_context,
                record.livestockName, record.latestAffectedCount,
                current.livestock.firstOrNull { it.id == record.livestockId }?.quantity
                    ?: record.affectedCount, record.symptoms.joinToString { ui.symptomName(it) })
        ) { navigate(LivestockHealthPages.DETAIL, record.id) })
        renderAssessmentEvidence(content, review)
        renderAssessmentChecks(content, review, record)
        binding.healthPrimaryAction.isVisible = false
    }

private fun LivestockHealthFragment.renderAssessmentEvidence(content: LinearLayout,
    review: LivestockHealthAssessmentUi) {
        section(content, R.string.livestock_health_assessment_evidence)
        content.addView(review.evidence(R.drawable.ic_care_water_test_24,
            getString(R.string.livestock_health_assessment_water_title),
            getString(R.string.livestock_health_assessment_missing_water)))
        content.addView(ui.spacer(R.dimen.aqua_size_8))
        val lastWater = tankActivity.completedTasks.firstOrNull {
            it.type == CareTaskType.WATER_CHANGE
        }
        content.addView(review.evidence(R.drawable.ic_care_water_change_24,
            getString(R.string.livestock_health_assessment_water_change),
            lastWater?.let { date(it.completedAtMillis ?: it.dueAtMillis) }
                ?: getString(R.string.livestock_health_maintenance_none)))
        content.addView(ui.spacer(R.dimen.aqua_size_8))
        panel(content, getString(R.string.livestock_health_assessment_missing_data),
            getString(R.string.livestock_health_assessment_no_diagnosis))
}

private fun LivestockHealthFragment.renderAssessmentChecks(content: LinearLayout,
    review: LivestockHealthAssessmentUi, record: LivestockHealthObservation) {
        section(content, R.string.livestock_health_assessment_check_title)
        val surface = LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE in record.symptoms
        content.addView(review.step(1, R.drawable.ic_health_surface,
            getString(if (surface) R.string.livestock_health_assessment_step_surface_title
                else R.string.livestock_health_assessment_step_general_title),
            getString(if (surface) R.string.livestock_health_assessment_check_surface
                else R.string.livestock_health_assessment_check_general)) { openTank() })
        content.addView(ui.spacer(R.dimen.aqua_size_8))
        content.addView(review.step(2, R.drawable.ic_care_water_test_24,
            getString(R.string.livestock_health_assessment_step_water_title),
            getString(R.string.livestock_health_assessment_check_water)) { openTank() })
        content.addView(ui.spacer(R.dimen.aqua_size_8))
        content.addView(review.step(3, R.drawable.ic_life_fish_24,
            getString(R.string.livestock_health_assessment_step_others_title),
            getString(R.string.livestock_health_assessment_check_others)) {
            navigate(LivestockHealthPages.DETAIL, record.id)
        })
        section(content, R.string.livestock_health_assessment_rationale_title)
        panel(content, getString(R.string.livestock_health_assessment_rationale_subtitle))
        panel(content, getString(R.string.livestock_health_assessment_disclaimer))
        val actions = ui.row()
        actions.addView(ui.button(R.string.livestock_health_assessment_open_tank) {
            openTank()
        }.apply { layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 2f) })
        actions.addView(ui.compactChoice(
            getString(R.string.livestock_health_assessment_follow), false) {
            navigate(LivestockHealthPages.DETAIL, record.id)
        }.apply { layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        content.addView(actions)
        content.addView(ui.spacer())
    }

internal fun LivestockHealthFragment.renderDetail(current: AquariumTankSnapshot, content: LinearLayout) {
        val record = current.healthObservations.firstOrNull { it.id == args.observationId }
        if (record == null) { missing(content); return }
        binding.appHeader.setupAquaHeader(this, AquaHeaderConfig(
            titleOverride = getString(R.string.livestock_health_detail_title),
            onBackClick = { findNavController().popBackStack() },
            primaryAction = if (record.isActive) AquaHeaderPrimaryAction(
                getString(R.string.livestock_health_detail_end)) { closeDialog(record) }
            else null
        ))
        val animal = current.livestock.firstOrNull { it.id == record.livestockId }
            ?: AquariumLivestock(id = record.livestockId, name = record.livestockName,
                category = record.livestockCategory, quantity = record.affectedCount,
                catalogEntryId = record.catalogEntryId)
        renderDetailOverview(content, animal, record)
        renderDetailTimeline(content, animal, record)
    }

private data class HealthHistoryEvent(
    val animal: AquariumLivestock,
    val dateText: String,
    val status: String,
    val count: Int,
    val noteText: String,
    val imageUri: String?
)

private fun LivestockHealthFragment.historyCard(content: LinearLayout, event: HealthHistoryEvent) {
        val line = ui.row()
        val labels = ui.column()
        labels.addView(ui.text(event.dateText, colorRes = R.color.aqua_accent_primary))
        labels.addView(ui.text(event.status, bold = true))
        val countLabel = getString(R.string.livestock_health_detail_affected, event.count)
        labels.addView(ui.text(if (event.noteText.isBlank()) countLabel
            else getString(R.string.livestock_health_history_note, countLabel, event.noteText),
            colorRes = R.color.aqua_card_text_secondary))
        line.addView(labels.apply {
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        line.addView(ui.speciesImage(event.animal, R.dimen.aqua_size_80,
            R.dimen.aqua_size_80, event.imageUri))
        val timeline = ui.row()
        timeline.addView(ui.text(getString(R.string.livestock_health_timeline_marker),
            R.dimen.aqua_text_size_title_large, R.color.aqua_accent_primary).apply {
            layoutParams = LinearLayout.LayoutParams(ui.size(R.dimen.aqua_size_24),
                ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        timeline.addView(ui.card(content = ui.column().apply { addView(line) }).apply {
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        content.addView(timeline)
        content.addView(ui.spacer(R.dimen.aqua_size_12))
    }

private fun LivestockHealthFragment.renderDetailOverview(content: LinearLayout,
    animal: AquariumLivestock, record: LivestockHealthObservation) {
        val header = ui.column()
        val overview = ui.row()
        overview.addView(ui.speciesImage(animal, R.dimen.aqua_size_96,
            R.dimen.aqua_size_96,
            record.checks.lastOrNull { !it.photoUri.isNullOrBlank() }?.photoUri
                ?: record.photoUri))
        val labels = ui.column().apply {
            setPadding(ui.size(R.dimen.aqua_size_12), 0, 0, 0)
            addView(ui.text(record.livestockName,
                R.dimen.aqua_text_size_body_large, bold = true))
            addView(ui.text(getString(R.string.livestock_health_home_affected_short,
                record.latestAffectedCount, animal.quantity),
                colorRes = R.color.aqua_card_text_secondary))
        }
        labels.layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        overview.addView(labels)
        overview.addView(statusBadge(getString(if (record.isActive)
            R.string.livestock_health_home_tracking
        else if (record.outcome == LivestockHealthTrend.RESOLVED)
            R.string.livestock_health_home_resolved
        else R.string.livestock_health_home_ended), record.isActive))
        header.addView(overview)
        header.addView(ui.spacer(R.dimen.aqua_size_12))
        header.addView(ui.text(record.symptoms.joinToString { ui.symptomName(it) }, bold = true))
        header.addView(ui.text(getString(R.string.livestock_health_detail_first,
            date(record.observedAtMillis)), colorRes = R.color.aqua_card_text_secondary))
        content.addView(ui.card(content = header))
}

private fun LivestockHealthFragment.renderDetailTimeline(content: LinearLayout,
    animal: AquariumLivestock, record: LivestockHealthObservation) {
        section(content, R.string.livestock_health_detail_status_title)
        val latest = ui.row()
        trendOptions().forEach { (value, label) ->
            latest.addView(ui.compactChoice(getString(label), record.latestTrend == value) {
                if (record.isActive) LivestockHealthCheckSheet.show(
                    childFragmentManager, args.tankId, record.id)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = ui.size(R.dimen.aqua_size_8)
                }
            })
        }
        content.addView(latest)
        section(content, R.string.livestock_health_detail_history)
        record.checks.asReversed().forEach { check ->
            historyCard(content, HealthHistoryEvent(animal, date(check.observedAtMillis),
                getString(R.string.livestock_health_history_event,
                    ui.symptomName(record.symptoms.first()),
                    getString(trendOptions().first { it.first == check.trend }.second)),
                check.affectedCount, check.note, check.photoUri))
        }
        historyCard(content, HealthHistoryEvent(animal, date(record.observedAtMillis),
            getString(R.string.livestock_health_history_event,
                ui.symptomName(record.symptoms.first()),
                getString(R.string.livestock_health_detail_initial)),
            record.affectedCount, record.note, record.photoUri))
        section(content, R.string.livestock_health_home_tank_data)
        panel(content, getString(R.string.livestock_health_home_tank_data_pending))
        maintenanceContext(content)
        content.addView(ui.button(R.string.livestock_health_home_tank_open) { openTank() })
        content.addView(ui.spacer())
        primary(R.string.livestock_health_detail_add_check, record.isActive) {
            LivestockHealthCheckSheet.show(childFragmentManager, args.tankId, record.id)
        }
}
