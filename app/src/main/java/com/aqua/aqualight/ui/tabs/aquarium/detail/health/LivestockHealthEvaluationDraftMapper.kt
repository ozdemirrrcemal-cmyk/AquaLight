package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.lifecycle.SavedStateHandle
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput

internal fun SavedStateHandle.toLivestockObservationInput(
    tankId: Long,
    livestockId: Long,
    symptomKey: String,
    affectedCount: Int
): LivestockObservationInput? {
    val symptoms = get<ArrayList<String>>(LivestockHealthObservationFragment.DRAFT_SYMPTOMS)
        .orEmpty()
    val matchesSelection =
        get<Long>(LivestockHealthObservationFragment.DRAFT_TANK_ID) == tankId &&
            get<Long>(LivestockHealthObservationFragment.DRAFT_LIVESTOCK_ID) == livestockId &&
            symptoms.firstOrNull() == symptomKey

    return if (matchesSelection) {
        LivestockObservationInput(
            requestId = get<String>(
                LivestockHealthObservationFragment.DRAFT_REQUEST_ID
            ).orEmpty(),
            tankId = tankId,
            livestockId = livestockId,
            symptomKeys = symptoms,
            onsetKey = get<String>(
                LivestockHealthObservationFragment.DRAFT_ONSET
            ).orEmpty(),
            otherObservation = get<String>(
                LivestockHealthObservationFragment.DRAFT_OTHER
            ).orEmpty(),
            note = get<String>(
                LivestockHealthObservationFragment.DRAFT_NOTE
            ).orEmpty(),
            photoUris = get<ArrayList<String>>(
                LivestockHealthObservationFragment.DRAFT_PHOTOS
            ).orEmpty(),
            affectedCount = get<Int>(
                LivestockHealthObservationFragment.DRAFT_AFFECTED
            ) ?: affectedCount
        )
    } else {
        null
    }
}
