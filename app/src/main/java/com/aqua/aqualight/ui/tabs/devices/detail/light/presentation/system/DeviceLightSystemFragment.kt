package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
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
import com.aqua.aqualight.databinding.FragmentDeviceLightSystemBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import kotlinx.coroutines.launch

class DeviceLightSystemFragment : Fragment(R.layout.fragment_device_light_system) {
    private val args: DeviceLightSystemFragmentArgs by navArgs()
    private val viewModel: DeviceLightSystemViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var binding: FragmentDeviceLightSystemBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentDeviceLightSystemBinding.bind(view)
        compactHeaderForApprovedLayout()
        setupContent()
        viewModel.bind(args.deviceUid)
        renderState(viewModel.uiState.value)
        observeViewModel()
    }

    private fun compactHeaderForApprovedLayout() {
        val header = binding?.appHeader?.root ?: return
        ViewCompat.setPaddingRelative(
            header,
            ViewCompat.getPaddingStart(header),
            resources.getDimensionPixelSize(R.dimen.aqua_size_4),
            ViewCompat.getPaddingEnd(header),
            resources.getDimensionPixelSize(R.dimen.aqua_size_6)
        )
    }

    private fun setupContent() {
        val actions = DeviceLightSystemActions(
            onModeChanged = viewModel::updateMode,
            onStartTemperatureChanged = viewModel::updateStartTemperature,
            onFullSpeedTemperatureChanged = viewModel::updateFullSpeedTemperature,
            onProtectionThresholdChanged = viewModel::updateProtectionThreshold,
            onSaveClick = viewModel::save
        )
        binding?.systemCompose?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceLightSystemScreen(state, actions)
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

    private fun renderState(state: DeviceLightSystemUiState) {
        val currentBinding = binding ?: return
        currentBinding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_light_system_title),
                onBackClick = { findNavController().navigateUp() },
                statusIcon = state.connectionVisualState?.toWifiHeaderStatusIcon(requireContext())
            )
        )
        setFragmentGlobalLoading(state.initialLoading)
    }

    private fun renderEffect(effect: DeviceLightSystemEffect) {
        if (binding == null) return
        when (effect) {
            DeviceLightSystemEffect.CloseUnavailable -> {
                val controller = findNavController()
                if (controller.currentDestination?.id == R.id.deviceLightSystemFragment) {
                    controller.navigateUp()
                }
            }
            is DeviceLightSystemEffect.ShowMessage -> {
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
        if (binding != null) viewModel.refresh()
    }

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        binding = null
        super.onDestroyView()
    }
}
