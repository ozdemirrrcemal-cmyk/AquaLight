package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

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
import com.aqua.aqualight.databinding.FragmentDeviceLightManualControlBinding
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryNamePolicy
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import kotlinx.coroutines.launch

class DeviceLightManualControlFragment : Fragment(R.layout.fragment_device_light_manual_control) {

    private val args: DeviceLightManualControlFragmentArgs by navArgs()
    private val viewModel: DeviceLightManualControlViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceLightManualControlBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceLightManualControlBinding.bind(view)

        registerSaveAsResult()
        viewModel.bind(args.deviceUid)
        renderState(viewModel.uiState.value)
        setupManualContent()
        observeViewModel()
    }

    private fun setupManualContent() {
        val actions = DeviceLightManualControlActions(
            channels = DeviceLightManualChannelActions(
                onChannelValueChanged = viewModel::updateChannel,
                onChannelValueChangeFinished = { viewModel.commitScene() },
                onChannelStep = viewModel::stepChannel
            ),
            onPresetClick = viewModel::applyPreset,
            onLoadClick = viewModel.libraryActions::requestLoad,
            onSaveAsClick = viewModel.libraryActions::requestSaveAs,
            onPowerOffClick = viewModel::turnOff
        )
        binding.manualControlCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceLightManualControlScreen(state = state, actions = actions)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect(::renderState)
                }
                launch {
                    viewModel.effects.collect(::handleEffect)
                }
            }
        }
    }

    private fun renderState(state: DeviceLightManualControlUiState) {
        if (_binding == null) return
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_menu_manual_control_title),
                onBackClick = { findNavController().navigateUp() },
                statusIcon = state.connectionVisualState?.toWifiHeaderStatusIcon(requireContext())
            )
        )
        setFragmentGlobalLoading(state.showGlobalLoading)
    }

    private fun handleEffect(effect: DeviceLightManualControlEffect) {
        if (_binding == null) return
        when (effect) {
            DeviceLightManualControlEffect.OpenLibrary -> openLibrary()
            is DeviceLightManualControlEffect.OpenSaveAs -> showSaveAsSheet(
                effect.usedManualNames
            )
            is DeviceLightManualControlEffect.ShowSuccess -> {
                (activity as? BaseActivity)?.showSnackBar(
                    message = getString(effect.messageRes),
                    type = BaseActivity.SnackType.SUCCESS
                )
            }
            is DeviceLightManualControlEffect.ShowError -> {
                setFragmentGlobalLoading(false)
                (activity as? BaseActivity)?.showSnackBar(
                    message = getString(effect.messageRes),
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    private fun registerSaveAsResult() {
        childFragmentManager.setFragmentResultListener(
            SAVE_AS_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(TextInputBottomSheet.RESULT_KEY) ==
                TextInputBottomSheet.RESULT_SAVED
            ) {
                viewModel.libraryActions.saveAs(
                    result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty()
                )
            }
        }
    }

    private fun showSaveAsSheet(usedManualNames: List<String>) {
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_light_library_save_as_title),
            label = getString(R.string.device_light_library_name_label),
            hint = getString(R.string.device_light_library_name_hint),
            initialValue = "",
            supportingText = getString(R.string.device_light_library_name_supporting_text),
            saveText = getString(R.string.device_light_library_save),
            cancelText = getString(R.string.cancel),
            required = true,
            requiredMessage = getString(R.string.device_light_library_name_required_error),
            requestKey = SAVE_AS_REQUEST_KEY,
            maxLength = DeviceLightLibraryNamePolicy.MAX_LENGTH,
            requestFocus = true,
            disallowedValues = usedManualNames,
            disallowedMessage = getString(R.string.device_light_library_name_duplicate_error)
        )
    }

    private fun openLibrary() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceLightManualControlFragment) return
        navController.navigate(
            DeviceLightManualControlFragmentDirections
                .actionDeviceLightManualControlFragmentToDeviceLightLibraryFragment(
                    deviceUid = args.deviceUid
                )
        )
    }

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val SAVE_AS_REQUEST_KEY = "device_light_manual_save_as"
    }
}
