package com.aqua.aqualight.smoke

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationKind
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.HealthObservationViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.HealthObservationFormFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.HealthObservationDetailFragment
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.AlgaeControlFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.AlgaeControlFragmentArgs
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.AlgaeControlFragmentDirections
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.PlantHealthFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.PlantHealthFragmentArgs
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.PlantHealthFragmentDirections
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.LivestockHealthFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.LivestockHealthFragmentArgs
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.LivestockHealthFragmentDirections
import kotlinx.coroutines.delay

/** Actual central graph, production delegates and SavedState extras in the minified application. */
internal class HealthObservationNavigationSmoke(
    private val host: NavHostFragment, private val capture: (String) -> Unit
) {
    suspend fun verify() {
        HealthObservationKind.entries.forEach { kind -> verifyKind(kind) }
    }

    private suspend fun verifyKind(kind: HealthObservationKind) {
        val route = when (kind) {
            HealthObservationKind.ALGAE -> R.id.algaeControlFragment
            HealthObservationKind.PLANT -> R.id.plantHealthFragment
            HealthObservationKind.LIVESTOCK -> R.id.livestockHealthFragment
        }
        val arguments = when (kind) {
            HealthObservationKind.ALGAE -> AlgaeControlFragmentArgs(TANK).toBundle()
            HealthObservationKind.PLANT -> PlantHealthFragmentArgs(TANK).toBundle()
            HealthObservationKind.LIVESTOCK -> LivestockHealthFragmentArgs(TANK).toBundle()
        }
        val graph = host.navController.navInflater.inflate(R.navigation.nav_aquarium)
        graph.setStartDestination(route)
        host.navController.setGraph(graph, arguments)
        val list = current(kind)
        capture("${kind.name.lowercase()}-observations")
        val formDirection = form(kind)
        check(host.navController.navigateSafelyFrom(route, formDirection))
        check(!host.navController.navigateSafelyFrom(route, formDirection))
        val form = current(kind)
        capture("${kind.name.lowercase()}-observation-form")
        check(form !== list && host.childFragmentManager.primaryNavigationFragment is HealthObservationFormFragment)
        check(host.navController.popBackStack())
        check(current(kind) === list)
        check(host.navController.navigateSafelyFrom(route, detail(kind)))
        val detail = current(kind)
        capture("${kind.name.lowercase()}-observation-detail")
        check(detail !== form && detail !== list && detail.observationId == RECORD)
        check(host.childFragmentManager.primaryNavigationFragment is HealthObservationDetailFragment)
        check(host.navController.popBackStack())
        check(current(kind) === list)
    }

    private fun form(kind: HealthObservationKind): NavDirections = when (kind) {
        HealthObservationKind.ALGAE -> AlgaeControlFragmentDirections
            .actionAlgaeControlFragmentToHealthObservationFormFragment(TANK, kind.name)
        HealthObservationKind.PLANT -> PlantHealthFragmentDirections
            .actionPlantHealthFragmentToHealthObservationFormFragment(TANK, kind.name)
        HealthObservationKind.LIVESTOCK -> LivestockHealthFragmentDirections
            .actionLivestockHealthFragmentToHealthObservationFormFragment(TANK, kind.name)
    }

    private fun detail(kind: HealthObservationKind): NavDirections = when (kind) {
        HealthObservationKind.ALGAE -> AlgaeControlFragmentDirections
            .actionAlgaeControlFragmentToHealthObservationDetailFragment(TANK, kind.name, RECORD)
        HealthObservationKind.PLANT -> PlantHealthFragmentDirections
            .actionPlantHealthFragmentToHealthObservationDetailFragment(TANK, kind.name, RECORD)
        HealthObservationKind.LIVESTOCK -> LivestockHealthFragmentDirections
            .actionLivestockHealthFragmentToHealthObservationDetailFragment(TANK, kind.name, RECORD)
    }

    private suspend fun current(kind: HealthObservationKind): HealthObservationViewModel {
        host.childFragmentManager.executePendingTransactions()
        delay(SETTLE_MILLIS)
        val fragment = host.childFragmentManager.primaryNavigationFragment
            ?: error("Health observation route did not create a primary navigation fragment")
        check(fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && fragment.view != null)
        return ViewModelProvider(fragment)[HealthObservationViewModel::class.java].also {
            check(it.tankId == TANK && it.kind == kind)
        }
    }

    private companion object {
        const val TANK = 901L
        const val RECORD = 902L
        const val SETTLE_MILLIS = 300L
    }
}
