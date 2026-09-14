package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
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
import com.aqua.aqualight.databinding.FragmentDeviceLightAutomaticProgramsBinding
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetAction
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetActionStyle
import com.aqua.aqualight.ui.common.bottomsheet.GlobalActionBottomSheet
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsActions
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsEffect
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsScreen
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsViewModel
import kotlinx.coroutines.launch

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceLightAutomaticProgramsBinding.bind(view)
        editorMode = savedInstanceState?.getBoolean(STATE_EDITOR_MODE) == true
        setupEditorBackHandling()
        registerSheetResults()
        if (editorMode) {
            setupEditorPlaceholder()
        } else {
            setupContent()
        }
        viewModel.bind(args.deviceUid)
        renderState(viewModel.uiState.value)
        observeViewModel()
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
            onMoreClick = ::showActions,
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

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderState) }
                launch { viewModel.effects.collect(::handleEffect) }
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

    private fun handleEffect(effect: DeviceLightAutomaticProgramsEffect) {
        if (_binding == null || editorMode) return
        when (effect) {
            is DeviceLightAutomaticProgramsEffect.ShowMessage -> {
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

    private fun showActions(programId: String) {
        GlobalActionBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_light_auto_actions_title),
            actions = listOf(
                BottomSheetAction(
                    id = ACTION_DUPLICATE,
                    text = getString(R.string.device_light_auto_duplicate),
                    style = BottomSheetActionStyle.NEUTRAL
                ),
                BottomSheetAction(
                    id = ACTION_DELETE,
                    text = getString(R.string.device_light_auto_delete),
                    style = BottomSheetActionStyle.DANGER
                )
            ),
            requestKey = ACTIONS_REQUEST_KEY,
            payloadId = programId
        )
    }

    private fun showDeleteConfirmation(programId: String) {
        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_light_auto_delete_title),
            message = getString(R.string.device_light_auto_delete_message),
            primaryText = getString(R.string.device_light_auto_delete_confirm),
            cancelText = getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.DANGER,
            requestKey = DELETE_REQUEST_KEY,
            actionId = programId
        )
    }

    private fun registerSheetResults() {
        childFragmentManager.setFragmentResultListener(
            ACTIONS_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(GlobalActionBottomSheet.RESULT_KEY) !=
                GlobalActionBottomSheet.RESULT_ACTION
            ) {
                return@setFragmentResultListener
            }
            val programId = result.getString(GlobalActionBottomSheet.RESULT_PAYLOAD_ID).orEmpty()
            when (result.getString(GlobalActionBottomSheet.RESULT_ACTION_ID)) {
                ACTION_DUPLICATE -> openEditor()
                ACTION_DELETE -> showDeleteConfirmation(programId)
            }
        }
        childFragmentManager.setFragmentResultListener(
            DELETE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(FeedbackBottomSheet.RESULT_KEY) ==
                FeedbackBottomSheet.RESULT_PRIMARY
            ) {
                viewModel.delete(result.getString(FeedbackBottomSheet.RESULT_ACTION_ID).orEmpty())
            }
        }
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
        const val ACTIONS_REQUEST_KEY = "device_light_auto_actions"
        const val DELETE_REQUEST_KEY = "device_light_auto_delete"
        const val ACTION_DUPLICATE = "duplicate"
        const val ACTION_DELETE = "delete"
        const val STATE_EDITOR_MODE = "device_light_auto_editor_mode"
    }
}
