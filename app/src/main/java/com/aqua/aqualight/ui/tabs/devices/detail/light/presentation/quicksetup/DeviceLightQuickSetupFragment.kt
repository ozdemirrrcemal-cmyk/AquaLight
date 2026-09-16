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
        _binding = FragmentDeviceLightQuickSetupBinding.bind(view)
        setupContent()
        viewModel.bind(args.deviceUid)
        renderState(viewModel.uiState.value)
        observeViewModel()
    }

    private fun setupContent() {
        val actions = DeviceLightQuickSetupActions(
            onAmbientLightSelected = viewModel::selectAmbientLight,
            onAlgaeLevelSelected = viewModel::selectAlgaeLevel,
            onToggleDetails = viewModel::toggleDetails,
            onEditInstalledPlan = viewModel::editInstalledPlan,
            onCancelEdit = viewModel::cancelEdit,
            onApply = viewModel::apply
        )
        binding.quickSetupCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceLightQuickSetupScreen(state, actions)
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

    private fun renderState(state: DeviceLightQuickSetupUiState) {
        if (_binding == null) return
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_menu_quick_setup_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )
        setFragmentGlobalLoading(state.initialLoading || state.applying)
    }

    private fun renderEffect(effect: DeviceLightQuickSetupEffect) {
        if (_binding == null) return
        when (effect) {
            DeviceLightQuickSetupEffect.Applied -> {
                showMessage(R.string.device_light_quick_setup_applied, true)
            }
            is DeviceLightQuickSetupEffect.CloseUnavailable -> {
                showMessage(effect.messageRes, false)
                findNavController().navigateUp()
            }
            is DeviceLightQuickSetupEffect.ShowMessage -> showMessage(effect.messageRes, false)
        }
    }

    private fun showMessage(messageRes: Int, success: Boolean) {
        (activity as? BaseActivity)?.showSnackBar(
            message = getString(messageRes),
            type = if (success) BaseActivity.SnackType.SUCCESS else BaseActivity.SnackType.ERROR
        )
    }

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }
}
