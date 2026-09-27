package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class LivestockHealthFragment : HealthObservationListFragment(
    R.id.livestockHealthFragment, R.string.screen_title_livestock_health) {
    private val args: LivestockHealthFragmentArgs by navArgs()

    override fun newDirections() = LivestockHealthFragmentDirections
        .actionLivestockHealthFragmentToHealthObservationFormFragment(
            tankId = args.tankId, healthKind = args.healthKind)

    override fun detailDirections(id: Long) = LivestockHealthFragmentDirections
        .actionLivestockHealthFragmentToHealthObservationDetailFragment(
            tankId = args.tankId, healthKind = args.healthKind, observationId = id)
}
