package com.aqua.aqualight.ui.tabs.devices.detail.timer.channel

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
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceTimerChannelBinding
import com.aqua.aqualight.ui.common.bottomsheet.IntegerStepperBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.SingleChoiceBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.devices.detail.timer.effectiveName
import com.aqua.aqualight.ui.tabs.devices.detail.timer.toCommercialTimerError
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
class DeviceTimerChannelFragment : Fragment(R.layout.fragment_device_timer_channel) {

    private val args: DeviceTimerChannelFragmentArgs by navArgs()
    private val viewModel: DeviceTimerChannelViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentDeviceTimerChannelBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceTimerChannelBinding.bind(view)
        registerResults()
        setupContent()
        observeState()
        viewModel.bind(args.deviceUid, args.slotId)
    }

    private fun setupContent() {
        binding.timerChannelCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceTimerChannelScreen(
                    state = state,
                    actions = DeviceTimerChannelActions(
                        onPowerClick = viewModel::toggleManualPower,
                        onTimedControlClick = ::showTimedControl,
                        onProgramsClick = ::openPrograms,
                        onWorkModeClick = ::showWorkMode,
                        onChannelNameClick = ::showChannelName
                    )
                )
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (_binding == null) return@collect
                        setupHeader(state)
                        setFragmentGlobalLoading(
                            state.loadState == DeviceTimerChannelLoadState.LOADING ||
                                state.mutationPending
                        )
                    }
                }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun setupHeader(state: DeviceTimerChannelDetailUiState) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = state.channel?.effectiveName
                    ?.takeIf(String::isNotBlank)
                    ?: getString(R.string.device_family_timer),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun openPrograms() {
        val state = viewModel.uiState.value
        if (!state.scheduleReadEnabled || state.mutationPending) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceTimerChannelFragment) return
        navController.navigate(
            DeviceTimerChannelFragmentDirections
                .actionDeviceTimerChannelFragmentToDeviceTimerProgramFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId
                )
        )
    }

    private fun showTimedControl() {
        val state = viewModel.uiState.value
        val channel = state.channel ?: return
        if (!state.temporaryOverrideWriteEnabled || state.mutationPending) return
        val initialRegime = if (channel.operatingState == DeviceTimerOperatingState.ON) {
            DeviceTimerChannelRegime.OFF
        } else {
            DeviceTimerChannelRegime.ON
        }
        DeviceTimerManualControlBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.device_timer_manual_control_title),
            message = getString(R.string.device_timer_manual_control_message),
            durationLabel = getString(R.string.device_timer_manual_duration_label),
            onText = getString(R.string.device_timer_timed_on),
            offText = getString(R.string.device_timer_timed_off),
            duration15Text = getString(R.string.device_timer_duration_15_minutes),
            duration30Text = getString(R.string.device_timer_duration_30_minutes),
            duration60Text = getString(R.string.device_timer_duration_1_hour),
            customText = getString(R.string.device_timer_duration_custom),
            startText = getString(R.string.device_timer_manual_start),
            resumeText = getString(R.string.device_timer_resume_program),
            resumeVisible = channel.temporaryOverrideActive,
            initialRegime = initialRegime,
            requestKey = REQUEST_MANUAL_CONTROL
        )
    }

    private fun showCustomDuration(regime: DeviceTimerChannelRegime) {
        IntegerStepperBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.device_timer_manual_custom_duration_title),
            helperText = getString(R.string.device_timer_timed_duration_helper),
            valueFormat = getString(R.string.device_timer_duration_value_format),
            initialValue = DEFAULT_CUSTOM_DURATION_MINUTES,
            minValue = MIN_TEMPORARY_DURATION_MINUTES,
            maxValue = MAX_TEMPORARY_DURATION_MINUTES,
            step = TEMPORARY_DURATION_STEP_MINUTES,
            saveText = getString(R.string.device_timer_manual_start),
            cancelText = getString(R.string.device_timer_cancel),
            decreaseContentDescription = getString(R.string.device_timer_duration_decrease),
            increaseContentDescription = getString(R.string.device_timer_duration_increase),
            requestKey = REQUEST_CUSTOM_DURATION,
            payloadId = regime.name
        )
    }

    private fun showWorkMode() {
        val state = viewModel.uiState.value
        val channel = state.channel ?: return
        if (!state.channelStateWriteEnabled || state.mutationPending) return
        SingleChoiceBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.device_timer_work_mode_title),
            options = listOf(
                DeviceTimerWorkMode.MANUAL.name to
                    getString(R.string.device_timer_work_mode_manual),
                DeviceTimerWorkMode.PROGRAM.name to
                    getString(R.string.device_timer_work_mode_program)
            ),
            selectedId = if (channel.regime == DeviceTimerChannelRegime.AUTO) {
                DeviceTimerWorkMode.PROGRAM.name
            } else {
                DeviceTimerWorkMode.MANUAL.name
            },
            columns = 1,
            requestKey = REQUEST_WORK_MODE
        )
    }

    private fun showChannelName() {
        val state = viewModel.uiState.value
        val channel = state.channel ?: return
        if (!state.displayNameWriteEnabled || state.mutationPending) return
        TextInputBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.device_timer_channel_name_title),
            label = getString(R.string.device_timer_channel_name_label),
            hint = channel.defaultName,
            initialValue = channel.effectiveName,
            supportingText = getString(R.string.device_timer_channel_name_helper),
            saveText = getString(R.string.device_timer_apply),
            cancelText = getString(R.string.device_timer_cancel),
            required = true,
            requiredMessage = getString(R.string.device_timer_channel_name_required),
            requestKey = REQUEST_CHANNEL_NAME,
            maxLength = MAX_NAME_CHARACTERS,
            disableSaveWhenUnchanged = true,
            requestFocus = true,
            presetActionText = getString(R.string.device_timer_channel_name_reset),
            presetDisplayValue = channel.defaultName,
            presetResultValue = ""
        )
    }

    private fun registerResults() {
        registerTimedControlResult()
        registerCustomDurationResult()
        registerWorkModeResult()
        registerChannelNameResult()
    }

    private fun registerTimedControlResult() {
        parentFragmentManager.setFragmentResultListener(
            REQUEST_MANUAL_CONTROL,
            viewLifecycleOwner
        ) { _, result ->
            val regime = result.getString(DeviceTimerManualControlBottomSheet.RESULT_REGIME)
                ?.let { runCatching { DeviceTimerChannelRegime.valueOf(it) }.getOrNull() }
                ?: return@setFragmentResultListener
            when (result.getString(DeviceTimerManualControlBottomSheet.RESULT_KEY)) {
                DeviceTimerManualControlBottomSheet.RESULT_START ->
                    viewModel.startTemporaryOverride(
                        regime,
                        result.getInt(
                            DeviceTimerManualControlBottomSheet.RESULT_DURATION_MINUTES
                        )
                    )
                DeviceTimerManualControlBottomSheet.RESULT_CUSTOM_DURATION ->
                    showCustomDuration(regime)
                DeviceTimerManualControlBottomSheet.RESULT_RESUME ->
                    viewModel.resumePersistentMode()
            }
        }
    }

    private fun registerCustomDurationResult() {
        parentFragmentManager.setFragmentResultListener(
            REQUEST_CUSTOM_DURATION,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(IntegerStepperBottomSheet.RESULT_KEY) !=
                IntegerStepperBottomSheet.RESULT_SAVED
            ) return@setFragmentResultListener
            val regime = result.getString(IntegerStepperBottomSheet.RESULT_PAYLOAD_ID)
                ?.let { runCatching { DeviceTimerChannelRegime.valueOf(it) }.getOrNull() }
                ?: return@setFragmentResultListener
            viewModel.startTemporaryOverride(
                regime,
                result.getInt(IntegerStepperBottomSheet.RESULT_VALUE)
            )
        }
    }

    private fun registerWorkModeResult() {
        parentFragmentManager.setFragmentResultListener(
            REQUEST_WORK_MODE,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(SingleChoiceBottomSheet.RESULT_KEY) !=
                SingleChoiceBottomSheet.RESULT_SELECTED
            ) return@setFragmentResultListener
            val workMode = result.getString(SingleChoiceBottomSheet.RESULT_SELECTED_ID)
                ?.let { runCatching { DeviceTimerWorkMode.valueOf(it) }.getOrNull() }
                ?: return@setFragmentResultListener
            viewModel.setWorkMode(workMode)
        }
    }

    private fun registerChannelNameResult() {
        parentFragmentManager.setFragmentResultListener(
            REQUEST_CHANNEL_NAME,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(TextInputBottomSheet.RESULT_KEY) !=
                TextInputBottomSheet.RESULT_SAVED
            ) return@setFragmentResultListener
            viewModel.updateDisplayName(
                result.getString(TextInputBottomSheet.RESULT_VALUE)
                    .orEmpty()
                    .takeIf(String::isNotBlank)
            )
        }
    }

    private fun handleEvent(event: DeviceTimerChannelEvent) {
        when (event) {
            is DeviceTimerChannelEvent.Failed -> {
                val copy = event.failure.toCommercialTimerError()
                (activity as? BaseActivity)?.showSnackBar(
                    getString(copy.messageRes),
                    BaseActivity.SnackType.WARNING
                )
            }
            DeviceTimerChannelEvent.InvalidDisplayName ->
                (activity as? BaseActivity)?.showSnackBar(
                    getString(R.string.device_timer_channel_name_invalid),
                    BaseActivity.SnackType.WARNING
                )
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val REQUEST_MANUAL_CONTROL = "timer_manual_control"
        const val REQUEST_CUSTOM_DURATION = "timer_custom_duration"
        const val REQUEST_WORK_MODE = "timer_work_mode"
        const val REQUEST_CHANNEL_NAME = "timer_channel_name"
        const val MIN_TEMPORARY_DURATION_MINUTES = 1
        const val MAX_TEMPORARY_DURATION_MINUTES = 1_440
        const val DEFAULT_CUSTOM_DURATION_MINUTES = 30
        const val TEMPORARY_DURATION_STEP_MINUTES = 1
        const val MAX_NAME_CHARACTERS = 48
    }
}
