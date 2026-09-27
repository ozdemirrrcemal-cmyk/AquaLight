package com.aqua.aqualight.smoke

import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.NavHostFragment
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.aquarium.detail.TankDetailTankFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthAnalysisAddFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthAnalysisDetailFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthAnalysisHistoryFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.WaterAnalysisViewModel
import kotlinx.coroutines.delay

/** Exercises the actual Fragment delegates and route extras in the minified APK. */
internal class WaterAnalysisNavigationSmoke(private val host: NavHostFragment) {
    private val screens = listOf(
        R.id.tankDetailFragment to TankDetailTankFragment::class.java,
        R.id.tankHealthFragment to TankHealthFragment::class.java,
        R.id.tankHealthAnalysisAddFragment to TankHealthAnalysisAddFragment::class.java,
        R.id.tankHealthAnalysisHistoryFragment to TankHealthAnalysisHistoryFragment::class.java,
        R.id.tankHealthAnalysisDetailFragment to TankHealthAnalysisDetailFragment::class.java
    )

    suspend fun verify() {
        installGraph()
        val models = mutableListOf<WaterAnalysisViewModel>()
        screens.forEachIndexed { index, (destination, _) ->
            if (index > 0) host.navController.navigate(destination, routeArguments(destination))
            val model = requireScreen(index)
            check(models.none { it === model }) { "Water routes shared a ViewModel" }
            models += model
        }
        for (index in screens.lastIndex - 1 downTo 0) {
            check(host.navController.popBackStack())
            check(requireScreen(index) === models[index]) { "Back lost the route ViewModel" }
        }
    }

    private fun installGraph() {
        val provider = host.navController.navigatorProvider
        val fragments = provider.getNavigator(FragmentNavigator::class.java)
        val graph = NavGraph(provider.getNavigator(NavGraphNavigator::class.java)).apply {
            id = WATER_GRAPH_ID
            screens.forEach { (destination, fragmentClass) ->
                addDestination(fragments.createDestination().apply {
                    id = destination
                    setClassName(fragmentClass.name)
                })
            }
            setStartDestination(screens.first().first)
        }
        host.navController.setGraph(graph, routeArguments(screens.first().first))
    }

    private suspend fun requireScreen(index: Int): WaterAnalysisViewModel {
        host.childFragmentManager.executePendingTransactions()
        delay(SCREEN_SETTLE_MILLIS)
        val fragment = checkNotNull(host.childFragmentManager.primaryNavigationFragment)
        check(screens[index].second.isInstance(fragment))
        check(fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        check(fragment.view != null)
        // Every screen has already accessed its production delegate during onCreate/onViewCreated.
        // Provider retrieval here also verifies its instance survived a back-stack view recreation.
        return ViewModelProvider(fragment)[WaterAnalysisViewModel::class.java].also { model ->
            model.analysesStateForTank(TANK_ID)
            if (index == screens.lastIndex) model.analysisState(TANK_ID, ANALYSIS_ID)
        }
    }

    private fun routeArguments(destination: Int) = bundleOf("tankId" to TANK_ID).apply {
        // Only the detail screen is an event route; add/history remain tank routes.
        if (destination == R.id.tankHealthAnalysisDetailFragment) {
            putLong("analysisId", ANALYSIS_ID)
        }
    }

    private companion object {
        const val WATER_GRAPH_ID = 0x5A030201
        const val TANK_ID = 901L
        const val ANALYSIS_ID = 902L
        const val SCREEN_SETTLE_MILLIS = 300L
    }
}
