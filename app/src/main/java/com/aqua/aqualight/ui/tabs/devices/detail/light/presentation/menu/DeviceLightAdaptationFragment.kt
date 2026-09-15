package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

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
import com.aqua.aqualight.databinding.FragmentDeviceLightAdaptationBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation.DeviceLightAdaptationActions
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation.DeviceLightAdaptationEffect
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation.DeviceLightAdaptationScreen
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation.DeviceLightAdaptationUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation.DeviceLightAdaptationViewModel
import kotlinx.coroutines.launch

class DeviceLightAdaptationFragment : Fragment(R.layout.fragment_device_light_adaptation) {
    private val args: DeviceLightAdaptationFragmentArgs by navArgs()
    private val viewModel: DeviceLightAdaptationViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentDeviceLightAdaptationBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceLightAdaptationBinding.bind(view)
        setupContent()
        viewModel.bind(args.deviceUid)
        renderState(viewModel.uiState.value)
        observeViewModel()
    }

    private fun setupContent() {
        val actions = DeviceLightAdaptationActions(
            onStartPercentChanged = viewModel::updateStartPercent,
            onDurationDaysChanged = viewModel::updateDurationDays,
            onStartClick = viewModel::start,
            onStopClick = viewModel::stop,
            onConfigureAgainClick = viewModel::configureAgain
        )
        binding.adaptationCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceLightAdaptationScreen(state, actions)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderState) }
                launch { viewModel.effects.collect(::renderEffect) }
            }
        }
    }

    private fun renderState(state: DeviceLightAdaptationUiState) {
        if (_binding == null) return
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_light_adaptation_title),
                onBackClick = { findNavController().navigateUp() },
                statusIcon = state.connectionVisualState?.toWifiHeaderStatusIcon(requireContext())
            )
        )
        setFragmentGlobalLoading(state.initialLoading)
    }

    private fun renderEffect(effect: DeviceLightAdaptationEffect) {
        if (_binding == null) return
        when (effect) {
            DeviceLightAdaptationEffect.CloseUnavailable -> {
                val controller = findNavController()
                if (controller.currentDestination?.id == R.id.deviceLightAdaptationFragment) {
                    controller.navigateUp()
                }
            }
            is DeviceLightAdaptationEffect.ShowMessage -> {
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

    override fun onResume() {
        super.onResume()
        if (_binding != null) viewModel.refresh()
    }

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }
}
