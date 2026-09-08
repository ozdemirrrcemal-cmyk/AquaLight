package com.aqua.aqualight.ui.tabs.devices.detail.timer

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
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceTimerRootBinding
import com.aqua.aqualight.ui.common.bottomsheet.IntegerStepperBottomSheet
import com.aqua.aqualight.ui.common.devicepresence.DeviceMenuUnavailableMessageMapper
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import kotlinx.coroutines.launch

class DeviceTimerRootFragment : Fragment(R.layout.fragment_device_timer_root) {

    private val args: DeviceTimerRootFragmentArgs by navArgs()
    private val viewModel: DeviceTimerRootViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceTimerRootBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceTimerRootBinding.bind(view)
        viewModel.bind(args.deviceUid)
        val initialState = viewModel.uiState.value
        setFragmentGlobalLoading(initialState.showBlockingPreparation)
        setupHeader(initialState)
        registerTemporaryOverrideResult()
        setupDashboardContent()
        observeViewModel()
    }

    private fun setupDashboardContent() {
        binding.timerDashboardCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceTimerDashboardScreen(
                    state = state,
                    onRegimeSelected = viewModel::selectRegime,
                    onProgramClick = ::openPrograms,
                    onTemporaryOverrideClick = ::showTemporaryOverrideSheet
                )
            }
        }
    }

    private fun setupHeader(state: DeviceTimerRootUiState) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = state.title.ifBlank {
                    getString(R.string.device_family_timer)
                },
                onBackClick = { findNavController().navigateUp() },
                statusIcon = state.connectionVisualState.toWifiHeaderStatusIcon(requireContext()),
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_settings,
                        contentDescription = getString(
                            R.string.device_timer_open_settings_description
                        ),
                        enabled = state.contentEnabled,
                        onClick = ::openSettings
                    )
                )
            )
        )
    }

    private fun openSettings() {
        if (!viewModel.uiState.value.contentEnabled) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceTimerRootFragment) return
        navController.navigate(
            DeviceTimerRootFragmentDirections
                .actionDeviceTimerRootFragmentToDeviceTimerSettingsFragment(
                    deviceUid = args.deviceUid
                )
        )
    }

    private fun openPrograms(slotId: String) {
        if (!viewModel.uiState.value.contentEnabled) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceTimerRootFragment) return
        navController.navigate(
            DeviceTimerRootFragmentDirections
                .actionDeviceTimerRootFragmentToDeviceTimerProgramFragment(
                    deviceUid = args.deviceUid,
                    slotId = slotId
                )
        )
    }

    private fun showTemporaryOverrideSheet(
        slotId: String,
        regime: DeviceTimerChannelRegime
    ) {
        IntegerStepperBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(
                if (regime == DeviceTimerChannelRegime.ON) {
                    R.string.device_timer_timed_on_title
                } else {
                    R.string.device_timer_timed_off_title
                }
            ),
            helperText = getString(R.string.device_timer_timed_duration_helper),
            valueFormat = getString(R.string.device_timer_duration_value_format),
            initialValue = DEFAULT_TEMPORARY_DURATION_MINUTES,
            minValue = MIN_TEMPORARY_DURATION_MINUTES,
            maxValue = MAX_TEMPORARY_DURATION_MINUTES,
            step = TEMPORARY_DURATION_STEP_MINUTES,
            saveText = getString(R.string.device_timer_apply),
            cancelText = getString(R.string.device_timer_cancel),
            decreaseContentDescription = getString(R.string.device_timer_duration_decrease),
            increaseContentDescription = getString(R.string.device_timer_duration_increase),
            requestKey = REQUEST_TEMPORARY_OVERRIDE,
            payloadId = "$slotId${PAYLOAD_SEPARATOR}${regime.name}"
        )
    }

    private fun registerTemporaryOverrideResult() {
        parentFragmentManager.setFragmentResultListener(
            REQUEST_TEMPORARY_OVERRIDE,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(IntegerStepperBottomSheet.RESULT_KEY) !=
                IntegerStepperBottomSheet.RESULT_SAVED
            ) return@setFragmentResultListener
            val payload = result.getString(IntegerStepperBottomSheet.RESULT_PAYLOAD_ID).orEmpty()
            val separatorIndex = payload.lastIndexOf(PAYLOAD_SEPARATOR)
            if (separatorIndex <= 0) return@setFragmentResultListener
            val regime = payload.substring(separatorIndex + 1)
                .let { value -> runCatching { DeviceTimerChannelRegime.valueOf(value) }.getOrNull() }
                ?: return@setFragmentResultListener
            val durationMillis = result.getInt(IntegerStepperBottomSheet.RESULT_VALUE) *
                MILLIS_PER_MINUTE
            viewModel.selectRegime(
                slotId = payload.substring(0, separatorIndex),
                regime = regime,
                temporaryDurationMillis = durationMillis
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> renderState(state) }
                }
                launch {
                    viewModel.surfaceUnavailableEvents.collect { reason ->
                        if (_binding == null) return@collect
                        setFragmentGlobalLoading(false)
                        val navController = findNavController()
                        if (navController.currentDestination?.id == R.id.deviceTimerRootFragment) {
                            navController.navigateUp()
                        }
                        (activity as? BaseActivity)?.showSnackBar(
                            message = getString(
                                DeviceMenuUnavailableMessageMapper.messageRes(reason)
                            ),
                            type = BaseActivity.SnackType.ERROR
                        )
                    }
                }
            }
        }
    }

    private fun renderState(state: DeviceTimerRootUiState) {
        if (_binding == null) return

        setupHeader(state)
        setFragmentGlobalLoading(state.showBlockingPreparation)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val REQUEST_TEMPORARY_OVERRIDE = "timer_temporary_override"
        const val PAYLOAD_SEPARATOR = ':'
        const val MIN_TEMPORARY_DURATION_MINUTES = 1
        const val MAX_TEMPORARY_DURATION_MINUTES = 1_440
        const val DEFAULT_TEMPORARY_DURATION_MINUTES = 30
        const val TEMPORARY_DURATION_STEP_MINUTES = 1
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
