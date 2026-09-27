package com.aqua.aqualight.smoke

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDirections
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.NavHostFragment
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.aquarium.detail.TankDetailTankFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthAnalysisAddFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthAnalysisAddFragmentDirections
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthAnalysisDetailFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthAnalysisHistoryFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthAnalysisHistoryFragmentDirections
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthFragmentArgs
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthFragmentDirections
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.WaterAnalysisViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import kotlinx.coroutines.delay

/** Exercises the actual Fragment delegates and route extras in the minified APK. */
internal class WaterAnalysisNavigationSmoke(private val host: NavHostFragment) {
    private val screens = listOf(
        ENTRY_CHILD_DESTINATION to TankDetailTankFragment::class.java,
        R.id.tankHealthFragment to TankHealthFragment::class.java,
        R.id.tankHealthAnalysisAddFragment to TankHealthAnalysisAddFragment::class.java,
        R.id.tankHealthAnalysisHistoryFragment to TankHealthAnalysisHistoryFragment::class.java,
        R.id.tankHealthAnalysisDetailFragment to TankHealthAnalysisDetailFragment::class.java
    )

    suspend fun verify() {
        installGraph()
        val models = mutableListOf<WaterAnalysisViewModel>()
        screens.indices.forEach { index ->
            if (index > 0) openScreen(index)
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
        val graph = host.navController.navInflater.inflate(R.navigation.nav_aquarium).apply {
            // The pager child is not a production graph destination. Mount it only in this
            // test host; every production destination and action below comes from the real XML.
            addDestination(fragments.createDestination().apply {
                id = ENTRY_CHILD_DESTINATION
                setClassName(TankDetailTankFragment::class.java.name)
            })
            setStartDestination(ENTRY_CHILD_DESTINATION)
        }
        host.navController.setGraph(graph, TankDetailTankFragment.newInstance(TANK_ID).arguments)
    }

    private fun openScreen(index: Int) {
        if (index == 1) {
            host.navController.navigate(R.id.tankHealthFragment, TankHealthFragmentArgs(TANK_ID).toBundle())
            return
        }
        val directions: NavDirections = when (screens[index].first) {
            R.id.tankHealthAnalysisAddFragment ->
                TankHealthFragmentDirections.actionTankHealthFragmentToTankHealthAnalysisAddFragment(TANK_ID)
            R.id.tankHealthAnalysisHistoryFragment ->
                TankHealthAnalysisAddFragmentDirections
                    .actionTankHealthAnalysisAddFragmentToTankHealthAnalysisHistoryFragment(TANK_ID)
            else -> TankHealthAnalysisHistoryFragmentDirections
                .actionTankHealthAnalysisHistoryFragmentToTankHealthAnalysisDetailFragment(TANK_ID, ANALYSIS_ID)
        }
        val source = screens[index - 1].first
        check(host.navController.navigateSafelyFrom(source, directions))
        check(!host.navController.navigateSafelyFrom(source, directions)) { "Repeated stale navigation was accepted" }
    }

    private suspend fun requireScreen(index: Int): WaterAnalysisViewModel {
        host.childFragmentManager.executePendingTransactions()
        delay(SCREEN_SETTLE_MILLIS)
        val fragment = host.childFragmentManager.primaryNavigationFragment
            ?: error("Water navigation has no active fragment")
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

    private companion object {
        const val ENTRY_CHILD_DESTINATION = 0x5A030201
        const val TANK_ID = 901L
        const val ANALYSIS_ID = 902L
        const val SCREEN_SETTLE_MILLIS = 300L
    }
}
