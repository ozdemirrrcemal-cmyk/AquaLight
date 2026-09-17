package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.programs

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
import com.aqua.aqualight.databinding.FragmentDeviceLightAutomaticProgramsBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading

class DeviceLightAutomaticProgramsFragment :
    Fragment(R.layout.fragment_device_light_automatic_programs) {

    private val args: DeviceLightAutomaticProgramsFragmentArgs by navArgs()
    private val viewModel: DeviceLightAutomaticProgramsViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentDeviceLightAutomaticProgramsBinding? = null
    private val binding get() = _binding!!
    private val programSheets by lazy(LazyThreadSafetyMode.NONE) {
        DeviceLightAutomaticProgramSheetCoordinator(
            fragment = this,
            onDuplicate = { programId -> openEditor(programId, duplicate = true) },
            onDelete = viewModel::delete
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceLightAutomaticProgramsBinding.bind(view)
        programSheets.register(viewLifecycleOwner)
        setupContent()
        viewModel.bind(args.deviceUid)
        renderState(viewModel.uiState.value)
        viewLifecycleOwner.observeAutomaticPrograms(
            viewModel = viewModel,
            onState = ::renderState,
            onEffect = { effect ->
                if (_binding != null) {
                    renderAutomaticProgramsEffect(effect)
                }
            }
        )
    }

    private fun setupContent() {
        val actions = DeviceLightAutomaticProgramsActions(
            onProgramClick = { programId -> openEditor(programId, duplicate = false) },
            onEnabledChanged = viewModel::setEnabled,
            onMoreClick = programSheets::showActions,
            onAddClick = { openEditor(programId = "", duplicate = false) }
        )
        binding.automaticProgramsCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceLightAutomaticProgramsScreen(state = state, actions = actions)
            }
        }
    }

    private fun renderState(state: DeviceLightAutomaticProgramsUiState) {
        if (_binding == null) return
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_light_automatic_programs_title),
                onBackClick = { findNavController().navigateUp() },
                statusIcon = state.connectionVisualState?.toWifiHeaderStatusIcon(requireContext())
            )
        )
        setFragmentGlobalLoading(state.initialLoading)
    }

    private fun openEditor(programId: String, duplicate: Boolean) {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceLightAutomaticProgramsFragment) return
        navController.navigate(
            DeviceLightAutomaticProgramsFragmentDirections
                .actionDeviceLightAutomaticProgramsFragmentToDeviceLightAutomaticProgramEditorFragment(
                    deviceUid = args.deviceUid,
                    programId = programId,
                    duplicate = duplicate
                )
        )
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
