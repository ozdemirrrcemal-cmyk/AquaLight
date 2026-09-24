package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import android.os.Bundle
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeDensity
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeObservationLocation
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTrend
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTypeId

internal data class AlgaeObservationSavedState(
    val formVisible: Boolean = false,
    val selectedType: AlgaeTypeId? = null,
    val locations: Set<AlgaeObservationLocation> = emptySet(),
    val density: AlgaeDensity? = null,
    val trend: AlgaeTrend? = null,
    val note: String = ""
)

internal fun readAlgaeObservationSavedState(
    state: Bundle?
): AlgaeObservationSavedState {
    if (state == null) {
        return AlgaeObservationSavedState()
    }

    return AlgaeObservationSavedState(
        formVisible = state.getBoolean(KEY_FORM_VISIBLE),
        selectedType = state.getString(KEY_SELECTED_TYPE)
            ?.let { value -> enumValueOrNull<AlgaeTypeId>(value) },
        locations = state.getStringArrayList(KEY_SELECTED_LOCATIONS)
            .orEmpty()
            .mapNotNull { value ->
                enumValueOrNull<AlgaeObservationLocation>(value)
            }
            .toSet(),
        density = state.getString(KEY_SELECTED_DENSITY)
            ?.let { value -> enumValueOrNull<AlgaeDensity>(value) },
        trend = state.getString(KEY_SELECTED_TREND)
            ?.let { value -> enumValueOrNull<AlgaeTrend>(value) },
        note = state.getString(KEY_NOTE).orEmpty()
    )
}

internal fun writeAlgaeObservationSavedState(
    outState: Bundle,
    state: AlgaeObservationSavedState
) {
    outState.putBoolean(KEY_FORM_VISIBLE, state.formVisible)
    outState.putString(KEY_SELECTED_TYPE, state.selectedType?.name)
    outState.putStringArrayList(
        KEY_SELECTED_LOCATIONS,
        ArrayList(state.locations.map(AlgaeObservationLocation::name))
    )
    outState.putString(KEY_SELECTED_DENSITY, state.density?.name)
    outState.putString(KEY_SELECTED_TREND, state.trend?.name)
    outState.putString(KEY_NOTE, state.note)
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { entry ->
        entry.name == value
    }

private const val KEY_FORM_VISIBLE = "formVisible"
private const val KEY_SELECTED_TYPE = "selectedType"
private const val KEY_SELECTED_LOCATIONS = "selectedLocations"
private const val KEY_SELECTED_DENSITY = "selectedDensity"
private const val KEY_SELECTED_TREND = "selectedTrend"
private const val KEY_NOTE = "note"
