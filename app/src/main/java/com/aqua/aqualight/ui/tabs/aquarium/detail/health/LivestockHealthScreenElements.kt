package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.graphics.drawable.GradientDrawable
import android.widget.EditText
import androidx.core.content.ContextCompat
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.aqua.aqualight.R
import com.aqua.aqualight.application.care.CareTaskType

private const val NOTE_MAX_LINES = 4
internal const val DATE_REQUEST = "livestock_health_onset_date"
internal const val TREND_REQUEST = "livestock_health_initial_trend"
internal const val CLOSE_REQUEST = "livestock_health_close_confirmation"

internal fun LivestockHealthFragment.noteField(content: LinearLayout, value: String, onChange: (String) -> Unit) {
        val field = EditText(requireContext()).apply {
            hint = getString(R.string.livestock_health_form_note_hint)
            minLines = 2
            maxLines = NOTE_MAX_LINES
            setText(value)
            doAfterTextChanged { onChange(it?.toString().orEmpty()) }
        }
        content.addView(field)
    }

internal fun LivestockHealthFragment.maintenanceContext(content: LinearLayout) {
        val lastWater = tankActivity.completedTasks.firstOrNull { it.type == CareTaskType.WATER_CHANGE }
        if (lastWater == null) {
            panel(content, getString(R.string.livestock_health_maintenance_none))
        } else {
            val date = date(lastWater.completedAtMillis ?: lastWater.dueAtMillis)
            val percent = lastWater.waterChangePercent?.let {
                getString(R.string.livestock_health_maintenance_percent, it)
            }.orEmpty()
            panel(content, getString(R.string.livestock_health_maintenance_water, date, percent))
        }
        val lastFilter = tankActivity.completedTasks.firstOrNull { it.type in setOf(
            CareTaskType.FILTER_MAINTENANCE, CareTaskType.FILTER_CHANGE,
            CareTaskType.PRE_FILTER_CLEANING
        ) }
        if (lastFilter != null) {
            panel(content, getString(R.string.livestock_health_maintenance_filter,
                date(lastFilter.completedAtMillis ?: lastFilter.dueAtMillis)))
        }
    }

internal fun LivestockHealthFragment.panel(content: LinearLayout, title: String, subtitle: String? = null) {
        val body = ui.column()
        body.addView(ui.text(title, bold = true))
        if (subtitle != null) {
            body.addView(ui.spacer(R.dimen.aqua_size_8))
            body.addView(ui.text(subtitle, colorRes = R.color.aqua_card_text_secondary))
        }
        content.addView(ui.card(content = body))
        content.addView(ui.spacer(R.dimen.aqua_size_12))
    }

internal fun LivestockHealthFragment.section(content: LinearLayout, title: Int) {
        content.addView(ui.spacer())
        content.addView(ui.heading(title))
        content.addView(ui.spacer(R.dimen.aqua_size_12))
    }

internal fun LivestockHealthFragment.missing(content: LinearLayout) {
        binding.healthPrimaryAction.isVisible = false
        panel(content, getString(R.string.aquarium_livestock_no_longer_exists_message))
    }

internal fun LivestockHealthFragment.primary(label: Int, enabled: Boolean = true, onClick: () -> Unit) {
        binding.healthPrimaryAction.isVisible = true
        binding.healthPrimaryAction.setText(label)
        binding.healthPrimaryAction.isEnabled = enabled && !saving
        binding.healthPrimaryAction.setOnClickListener { onClick() }
    }

internal fun LivestockHealthFragment.statusBadge(label: String, active: Boolean): android.widget.TextView = ui.text(
        label, R.dimen.aqua_text_size_body_small,
        if (active) R.color.aqua_content_warning else R.color.aqua_card_text_secondary
    ).apply {
        setPadding(ui.size(R.dimen.aqua_size_10), ui.size(R.dimen.aqua_size_6),
            ui.size(R.dimen.aqua_size_10), ui.size(R.dimen.aqua_size_6))
        background = GradientDrawable().apply {
            cornerRadius = ui.size(R.dimen.aqua_size_20).toFloat()
            setColor(ContextCompat.getColor(requireContext(), if (active)
                R.color.aqua_bg_maintenance_profile_percent_warning_fill
            else R.color.aqua_bg_maintenance_tab_unselected_fill))
            setStroke(ui.size(R.dimen.aqua_size_1), ContextCompat.getColor(requireContext(),
                if (active) R.color.aqua_content_warning else R.color.aqua_card_outline))
        }
    }
