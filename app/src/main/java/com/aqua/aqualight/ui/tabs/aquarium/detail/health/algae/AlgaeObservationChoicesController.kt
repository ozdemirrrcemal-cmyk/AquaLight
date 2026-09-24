package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import android.content.Context
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeDensity
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeObservationLocation
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTrend
import com.aqua.aqualight.databinding.FragmentTankAlgaeControlBinding
import com.google.android.material.button.MaterialButton

internal class AlgaeObservationChoicesController(
    private val context: Context,
    private val binding: FragmentTankAlgaeControlBinding,
    private val onChanged: () -> Unit
) {

    private val selectedLocations = linkedSetOf<AlgaeObservationLocation>()
    private var selectedDensity: AlgaeDensity? = null
    private var selectedTrend: AlgaeTrend? = null

    val locations: Set<AlgaeObservationLocation>
        get() = selectedLocations.toSet()

    val density: AlgaeDensity?
        get() = selectedDensity

    val trend: AlgaeTrend?
        get() = selectedTrend

    val complete: Boolean
        get() = selectedLocations.isNotEmpty() &&
            selectedDensity != null &&
            selectedTrend != null

    fun bind() {
        locationButtons().forEach { (button, location) ->
            button.setOnClickListener {
                if (location in selectedLocations) {
                    selectedLocations -= location
                } else {
                    selectedLocations += location
                }
                renderChoiceButton(button, location in selectedLocations)
                onChanged()
            }
        }

        densityButtons().forEach { (button, value) ->
            button.setOnClickListener {
                selectedDensity = value
                renderAll()
                onChanged()
            }
        }

        trendButtons().forEach { (button, value) ->
            button.setOnClickListener {
                selectedTrend = value
                renderAll()
                onChanged()
            }
        }

        renderAll()
    }

    fun reset() {
        selectedLocations.clear()
        selectedDensity = null
        selectedTrend = null
        renderAll()
        onChanged()
    }

    fun restore(
        locations: Set<AlgaeObservationLocation>,
        density: AlgaeDensity?,
        trend: AlgaeTrend?
    ) {
        selectedLocations.clear()
        selectedLocations += locations
        selectedDensity = density
        selectedTrend = trend
        renderAll()
        onChanged()
    }

    private fun renderAll() {
        locationButtons().forEach { (button, location) ->
            renderChoiceButton(button, location in selectedLocations)
        }
        densityButtons().forEach { (button, value) ->
            renderChoiceButton(button, value == selectedDensity)
        }
        trendButtons().forEach { (button, value) ->
            renderChoiceButton(button, value == selectedTrend)
        }
    }

    private fun renderChoiceButton(
        button: MaterialButton,
        selected: Boolean
    ) {
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                if (selected) {
                    R.color.aqua_surface_action
                } else {
                    R.color.aqua_button_secondary_container_tint
                }
            )
        )
        button.strokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                if (selected) {
                    R.color.aqua_accent_primary
                } else {
                    R.color.aqua_card_metric_outline
                }
            )
        )
        button.strokeWidth = context.resources.getDimensionPixelSize(
            if (selected) {
                R.dimen.aqua_size_2
            } else {
                R.dimen.aqua_size_1
            }
        )
    }

    private fun locationButtons(): List<Pair<MaterialButton, AlgaeObservationLocation>> =
        listOf(
            binding.btnLocationFront to AlgaeObservationLocation.FRONT_GLASS,
            binding.btnLocationBack to AlgaeObservationLocation.BACK_GLASS,
            binding.btnLocationSide to AlgaeObservationLocation.SIDE_GLASS,
            binding.btnLocationPlants to AlgaeObservationLocation.PLANTS,
            binding.btnLocationWood to AlgaeObservationLocation.ROOT_WOOD,
            binding.btnLocationRocks to AlgaeObservationLocation.ROCKS,
            binding.btnLocationSubstrate to AlgaeObservationLocation.SUBSTRATE,
            binding.btnLocationEquipment to AlgaeObservationLocation.EQUIPMENT,
            binding.btnLocationWaterColumn to AlgaeObservationLocation.WATER_COLUMN,
            binding.btnLocationOther to AlgaeObservationLocation.OTHER
        )

    private fun densityButtons(): List<Pair<MaterialButton, AlgaeDensity>> =
        listOf(
            binding.btnDensityLow to AlgaeDensity.LOW,
            binding.btnDensityMedium to AlgaeDensity.MEDIUM,
            binding.btnDensityHigh to AlgaeDensity.HIGH
        )

    private fun trendButtons(): List<Pair<MaterialButton, AlgaeTrend>> =
        listOf(
            binding.btnTrendIncreasing to AlgaeTrend.INCREASING,
            binding.btnTrendStable to AlgaeTrend.STABLE,
            binding.btnTrendDecreasing to AlgaeTrend.DECREASING
        )
}
