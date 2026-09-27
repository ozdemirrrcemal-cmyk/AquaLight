package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import kotlinx.coroutines.launch

/** A recreated view consumes a committed outcome once, only while its own route is visible. */
internal fun Fragment.bindWaterAnalysisMutation(
    mutations: WaterAnalysisMutationController,
    sourceDestinationId: Int,
    render: (WaterAnalysisMutationState) -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            mutations.state.collect { state ->
                render(state)
                when (state) {
                    is WaterAnalysisMutationState.Saved, WaterAnalysisMutationState.Deleted -> {
                        if (findNavController().currentDestination?.id == sourceDestinationId) {
                            findNavController().navigateUp()
                            mutations.consume(state)
                        }
                    }
                    is WaterAnalysisMutationState.Failed -> {
                        (activity as? BaseActivity)?.showSnackBar(
                            message = getString(if (state.deleting) R.string.tank_health_analysis_delete_failed
                                else R.string.tank_health_analysis_save_failed),
                            type = BaseActivity.SnackType.ERROR
                        )
                        mutations.consume(state)
                    }
                    else -> Unit
                }
            }
        }
    }
}
