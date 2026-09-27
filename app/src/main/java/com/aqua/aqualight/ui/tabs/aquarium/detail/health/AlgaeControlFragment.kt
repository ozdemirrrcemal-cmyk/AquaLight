package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class AlgaeControlFragment : HealthObservationListFragment(
    R.id.algaeControlFragment, R.string.tank_health_tab_algae_control) {
    private val args: AlgaeControlFragmentArgs by navArgs()

    override fun newDirections() = AlgaeControlFragmentDirections
        .actionAlgaeControlFragmentToHealthObservationFormFragment(
            tankId = args.tankId, healthKind = args.healthKind)

    override fun detailDirections(id: Long) = AlgaeControlFragmentDirections
        .actionAlgaeControlFragmentToHealthObservationDetailFragment(
            tankId = args.tankId, healthKind = args.healthKind, observationId = id)
}
