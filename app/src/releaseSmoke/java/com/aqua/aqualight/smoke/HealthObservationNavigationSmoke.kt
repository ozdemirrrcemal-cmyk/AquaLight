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
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.HealthLoadState
import android.view.View
import android.widget.TextView
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
import kotlinx.coroutines.withTimeout

/** Actual central graph, production delegates and SavedState extras in the minified application. */
internal class HealthObservationNavigationSmoke(
    private val host: NavHostFragment, private val data: HealthObservationSmokeData,
    private val capture: (String) -> Unit
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
            HealthObservationKind.ALGAE -> AlgaeControlFragmentArgs(data.tankId).toBundle()
            HealthObservationKind.PLANT -> PlantHealthFragmentArgs(data.tankId).toBundle()
            HealthObservationKind.LIVESTOCK -> LivestockHealthFragmentArgs(data.tankId).toBundle()
        }
        val graph = host.navController.navInflater.inflate(R.navigation.nav_aquarium)
        graph.setStartDestination(route)
        host.navController.setGraph(graph, arguments)
        val list = current(kind)
        awaitContent {
            (list.history.value as? HealthLoadState.Content)?.value?.records?.any {
                it.id == data.records.getValue(kind)
            } == true
        }
        capture("${kind.name.lowercase()}-observations")
        val formDirection = form(kind)
        check(host.navController.navigateSafelyFrom(route, formDirection))
        check(!host.navController.navigateSafelyFrom(route, formDirection))
        val form = current(kind)
        awaitContent {
            form.preparation.value is HealthLoadState.Content && root().findViewById<View>(R.id.save).isEnabled
        }
        capture("${kind.name.lowercase()}-observation-form")
        check(form !== list && host.childFragmentManager.primaryNavigationFragment is HealthObservationFormFragment)
        check(host.navController.popBackStack())
        check(current(kind) === list)
        check(host.navController.navigateSafelyFrom(route, detail(kind)))
        val detail = current(kind)
        awaitContent { (detail.record.value as? HealthLoadState.Content)?.value?.id == data.records.getValue(kind) }
        check(root().findViewById<TextView>(R.id.notes).text.isNotBlank())
        check(root().findViewById<View>(R.id.follow).isEnabled && root().findViewById<View>(R.id.delete).isEnabled)
        capture("${kind.name.lowercase()}-observation-detail")
        check(detail !== form && detail !== list && detail.observationId == data.records.getValue(kind))
        check(host.childFragmentManager.primaryNavigationFragment is HealthObservationDetailFragment)
        verifyFollowUp(kind, detail)
        check(host.navController.popBackStack())
        check(current(kind) === list)
    }

    private suspend fun verifyFollowUp(kind: HealthObservationKind, detail: HealthObservationViewModel) {
        check(root().findViewById<View>(R.id.follow).performClick())
        val followUp = current(kind)
        check(followUp !== detail && followUp.previousId == detail.observationId)
        awaitContent {
            followUp.previous.value is HealthLoadState.Content && root().findViewById<View>(R.id.save).isEnabled
        }
        val parent = checkNotNull((followUp.previous.value as? HealthLoadState.Content)?.value)
        check(followUp.draft.getLong("subject") == (parent.input.observation.subjectId ?: 0L))
        check(!root().findViewById<View>(R.id.subject).isEnabled)
        check(root().findViewById<TextView>(R.id.parent).text.isNotBlank())
        check(host.navController.popBackStack())
        check(current(kind) === detail)
    }

    private fun form(kind: HealthObservationKind): NavDirections = when (kind) {
        HealthObservationKind.ALGAE -> AlgaeControlFragmentDirections
            .actionAlgaeControlFragmentToHealthObservationFormFragment(data.tankId, kind.name)
        HealthObservationKind.PLANT -> PlantHealthFragmentDirections
            .actionPlantHealthFragmentToHealthObservationFormFragment(data.tankId, kind.name)
        HealthObservationKind.LIVESTOCK -> LivestockHealthFragmentDirections
            .actionLivestockHealthFragmentToHealthObservationFormFragment(data.tankId, kind.name)
    }

    private fun detail(kind: HealthObservationKind): NavDirections = when (kind) {
        HealthObservationKind.ALGAE -> AlgaeControlFragmentDirections
            .actionAlgaeControlFragmentToHealthObservationDetailFragment(
                data.tankId, kind.name, data.records.getValue(kind))
        HealthObservationKind.PLANT -> PlantHealthFragmentDirections
            .actionPlantHealthFragmentToHealthObservationDetailFragment(
                data.tankId, kind.name, data.records.getValue(kind))
        HealthObservationKind.LIVESTOCK -> LivestockHealthFragmentDirections
            .actionLivestockHealthFragmentToHealthObservationDetailFragment(
                data.tankId, kind.name, data.records.getValue(kind))
    }

    private suspend fun current(kind: HealthObservationKind): HealthObservationViewModel {
        host.childFragmentManager.executePendingTransactions()
        delay(SETTLE_MILLIS)
        val fragment = host.childFragmentManager.primaryNavigationFragment
            ?: error("Health observation route did not create a primary navigation fragment")
        check(fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && fragment.view != null)
        return ViewModelProvider(fragment)[HealthObservationViewModel::class.java].also {
            check(it.tankId == data.tankId && it.kind == kind)
        }
    }

    private fun root(): View {
        val fragment = host.childFragmentManager.primaryNavigationFragment
            ?: error("Health observation route has no active fragment")
        return fragment.requireView()
    }

    private suspend fun awaitContent(ready: () -> Boolean) = withTimeout(CONTENT_TIMEOUT_MILLIS) {
        while (!ready()) delay(CONTENT_POLL_MILLIS)
        delay(SETTLE_MILLIS)
    }

    private companion object {
        const val SETTLE_MILLIS = 300L
        const val CONTENT_TIMEOUT_MILLIS = 10_000L
        const val CONTENT_POLL_MILLIS = 50L
    }
}
