package com.aqua.aqualight.composition

import androidx.lifecycle.ViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.TankDetailViewModel

/** Exact ViewModel bindings that do not require authenticated-owner state. */
internal class ProcessViewModelFactory : ScopedViewModelFactory {

    override fun supports(modelClass: Class<out ViewModel>): Boolean =
        modelClass == TankDetailViewModel::class.java

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when (modelClass) {
            TankDetailViewModel::class.java -> TankDetailViewModel()
            else -> throw IllegalArgumentException(
                "No process-scoped ViewModel binding for ${modelClass.name}."
            )
        }

        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }
}
