package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

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
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryNamePolicy
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceLightLibraryBinding
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetAction
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetActionStyle
import com.aqua.aqualight.ui.common.bottomsheet.GlobalActionBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import kotlinx.coroutines.launch

class DeviceLightLibraryFragment : Fragment(R.layout.fragment_device_light_library) {

    private val args: DeviceLightLibraryFragmentArgs by navArgs()
    private val viewModel: DeviceLightLibraryViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentDeviceLightLibraryBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceLightLibraryBinding.bind(view)
        registerSheetResults()
        setupContent()
        viewModel.bind(args.deviceUid)
        viewModel.selectTab(
            if (args.initialTab == INITIAL_TAB_CUSTOM) {
                DeviceLightLibraryTab.CUSTOM
            } else {
                DeviceLightLibraryTab.MANUAL
            }
        )
        renderState(viewModel.uiState.value)
        observeViewModel()
    }

    private fun setupContent() {
        val actions = DeviceLightLibraryActions(
            onTabSelected = viewModel::selectTab,
            onLoadClick = viewModel::load,
            onMoreClick = viewModel::requestActions,
            onRetryClick = viewModel::retry
        )
        binding.lightLibraryCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceLightLibraryScreen(state = state, actions = actions)
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

    private fun renderState(state: DeviceLightLibraryUiState) {
        if (_binding == null) return
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_light_library_title),
                onBackClick = { findNavController().navigateUp() },
                statusIcon = state.target?.let {
                    DeviceConnectionVisualState.ONLINE.toWifiHeaderStatusIcon(requireContext())
                }
            )
        )
        setFragmentGlobalLoading(state.showGlobalLoading)
    }

    private fun handleEffect(effect: DeviceLightLibraryEffect) {
        if (_binding == null) return
        when (effect) {
            is DeviceLightLibraryEffect.OpenActions -> showActions(effect)
            is DeviceLightLibraryEffect.OpenRename -> showRename(effect)
            is DeviceLightLibraryEffect.OpenDeleteConfirmation -> showDeleteConfirmation(effect)
            is DeviceLightLibraryEffect.ShowMessage -> {
                setFragmentGlobalLoading(false)
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

    private fun showActions(effect: DeviceLightLibraryEffect.OpenActions) {
        GlobalActionBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_light_library_actions_title, effect.entryName),
            actions = listOf(
                BottomSheetAction(
                    id = ACTION_RENAME,
                    text = getString(R.string.device_light_library_rename),
                    style = BottomSheetActionStyle.NEUTRAL
                ),
                BottomSheetAction(
                    id = ACTION_DELETE,
                    text = getString(R.string.device_light_library_delete),
                    style = BottomSheetActionStyle.DANGER
                )
            ),
            requestKey = ACTIONS_REQUEST_KEY,
            payloadId = effect.entryId
        )
    }

    private fun showRename(effect: DeviceLightLibraryEffect.OpenRename) {
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_light_library_rename_title),
            label = getString(R.string.device_light_library_name_label),
            hint = getString(R.string.device_light_library_name_hint),
            initialValue = effect.currentName,
            supportingText = getString(R.string.device_light_library_name_supporting_text),
            saveText = getString(R.string.device_light_library_save),
            cancelText = getString(R.string.cancel),
            required = true,
            requiredMessage = getString(R.string.device_light_library_name_required_error),
            requestKey = RENAME_REQUEST_KEY,
            payloadId = effect.entryId,
            maxLength = DeviceLightLibraryNamePolicy.MAX_LENGTH,
            disableSaveWhenUnchanged = true,
            requestFocus = true,
            disallowedValues = effect.disallowedNames,
            disallowedMessage = getString(R.string.device_light_library_name_duplicate_error)
        )
    }

    private fun showDeleteConfirmation(
        effect: DeviceLightLibraryEffect.OpenDeleteConfirmation
    ) {
        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_light_library_delete_title),
            message = getString(R.string.device_light_library_delete_message, effect.entryName),
            primaryText = getString(R.string.device_light_library_delete_confirm),
            cancelText = getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.DANGER,
            requestKey = DELETE_REQUEST_KEY,
            actionId = effect.entryId
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
            val entryId = result.getString(GlobalActionBottomSheet.RESULT_PAYLOAD_ID).orEmpty()
            when (result.getString(GlobalActionBottomSheet.RESULT_ACTION_ID)) {
                ACTION_RENAME -> viewModel.requestRename(entryId)
                ACTION_DELETE -> viewModel.requestDelete(entryId)
            }
        }
        childFragmentManager.setFragmentResultListener(
            RENAME_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(TextInputBottomSheet.RESULT_KEY) ==
                TextInputBottomSheet.RESULT_SAVED
            ) {
                viewModel.rename(
                    entryId = result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID).orEmpty(),
                    name = result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty()
                )
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

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ACTIONS_REQUEST_KEY = "device_light_library_actions"
        const val RENAME_REQUEST_KEY = "device_light_library_rename"
        const val DELETE_REQUEST_KEY = "device_light_library_delete"
        const val ACTION_RENAME = "rename"
        const val ACTION_DELETE = "delete"
        const val INITIAL_TAB_CUSTOM = "CUSTOM"
    }
}
