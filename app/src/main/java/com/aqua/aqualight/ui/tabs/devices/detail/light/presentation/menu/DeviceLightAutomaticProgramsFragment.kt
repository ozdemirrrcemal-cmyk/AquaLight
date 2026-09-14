package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
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
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsActions
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsScreen
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsViewModel

class DeviceLightAutomaticProgramsFragment :
    Fragment(R.layout.fragment_device_light_automatic_programs) {

    private val args: DeviceLightAutomaticProgramsFragmentArgs by navArgs()
    private val viewModel: DeviceLightAutomaticProgramsViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentDeviceLightAutomaticProgramsBinding? = null
    private val binding get() = _binding!!
    private var editorMode: Boolean = false
    private var editorBackCallback: OnBackPressedCallback? = null
    private val programSheets by lazy(LazyThreadSafetyMode.NONE) {
        DeviceLightAutomaticProgramSheetCoordinator(
            fragment = this,
            onDuplicate = ::openEditor,
            onDelete = viewModel::delete
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceLightAutomaticProgramsBinding.bind(view)
        editorMode = savedInstanceState?.getBoolean(STATE_EDITOR_MODE) == true
        setupEditorBackHandling()
        programSheets.register(viewLifecycleOwner)
        if (editorMode) {
            setupEditorPlaceholder()
        } else {
            setupContent()
        }
        viewModel.bind(args.deviceUid)
        renderState(viewModel.uiState.value)
        viewLifecycleOwner.observeAutomaticPrograms(
            viewModel = viewModel,
            onState = ::renderState,
            onEffect = { effect ->
                if (_binding != null && !editorMode) {
                    renderAutomaticProgramsEffect(effect)
                }
            }
        )
    }

    private fun setupEditorBackHandling() {
        editorBackCallback = object : OnBackPressedCallback(editorMode) {
            override fun handleOnBackPressed() = closeEditor()
        }.also { callback ->
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
        }
    }

    private fun setupEditorPlaceholder() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_light_auto_editor_title),
                onBackClick = ::closeEditor
            )
        )
        binding.automaticProgramsCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { }
        }
        setFragmentGlobalLoading(false)
    }

    private fun setupContent() {
        val actions = DeviceLightAutomaticProgramsActions(
            onProgramClick = { openEditor() },
            onEnabledChanged = viewModel::setEnabled,
            onMoreClick = programSheets::showActions,
            onAddClick = ::openEditor
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
        if (_binding == null || editorMode) return
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

    private fun openEditor() {
        if (editorMode) return
        editorMode = true
        editorBackCallback?.isEnabled = true
        setupEditorPlaceholder()
    }

    private fun closeEditor() {
        if (!editorMode) return
        editorMode = false
        editorBackCallback?.isEnabled = false
        setupContent()
        renderState(viewModel.uiState.value)
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && !editorMode) viewModel.refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_EDITOR_MODE, editorMode)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        editorBackCallback = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val STATE_EDITOR_MODE = "device_light_auto_editor_mode"
    }
}
