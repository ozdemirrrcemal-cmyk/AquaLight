package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumMaterialCategoryKeys
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeAnalysisPriority
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeFactorStrength
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTankContext
import com.aqua.aqualight.databinding.FragmentTankAlgaeAnalysisBinding
import com.aqua.aqualight.databinding.ItemAlgaeAnalysisRowBinding
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal fun buildAlgaeTankContext(
    tank: AquariumTankSnapshot
): AlgaeTankContext {
    val tankAgeDays = tank.setupDateEpochDay?.let { setupEpochDay ->
        ChronoUnit.DAYS
            .between(
                LocalDate.ofEpochDay(setupEpochDay),
                LocalDate.now()
            )
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    val hasCo2 = tank.materials.any { material ->
        material.categoryKey == AquariumMaterialCategoryKeys.CO2
    }

    return AlgaeTankContext(
        tankAgeDays = tankAgeDays,
        hasCo2 = hasCo2,
        co2ScheduleKnown = false,
        plantCount = tank.plants.size
    )
}

internal fun applyAlgaeAssessmentColors(
    context: Context,
    binding: FragmentTankAlgaeAnalysisBinding,
    priority: AlgaeAnalysisPriority
) {
    val background = when (priority) {
        AlgaeAnalysisPriority.ACTION_RECOMMENDED ->
            R.color.aqua_bg_maintenance_profile_percent_warning_fill
        AlgaeAnalysisPriority.REVIEW ->
            R.color.aqua_surface_action
        AlgaeAnalysisPriority.MONITOR ->
            R.color.aqua_surface_positive
    }

    val stroke = when (priority) {
        AlgaeAnalysisPriority.ACTION_RECOMMENDED ->
            R.color.aqua_content_warning
        AlgaeAnalysisPriority.REVIEW ->
            R.color.aqua_accent_primary
        AlgaeAnalysisPriority.MONITOR ->
            R.color.aqua_outline_positive
    }

    val title = when (priority) {
        AlgaeAnalysisPriority.ACTION_RECOMMENDED ->
            R.color.aqua_content_warning
        AlgaeAnalysisPriority.REVIEW ->
            R.color.aqua_accent_primary
        AlgaeAnalysisPriority.MONITOR ->
            R.color.aqua_accent_positive
    }

    binding.cardAssessment.setCardBackgroundColor(
        ContextCompat.getColor(context, background)
    )
    binding.cardAssessment.strokeColor =
        ContextCompat.getColor(context, stroke)
    binding.tvAssessmentTitle.setTextColor(
        ContextCompat.getColor(context, title)
    )
}

internal fun addAlgaeAnalysisRow(
    parent: LinearLayout,
    title: String,
    subtitle: String,
    subtitleColor: Int? = null
) {
    val row = ItemAlgaeAnalysisRowBinding.inflate(
        LayoutInflater.from(parent.context),
        parent,
        false
    )

    row.tvTitle.text = title
    row.tvSubtitle.text = subtitle
    row.tvSubtitle.isVisible = subtitle.isNotBlank()

    if (subtitleColor != null) {
        row.tvSubtitle.setTextColor(subtitleColor)
    }

    parent.addView(row.root)
}

internal fun algaeFactorStrengthColor(
    context: Context,
    strength: AlgaeFactorStrength
): Int {
    val colorRes = when (strength) {
        AlgaeFactorStrength.HIGH -> R.color.aqua_content_warning
        AlgaeFactorStrength.MEDIUM -> R.color.aqua_accent_primary
        AlgaeFactorStrength.LOW -> R.color.aqua_accent_positive
    }

    return ContextCompat.getColor(context, colorRes)
}
