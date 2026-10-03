package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

internal class LivestockHealthNavigator(
    private val fragment: Fragment,
    private val tankId: Long
) {
    private var isNavigating = false

    fun reset() {
        isNavigating = false
    }

    fun openNewObservation(hasLivestock: Boolean) {
        if (!isNavigating && hasLivestock) {
            isNavigating = fragment.findNavController().navigateSafelyFrom(
                sourceDestinationId = R.id.livestockHealthFragment,
                directions = LivestockHealthFragmentDirections
                    .actionLivestockHealthFragmentToLivestockHealthObservationFragment(tankId)
            )
        }
    }

    fun openActiveFollowup(entry: ActiveLivestockFollowupUi) {
        if (!isNavigating) {
            isNavigating = fragment.findNavController().navigateSafelyFrom(
                sourceDestinationId = R.id.livestockHealthFragment,
                directions = LivestockHealthFragmentDirections
                    .actionLivestockHealthFragmentToLivestockHealthFollowUpFragment(
                        tankId = tankId,
                        livestockId = entry.livestockId,
                        symptomKey = entry.symptomKey,
                        affectedCount = entry.affectedCount,
                        readOnly = false
                    )
            )
        }
    }

    fun openPastFollowup(entry: ClosedLivestockFollowupUi) {
        if (!isNavigating) {
            isNavigating = fragment.findNavController().navigateSafelyFrom(
                sourceDestinationId = R.id.livestockHealthFragment,
                directions = LivestockHealthFragmentDirections
                    .actionLivestockHealthFragmentToLivestockHealthFollowUpFragment(
                        tankId = tankId,
                        livestockId = entry.livestockId,
                        symptomKey = entry.symptomKey,
                        affectedCount = entry.affectedCount,
                        readOnly = true,
                        closeReason = entry.closeReason
                    )
            )
        }
    }

    fun openAllPastFollowups(hasHistory: Boolean) {
        if (!isNavigating && hasHistory) {
            isNavigating = fragment.findNavController().navigateSafelyFrom(
                sourceDestinationId = R.id.livestockHealthFragment,
                directions = LivestockHealthFragmentDirections
                    .actionLivestockHealthFragmentToLivestockHealthHistoryFragment(tankId)
            )
        }
    }
}
