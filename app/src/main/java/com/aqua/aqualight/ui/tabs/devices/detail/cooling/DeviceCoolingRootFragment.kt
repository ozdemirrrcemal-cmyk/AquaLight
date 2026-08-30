package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceCoolingRootBinding

class DeviceCoolingRootFragment : Fragment(R.layout.fragment_device_cooling_root) {

    private val args: DeviceCoolingRootFragmentArgs by navArgs()
    private val viewModel: DeviceCoolingRootViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceCoolingRootBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceCoolingRootBinding.bind(view)
        viewModel.bind(args.deviceUid)
        binding.coolingContent.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceCoolingCatalogScreen(
                    state = state,
                    onBackClick = { findNavController().navigateUp() },
                    onSettingsClick = ::openSettings
                )
            }
        }
    }

    private fun openSettings() {
        if (!viewModel.uiState.value.contentEnabled) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceCoolingRootFragment) return
        navController.navigate(
            DeviceCoolingRootFragmentDirections
                .actionDeviceCoolingRootFragmentToDeviceCoolingSettingsFragment(
                    deviceUid = args.deviceUid
                )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
