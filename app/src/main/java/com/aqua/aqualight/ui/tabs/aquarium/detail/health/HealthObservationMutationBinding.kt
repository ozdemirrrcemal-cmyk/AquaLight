package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import kotlinx.coroutines.launch

internal fun Fragment.bindHealthMutation(model: HealthObservationViewModel, route: Int,
    saved: suspend () -> Unit = {}, render: (HealthMutationState) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.mutations.state.collect { state ->
                render(state)
                when (state) {
                    is HealthMutationState.Saved, HealthMutationState.Deleted -> {
                        if (findNavController().currentDestination?.id == route) {
                            if (state is HealthMutationState.Saved) saved()
                            findNavController().navigateUp()
                            model.mutations.consume(state)
                        }
                    }
                    HealthMutationState.Failed -> {
                        (activity as? BaseActivity)?.showSnackBar(getString(R.string.health_write_failed),
                            BaseActivity.SnackType.ERROR)
                        model.mutations.consume(state)
                    }
                    else -> Unit
                }
            }
        }
    }
}
