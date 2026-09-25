package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend

internal object LivestockHealthPages {
    const val FORM = "form"
    const val ASSESSMENT = "assessment"
    const val DETAIL = "detail"
}

internal fun LivestockHealthFragment.renderHome(current: AquariumTankSnapshot, content: LinearLayout) {
        val noRecords = current.healthObservations.isEmpty()
        content.addView(ui.hero(noRecords, current.livestock.isNotEmpty()) { navigate(LivestockHealthPages.FORM) })
        section(content, if (noRecords) R.string.livestock_health_home_followups
            else R.string.livestock_health_home_active)
        val active = current.healthObservations.filter { it.isActive }.sortedByDescending { it.observedAtMillis }
        val recent = current.healthObservations.sortedByDescending { it.observedAtMillis }
        if (active.isEmpty()) {
            if (noRecords) content.addView(ui.emptyState())
        } else {
            active.forEach { record -> observationCard(content, current, record) }
        }
        if (recent.isNotEmpty()) {
            section(content, R.string.livestock_health_home_recent)
            recent.forEach { record -> observationCard(content, current, record) }
        }
        content.addView(ui.spacer())
        content.addView(ui.tankContext(
            getString(R.string.livestock_health_home_tank_data_pending)) { openTank() })
        if (noRecords) {
            content.addView(ui.spacer(R.dimen.aqua_size_12))
            panel(content, getString(R.string.livestock_health_home_no_records_disclaimer))
        }
        binding.healthPrimaryAction.isVisible = false
        if (current.livestock.isEmpty()) {
            panel(content, getString(R.string.livestock_health_home_no_livestock))
        }
    }

internal fun LivestockHealthFragment.observationCard(
        content: LinearLayout,
        current: AquariumTankSnapshot,
        observation: LivestockHealthObservation
    ) {
        val cardContent = ui.column()
        val existing = current.livestock.firstOrNull { it.id == observation.livestockId }
        val header = ui.row()
        val animal = existing ?: AquariumLivestock(id = observation.livestockId,
            name = observation.livestockName, category = observation.livestockCategory,
            quantity = observation.affectedCount, catalogEntryId = observation.catalogEntryId)
        header.addView(ui.speciesImage(animal,
            R.dimen.aqua_size_52, R.dimen.aqua_size_52,
            observation.checks.lastOrNull { !it.photoUri.isNullOrBlank() }?.photoUri
                ?: observation.photoUri))
        val textColumn = ui.column()
        textColumn.addView(ui.text(observation.livestockName,
            R.dimen.aqua_text_size_body_large, bold = true))
        textColumn.addView(ui.text(observation.symptoms.joinToString { ui.symptomName(it) },
            colorRes = R.color.aqua_card_text_secondary))
        textColumn.addView(ui.text(date(observation.observedAtMillis),
            colorRes = R.color.aqua_card_text_secondary))
        textColumn.layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = ui.size(R.dimen.aqua_size_12)
        }
        header.addView(textColumn)
        header.addView(statusBadge(getString(if (observation.isActive)
            R.string.livestock_health_home_tracking
        else if (observation.outcome == LivestockHealthTrend.RESOLVED)
            R.string.livestock_health_home_resolved
        else R.string.livestock_health_home_ended), observation.isActive))
        cardContent.addView(header)
        content.addView(ui.card(content = cardContent).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { navigate(LivestockHealthPages.DETAIL, observation.id) }
        })
        content.addView(ui.spacer(R.dimen.aqua_size_12))
    }
