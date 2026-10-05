package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

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
    fun symptomLabelRes(symptomKey: String): Int =
        LivestockHealthObservationCatalog.symptomLabelRes(symptomKey)

    fun observationLabel(
        fragment: Fragment,
        symptomKey: String,
        otherObservation: String
    ): String = resolveObservationLabel(
        symptomKey = symptomKey,
        otherObservation = otherObservation,
        fallbackLabel = fragment.getString(symptomLabelRes(symptomKey))
    )

    internal fun resolveObservationLabel(
        symptomKey: String,
        otherObservation: String,
        fallbackLabel: String
    ): String {
        val customObservation = otherObservation.trim()
        return if (symptomKey == SYMPTOM_OTHER && customObservation.isNotEmpty()) {
            customObservation
        } else {
            fallbackLabel
        }
    }
}
