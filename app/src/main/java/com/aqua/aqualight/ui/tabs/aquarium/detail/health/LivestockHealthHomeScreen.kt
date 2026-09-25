package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend
import com.aqua.aqualight.application.care.CareTaskType
import java.util.Date

private const val RECENT_PREVIEW_COUNT = 2

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
            recentObservations(content, current, recent)
        }
        content.addView(ui.spacer())
        content.addView(ui.tankContext(tankSummary()) { openTank() })
        if (noRecords) {
            content.addView(ui.spacer(R.dimen.aqua_size_12))
            panel(content, getString(R.string.livestock_health_home_no_records_disclaimer))
        }
        binding.healthPrimaryAction.isVisible = false
        if (current.livestock.isEmpty()) {
            panel(content, getString(R.string.livestock_health_home_no_livestock))
        }
    }

private fun LivestockHealthFragment.tankSummary(): String {
    val lastWater = tankActivity.completedTasks.firstOrNull {
        it.type == CareTaskType.WATER_CHANGE
    } ?: return getString(R.string.livestock_health_home_tank_data_pending)
    val relative = DateUtils.getRelativeTimeSpanString(
        lastWater.completedAtMillis ?: lastWater.dueAtMillis,
        System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    return getString(R.string.livestock_health_home_tank_context,
        getString(R.string.livestock_health_home_tank_data_pending),
        getString(R.string.livestock_health_maintenance_water, relative, ""))
}

private fun LivestockHealthFragment.recentObservations(content: LinearLayout,
    current: AquariumTankSnapshot, recent: List<LivestockHealthObservation>) {
    val heading = ui.row()
    heading.addView(ui.heading(R.string.livestock_health_home_recent).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    })
    heading.addView(ui.text(getString(if (showAllObservations)
        R.string.livestock_health_home_show_less else R.string.livestock_health_home_view_all),
        colorRes = R.color.aqua_accent_primary).apply {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            showAllObservations = !showAllObservations
            tank?.let(::render)
        }
    })
    heading.addView(ui.image(R.drawable.ic_arrow_right, R.dimen.aqua_size_24,
        R.dimen.aqua_size_24))
    content.addView(ui.spacer())
    content.addView(heading)
    content.addView(ui.spacer(R.dimen.aqua_size_12))
    val list = ui.column()
    recent.take(if (showAllObservations) recent.size else RECENT_PREVIEW_COUNT)
        .forEachIndexed { index, record ->
            if (index > 0) {
                list.addView(View(requireContext()).apply {
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.aqua_card_outline))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ui.size(R.dimen.aqua_size_1)).apply {
                        topMargin = ui.size(R.dimen.aqua_size_8)
                        bottomMargin = ui.size(R.dimen.aqua_size_8)
                    }
                })
            }
            list.addView(recentObservationRow(current, record))
        }
    content.addView(ui.card(content = list))
}

private fun LivestockHealthFragment.recentObservationRow(current: AquariumTankSnapshot,
    record: LivestockHealthObservation): LinearLayout = ui.row().apply {
    val animal = current.livestock.firstOrNull { it.id == record.livestockId }
        ?: record.animalPlaceholder()
    addView(ui.speciesImage(animal, R.dimen.aqua_size_52, R.dimen.aqua_size_52,
        record.lastPhoto()))
    addView(ui.column().apply {
        addView(ui.text(record.livestockName, bold = true))
        addView(ui.text(getString(R.string.livestock_health_history_event,
            record.symptoms.joinToString { ui.symptomName(it) }, date(record.observedAtMillis)),
            colorRes = R.color.aqua_card_text_secondary))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = ui.size(R.dimen.aqua_size_12) }
    })
    addView(statusBadge(statusLabel(record), record.isActive))
    addView(ui.image(R.drawable.ic_arrow_right, R.dimen.aqua_size_24,
        R.dimen.aqua_size_24))
    isClickable = true
    isFocusable = true
    setOnClickListener { navigate(LivestockHealthPages.DETAIL, record.id) }
}

private fun LivestockHealthFragment.statusLabel(record: LivestockHealthObservation): String =
    getString(if (record.isActive) R.string.livestock_health_home_tracking
        else if (record.outcome == LivestockHealthTrend.RESOLVED) R.string.livestock_health_home_resolved
        else R.string.livestock_health_home_ended)

private fun LivestockHealthObservation.lastPhoto(): String? =
    checks.lastOrNull { !it.photoUri.isNullOrBlank() }?.photoUri ?: photoUri

private fun LivestockHealthObservation.animalPlaceholder(): AquariumLivestock =
    AquariumLivestock(id = livestockId, name = livestockName, category = livestockCategory,
        quantity = affectedCount, catalogEntryId = catalogEntryId)

internal fun LivestockHealthFragment.observationCard(
        content: LinearLayout,
        current: AquariumTankSnapshot,
        observation: LivestockHealthObservation
    ) {
        val cardContent = ui.column()
        val existing = current.livestock.firstOrNull { it.id == observation.livestockId }
        val header = ui.row()
        val animal = existing ?: observation.animalPlaceholder()
        header.addView(ui.speciesImage(animal,
            R.dimen.aqua_size_52, R.dimen.aqua_size_52,
            observation.lastPhoto()))
        val textColumn = ui.column()
        textColumn.addView(ui.text(observation.livestockName,
            R.dimen.aqua_text_size_body_large, bold = true))
        textColumn.addView(ui.text(observation.symptoms.joinToString { ui.symptomName(it) },
            colorRes = R.color.aqua_card_text_secondary))
        val observed = if (DateUtils.isToday(observation.observedAtMillis))
            getString(R.string.livestock_health_home_today_time,
                DateFormat.getTimeFormat(requireContext()).format(Date(observation.observedAtMillis)))
        else date(observation.observedAtMillis)
        val last = observation.checks.lastOrNull()
        val dateLine = if (last == null) observed else getString(
            R.string.livestock_health_home_observed_with_check, observed,
            if (DateUtils.isToday(last.observedAtMillis))
                getString(R.string.livestock_health_home_today)
            else date(last.observedAtMillis))
        textColumn.addView(ui.text(dateLine,
            colorRes = R.color.aqua_card_text_secondary))
        textColumn.layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = ui.size(R.dimen.aqua_size_12)
        }
        header.addView(textColumn)
        header.addView(statusBadge(statusLabel(observation), observation.isActive))
        header.addView(ui.image(R.drawable.ic_arrow_right, R.dimen.aqua_size_24,
            R.dimen.aqua_size_24))
        cardContent.addView(header)
        content.addView(ui.card(content = cardContent).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { navigate(LivestockHealthPages.DETAIL, observation.id) }
        })
        content.addView(ui.spacer(R.dimen.aqua_size_12))
    }
