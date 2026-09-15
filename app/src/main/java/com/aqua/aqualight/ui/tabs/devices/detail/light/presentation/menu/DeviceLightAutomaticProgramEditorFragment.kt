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
import com.aqua.aqualight.application.devices.light.preset.DeviceLightPresetId
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceLightAutomaticProgramEditorBinding
import com.aqua.aqualight.ui.common.dialog.UnsavedChangesExitGuard
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticDayActions
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticEditorDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticEditorMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramEditorActions
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramEditorScreen
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramEditorViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticPresetNavigation
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticScheduleActions
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticTimeField
import kotlinx.coroutines.launch

class DeviceLightAutomaticProgramEditorFragment :
    Fragment(R.layout.fragment_device_light_automatic_program_editor) {

    private val args: DeviceLightAutomaticProgramEditorFragmentArgs by navArgs()
    private val viewModel: DeviceLightAutomaticProgramEditorViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentDeviceLightAutomaticProgramEditorBinding? = null
    private val binding get() = _binding!!
    private lateinit var unsavedGuard: UnsavedChangesExitGuard
    private lateinit var effectHandler: DeviceLightAutomaticProgramEditorEffectHandler

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceLightAutomaticProgramEditorBinding.bind(view)
        viewModel.bind(
            deviceUidText = args.deviceUid,
            mode = resolveMode(),
            restoredDraft = savedInstanceState?.let(DeviceLightAutomaticEditorDraft::restore),
            restoredPresetId = DeviceLightPresetId.fromStorageName(
                savedInstanceState?.getString(STATE_SELECTED_PRESET_ID)
            )
        )
        findNavController().currentBackStackEntry?.savedStateHandle?.let { savedStateHandle ->
            savedStateHandle
                .getLiveData<String>(DeviceLightAutomaticPresetNavigation.RESULT_PRESET_ID)
                .observe(viewLifecycleOwner) { storedPresetId ->
                    savedStateHandle.remove<String>(
                        DeviceLightAutomaticPresetNavigation.RESULT_PRESET_ID
                    )
                    DeviceLightPresetId.fromStorageName(storedPresetId)
                        ?.let(viewModel::applyPreset)
                }
        }
        attachUnsavedGuard()
        effectHandler = DeviceLightAutomaticProgramEditorEffectHandler(this, viewModel)
        effectHandler.registerTimeResult()
        setupContent()
        renderState()
        observeViewModel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.currentState.draft.writeTo(outState)
        viewModel.currentState.selectedPresetId?.let { presetId ->
            outState.putString(STATE_SELECTED_PRESET_ID, presetId.name)
        }
        super.onSaveInstanceState(outState)
    }

    private fun resolveMode(): DeviceLightAutomaticEditorMode = when {
        args.programId.isBlank() -> DeviceLightAutomaticEditorMode.Create
        args.duplicate -> DeviceLightAutomaticEditorMode.Duplicate(args.programId)
        else -> DeviceLightAutomaticEditorMode.Edit(args.programId)
    }

    private fun attachUnsavedGuard() {
        unsavedGuard = UnsavedChangesExitGuard.attach(
            fragment = this,
            configuration = UnsavedChangesExitGuard.Configuration(
                requestKey = UNSAVED_REQUEST_KEY,
                actionId = ACTION_DISCARD_DRAFT,
                hasUnsavedChanges = { viewModel.currentState.hasUnsavedChanges },
                isExitBlocked = { viewModel.currentState.operationInProgress },
                exit = ::exitEditor
            )
        )
    }

    private fun setupContent() {
        val editor = viewModel.draftEditor
        val actions = DeviceLightAutomaticProgramEditorActions(
            days = DeviceLightAutomaticDayActions(
                onEveryDayClick = editor::selectEveryDay,
                onWeekdaysClick = editor::selectWeekdays,
                onWeekendClick = editor::selectWeekend,
                onCustomClick = editor::selectCustom,
                onDayClick = editor::toggleDay
            ),
            schedule = DeviceLightAutomaticScheduleActions(
                onStartTimeClick = { editor.requestTime(DeviceLightAutomaticTimeField.START) },
                onEndTimeClick = { editor.requestTime(DeviceLightAutomaticTimeField.END) },
                onTimeChanged = editor::updateTime,
                onRampClick = editor::selectRamp
            ),
            onChannelChanged = editor::updateChannel,
            onPresetClick = ::openPreset,
            onCancelClick = unsavedGuard::requestExit,
            onSaveClick = viewModel::save
        )
        binding.automaticProgramEditorCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceLightAutomaticProgramEditorScreen(state, actions)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { renderState() } }
                launch { viewModel.effects.collect(effectHandler::handle) }
            }
        }
    }

    private fun renderState() {
        if (_binding == null) return
        val state = viewModel.currentState
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(state.mode.titleRes()),
                onBackClick = unsavedGuard::requestExit,
                statusIcon = state.connectionVisualState?.toWifiHeaderStatusIcon(requireContext())
            )
        )
        setFragmentGlobalLoading(state.initialLoading || state.operationInProgress)
    }

    private fun openPreset() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceLightAutomaticProgramEditorFragment) {
            return
        }
        navController.navigate(
            DeviceLightAutomaticProgramEditorFragmentDirections
                .actionDeviceLightAutomaticProgramEditorFragmentToDeviceLightAutomaticPresetFragment(
                    deviceUid = args.deviceUid,
                    selectedPresetId = viewModel.currentState.selectedPresetId?.name.orEmpty()
                )
        )
    }

    private fun exitEditor() {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.deviceLightAutomaticProgramEditorFragment) {
            navController.navigateUp()
        }
    }

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val UNSAVED_REQUEST_KEY = "device_light_auto_editor_unsaved"
        const val ACTION_DISCARD_DRAFT = "discard_auto_program_draft"
        const val STATE_SELECTED_PRESET_ID = "device_light_auto_editor_selected_preset"
    }
}

private fun DeviceLightAutomaticEditorMode.titleRes(): Int = when (this) {
    DeviceLightAutomaticEditorMode.Create -> R.string.device_light_auto_editor_create_title
    is DeviceLightAutomaticEditorMode.Edit -> R.string.device_light_auto_editor_edit_title
    is DeviceLightAutomaticEditorMode.Duplicate -> R.string.device_light_auto_editor_duplicate_title
}
