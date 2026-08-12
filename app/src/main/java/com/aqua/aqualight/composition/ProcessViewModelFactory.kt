package com.aqua.aqualight.composition

import androidx.lifecycle.ViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.TankDetailViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail.DeviceDosingChannelDetailViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DeviceDosingPlanViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir.DeviceDosingReservoirViewModel

/**
 * ViewModels that do not require authenticated-owner state.
 *
 * Dosing child editors live here while they own local drafts only. When their firmware
 * application operations are connected, the binding can move to OwnerViewModelFactory
 * without changing Fragment construction.
 */
internal class ProcessViewModelFactory : ScopedViewModelFactory {

    override fun supports(modelClass: Class<out ViewModel>): Boolean =
        modelClass in PROCESS_BINDINGS

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when (modelClass) {
            TankDetailViewModel::class.java -> TankDetailViewModel()
            DeviceDosingChannelDetailViewModel::class.java -> DeviceDosingChannelDetailViewModel()
            DeviceDosingPlanViewModel::class.java -> DeviceDosingPlanViewModel()
            DeviceDosingReservoirViewModel::class.java -> DeviceDosingReservoirViewModel()
            else -> throw IllegalArgumentException(
                "No process-scoped ViewModel binding for ${modelClass.name}."
            )
        }

        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }

    private companion object {
        val PROCESS_BINDINGS: Set<Class<out ViewModel>> = setOf(
            TankDetailViewModel::class.java,
            DeviceDosingChannelDetailViewModel::class.java,
            DeviceDosingPlanViewModel::class.java,
            DeviceDosingReservoirViewModel::class.java
        )
    }
}
