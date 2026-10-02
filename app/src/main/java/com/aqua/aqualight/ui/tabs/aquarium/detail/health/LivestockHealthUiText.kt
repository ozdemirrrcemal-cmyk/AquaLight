package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.annotation.StringRes
import com.aqua.aqualight.R

internal object LivestockHealthUiText {
    const val SYMPTOM_NORMAL = "normal"
    const val SYMPTOM_SURFACE = "surface"
    const val SYMPTOM_APPETITE = "appetite"
    const val SYMPTOM_SWIMMING = "swimming"
    const val SYMPTOM_SPOT = "spot"
    const val SYMPTOM_HIDING = "hiding"
    const val SYMPTOM_FINS = "fins"
    const val SYMPTOM_OTHER = "other"

    @StringRes
    fun symptomLabelRes(symptomKey: String): Int = when (symptomKey) {
        SYMPTOM_NORMAL -> R.string.livestock_health_symptom_normal
        SYMPTOM_APPETITE -> R.string.livestock_health_symptom_appetite
        SYMPTOM_SWIMMING -> R.string.livestock_health_symptom_swimming
        SYMPTOM_SPOT -> R.string.livestock_health_symptom_spot
        SYMPTOM_HIDING -> R.string.livestock_health_symptom_hiding
        SYMPTOM_FINS -> R.string.livestock_health_symptom_fins
        SYMPTOM_OTHER -> R.string.livestock_health_symptom_other
        else -> R.string.livestock_health_symptom_surface
    }
}
