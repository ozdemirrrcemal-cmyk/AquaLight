package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

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
import com.aqua.aqualight.databinding.FragmentDeviceLightQuickSetupBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class DeviceLightQuickSetupFragment : Fragment(R.layout.fragment_device_light_quick_setup) {

    private val args: DeviceLightQuickSetupFragmentArgs by navArgs()
    private val viewModel: DeviceLightQuickSetupViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceLightQuickSetupBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        require(args.deviceUid.isNotBlank())
        _binding = FragmentDeviceLightQuickSetupBinding.bind(view)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_menu_quick_setup_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )

        binding.quickSetupCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceLightQuickSetupScreen(
                    state = state,
                    onAction = viewModel::dispatch,
                    onDone = ::finishQuickSetup
                )
            }
        }

        viewModel.bind(args.deviceUid)
    }

    private fun finishQuickSetup() {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.deviceLightQuickSetupFragment) {
            navController.navigateUp()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
