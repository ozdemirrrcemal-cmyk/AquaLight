package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class PlantHealthFragment : HealthObservationListFragment(
    R.id.plantHealthFragment, R.string.screen_title_plant_health) {
    private val args: PlantHealthFragmentArgs by navArgs()

    override fun newDirections() = PlantHealthFragmentDirections
        .actionPlantHealthFragmentToHealthObservationFormFragment(
            tankId = args.tankId, healthKind = args.healthKind)

    override fun detailDirections(id: Long) = PlantHealthFragmentDirections
        .actionPlantHealthFragmentToHealthObservationDetailFragment(
            tankId = args.tankId, healthKind = args.healthKind, observationId = id)
}
