package com.aqua.aqualight.composition

import androidx.lifecycle.ViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.TankDetailViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir.DeviceDosingReservoirViewModel

/**
 * ViewModels that do not require authenticated-owner state.
 *
 * Dosing child editors live here only while they own local drafts. Editors backed by an
 * authenticated application boundary belong to OwnerViewModelFactory.
 */
internal class ProcessViewModelFactory : ScopedViewModelFactory {

    override fun supports(modelClass: Class<out ViewModel>): Boolean =
        modelClass == TankDetailViewModel::class.java ||
            modelClass == DeviceDosingReservoirViewModel::class.java

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when (modelClass) {
            TankDetailViewModel::class.java -> TankDetailViewModel()
            DeviceDosingReservoirViewModel::class.java -> DeviceDosingReservoirViewModel()
            else -> throw IllegalArgumentException(
                "No process-scoped ViewModel binding for ${modelClass.name}."
            )
        }

        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }
}
