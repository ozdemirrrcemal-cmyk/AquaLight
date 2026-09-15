package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceLightQuickSetupBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import kotlinx.coroutines.launch

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
        viewModel.bind(args.deviceUid)
        setupQuickSetupContent()
        renderState(viewModel.uiState.value)
        observeViewModel()
    }

    private fun setupQuickSetupContent() {
        val actions = DeviceLightQuickSetupActions(
            onEditClick = viewModel::startEditing,
            onCancelEditClick = viewModel::cancelEditing,
            onAquariumAgeSelected = viewModel::selectAquariumAge,
            onAquariumAgeAdjusted = viewModel::adjustAquariumAge,
            onPlantDensitySelected = viewModel::selectPlantDensity,
            onPlantLightDemandSelected = viewModel::selectPlantLightDemand,
            onCo2StatusSelected = viewModel::selectCo2Status,
            onActiveSoilSelected = viewModel::selectActiveSoil,
            onWaterDepthSelected = viewModel::selectWaterDepth,
            onWaterDepthAdjusted = viewModel::adjustWaterDepth,
            onMountHeightSelected = viewModel::selectMountHeight,
            onMountHeightAdjusted = viewModel::adjustMountHeight,
            onViewingWindowSelected = viewModel::selectViewingWindow,
            onViewingStartAdjusted = viewModel::adjustViewingStart,
            onViewingEndAdjusted = viewModel::adjustViewingEnd,
            onAlgaeObservationSelected = viewModel::selectAlgaeObservation,
            onPlantStressObservationSelected = viewModel::selectPlantStressObservation,
            onRecordObservationsToday = viewModel::recordObservationsToday,
            onSaveProfileClick = viewModel::saveProfile,
            onApplyClick = viewModel::applyPlan,
            onRetryClick = viewModel::refresh
        )
        binding.quickSetupCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceLightQuickSetupScreen(state = state, actions = actions)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderState) }
                launch { viewModel.effects.collect(::handleEffect) }
            }
        }
    }

    private fun renderState(state: DeviceLightQuickSetupUiState) {
        if (_binding == null) return
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_menu_quick_setup_title),
                onBackClick = { findNavController().navigateUp() },
                statusIcon = state.connectionVisualState?.toWifiHeaderStatusIcon(requireContext())
            )
        )
        setFragmentGlobalLoading(state.initialLoading || state.operationInProgress)
    }

    private fun handleEffect(effect: DeviceLightQuickSetupEffect) {
        if (_binding == null) return
        when (effect) {
            is DeviceLightQuickSetupEffect.ShowMessage -> {
                if (!effect.success) setFragmentGlobalLoading(false)
                (activity as? BaseActivity)?.showSnackBar(
                    message = getString(effect.messageRes),
                    type = if (effect.success) {
                        BaseActivity.SnackType.SUCCESS
                    } else {
                        BaseActivity.SnackType.ERROR
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }
}
