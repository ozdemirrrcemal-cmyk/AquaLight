package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.LivestockHealthSymptom
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories

internal fun trendOptions(): List<Pair<LivestockHealthTrend, Int>> = listOf(
        LivestockHealthTrend.INCREASING to R.string.livestock_health_form_increasing,
        LivestockHealthTrend.SAME to R.string.livestock_health_form_same,
        LivestockHealthTrend.DECREASING to R.string.livestock_health_form_decreasing,
        LivestockHealthTrend.RESOLVED to R.string.livestock_health_form_resolved
    )

internal fun symptomsFor(category: String): List<LivestockHealthSymptom> {
        val general = listOf(
            LivestockHealthSymptom.APPETITE_CHANGE,
            LivestockHealthSymptom.ACTIVITY_CHANGE,
            LivestockHealthSymptom.COLOR_CHANGE
        )
        return when (category) {
            LivestockCategories.FISH -> listOf(
                LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE,
                LivestockHealthSymptom.APPETITE_CHANGE,
                LivestockHealthSymptom.SWIMMING_CHANGE,
                LivestockHealthSymptom.SKIN_OR_SPOTS,
                LivestockHealthSymptom.FIN_CHANGE,
                LivestockHealthSymptom.OTHER
            )
            LivestockCategories.SHRIMP, LivestockCategories.CRAB_CRAYFISH -> general + listOf(
                LivestockHealthSymptom.MOLTING_CHANGE, LivestockHealthSymptom.OTHER
            )
            LivestockCategories.SNAIL -> general + listOf(
                LivestockHealthSymptom.SHELL_CHANGE, LivestockHealthSymptom.OTHER
            )
            LivestockCategories.CORAL -> listOf(
                LivestockHealthSymptom.COLOR_CHANGE,
                LivestockHealthSymptom.POLYP_RETRACTION,
                LivestockHealthSymptom.OTHER
            )
            else -> general + LivestockHealthSymptom.OTHER
        }
    }
