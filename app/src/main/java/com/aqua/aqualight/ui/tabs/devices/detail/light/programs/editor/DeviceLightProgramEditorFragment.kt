package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightProgramEditorBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceFeedbackType
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceLoading
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceSnack
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightTransitionModeUiText
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTimeMath
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.sheet.LightCurveTimePickerSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCustomDaysSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightPreviewDaySheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightTransitionVariantSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramNameSheet
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import androidx.navigation.fragment.navArgs

class DeviceLightProgramEditorFragment :
    Fragment(R.layout.fragment_device_light_program_editor) {

    private val args: DeviceLightProgramEditorFragmentArgs by navArgs()


    private var _binding: FragmentDeviceLightProgramEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightProgramEditorViewModel by viewModels()

    private var isRendering = false
    private var previewDaySheet: LightPreviewDaySheet? = null

    private val deviceId: Long
        get() = args.deviceId

    private val programId: String?
        get() = args.programId

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightProgramEditorBinding.bind(view)

        viewModel.initialize(
            deviceId = deviceId,
            programId = programId
        )

        setupHeader()
        setupProgramSettingsRows()
        setupClicks()
        setupSliders()
        observeUiState()
        observeEvents()
    }

    private fun setupHeader() {
    binding.appHeader.setupAquaHeader(
        fragment = this,
        config = AquaHeaderConfig(
            titleOverride = "Program Editor"
        )
    )
}

    private fun setupProgramSettingsRows() {
        val initialTransitionMode = viewModel.uiState.value.transitionMode
        bindActionRow(
            row = binding.actionTransitionSmoothing.root,
            iconRes = R.drawable.ic_light_waves_24,
            title = LightTransitionModeUiText.title(initialTransitionMode),
            subtitle = LightTransitionModeUiText.subtitle(initialTransitionMode)
        )
    }

    private fun bindActionRow(
        row: View,
        iconRes: Int,
        title: String,
        subtitle: String
    ) {
        row.findViewById<ImageView>(R.id.ivActionIcon)
            ?.setImageResource(iconRes)

        row.findViewById<TextView>(R.id.tvActionTitle)
            ?.text = title

        row.findViewById<TextView>(R.id.tvActionSubtitle)
            ?.text = subtitle
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is DeviceLightProgramEditorEvent.ShowMessage -> {
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.SUCCESS
                            )
                        }

                        is DeviceLightProgramEditorEvent.ShowError -> {
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.ERROR
                            )
                        }

                        is DeviceLightProgramEditorEvent.SetLoading -> {
                            showDeviceLoading(event.isLoading)
                        }

                        DeviceLightProgramEditorEvent.NavigateBack -> {
                            findNavController().popBackStack()
                        }
                    }
                }
            }
        }
    }

    private fun renderUiState(
        state: DeviceLightProgramEditorUiState
    ) {
        isRendering = true

        try {
            binding.tvTimeStartValue.text = state.start.label
            binding.tvTimePeakStartValue.text = state.peakStart.label
            binding.tvTimePeakEndValue.text = state.peakEnd.label
            binding.tvTimeEndValue.text = LightProgramTimeMath.endLabel(state.end)

            setSliderValueIfNeeded(
                slider = binding.sliderRed,
                value = state.channelValues.red
            )

            setSliderValueIfNeeded(
                slider = binding.sliderGreen,
                value = state.channelValues.green
            )

            setSliderValueIfNeeded(
                slider = binding.sliderBlue,
                value = state.channelValues.blue
            )

            setSliderValueIfNeeded(
                slider = binding.sliderWhite,
                value = state.channelValues.white
            )

            binding.tvRedValue.text = "${state.channelValues.red}%"
            binding.tvGreenValue.text = "${state.channelValues.green}%"
            binding.tvBlueValue.text = "${state.channelValues.blue}%"
            binding.tvWhiteValue.text = "${state.channelValues.white}%"

            binding.lightCurveGraphView.setState(state.graphState)

            binding.btnPreviewProgram.setImageResource(
                if (state.isPreviewRunning) {
                    R.drawable.ic_light_stop_24
                } else {
                    R.drawable.ic_light_play_24
                }
            )

            binding.btnPreviewProgram.contentDescription =
                if (state.isPreviewRunning) {
                    "Stop preview"
                } else {
                    "Preview program"
                }

            previewDaySheet?.renderPreviewState(
                isPreviewRunning = state.isPreviewRunning,
                progressPercent = state.previewProgressPercent,
                simulatedTimeLabel = state.previewSimulationTime?.label
            )

            renderRepeatMode(state)
            renderTransitionSummary(state)
        } finally {
            isRendering = false
        }
    }

    private fun setSliderValueIfNeeded(
        slider: Slider,
        value: Int
    ) {
        val safeValue = value
            .coerceIn(0, 100)
            .toFloat()

        if (slider.value != safeValue) {
            slider.value = safeValue
        }
    }

    private fun setupClicks() {
        binding.btnPreviewProgram.setOnClickListener {
            val state = viewModel.uiState.value

            if (state.isPreviewRunning) {
                viewModel.stopPreview()
                return@setOnClickListener
            }

            showPreviewDaySheet(state)
        }

        binding.tvTimeStart.setOnClickListener {
            val state = viewModel.uiState.value

            showTimePickerSheet(
                title = "Start Time",
                point = state.start
            ) { selectedPoint ->
                viewModel.updateStartTime(selectedPoint)
            }
        }

        binding.tvTimePeakStart.setOnClickListener {
            val state = viewModel.uiState.value

            showTimePickerSheet(
                title = "Peak Start Time",
                point = state.peakStart
            ) { selectedPoint ->
                viewModel.updatePeakStartTime(selectedPoint)
            }
        }

        binding.tvTimePeakEnd.setOnClickListener {
            val state = viewModel.uiState.value

            showTimePickerSheet(
                title = "Peak End Time",
                point = state.peakEnd
            ) { selectedPoint ->
                viewModel.updatePeakEndTime(selectedPoint)
            }
        }

        binding.tvTimeEnd.setOnClickListener {
            val state = viewModel.uiState.value

            showTimePickerSheet(
                title = "End Time",
                point = state.end
            ) { selectedPoint ->
                viewModel.updateEndTime(selectedPoint)
            }
        }

        binding.repeatEvery.setOnClickListener {
            viewModel.updateRepeatEvery()
        }

        binding.repeatWeekdays.setOnClickListener {
            if (viewModel.uiState.value.repeatSelectionEnabled) {
                viewModel.updateRepeatWeekdays()
            }
        }

        binding.repeatWeekend.setOnClickListener {
            if (viewModel.uiState.value.repeatSelectionEnabled) {
                viewModel.updateRepeatWeekend()
            }
        }

        binding.repeatCustom.setOnClickListener {
            val state = viewModel.uiState.value

            if (!state.repeatSelectionEnabled) {
                return@setOnClickListener
            }

            LightCustomDaysSheet
                .create(requireContext())
                .show(
                    selectedDays = state.selectedDays
                ) { days ->
                    viewModel.updateCustomDays(days)
                }
        }

        binding.actionTransitionSmoothing.root.setOnClickListener {
            showTransitionModeSheet()
        }

        binding.btnLoadToDevice.setOnClickListener {
            val isEditing = viewModel.isEditingExistingProgram()

            showProgramNameSheet(
                title = if (isEditing) {
                    "Update Device"
                } else {
                    "Load to Device"
                },
                subtitle = if (isEditing) {
                    "Save changes and update the active device schedule."
                } else {
                    "Name this program before loading it to the device."
                },
                primaryButtonText = if (isEditing) {
                    "Update"
                } else {
                    "Load"
                },
                activateOnDevice = true
            )
        }

        binding.btnSaveAs.setOnClickListener {
            val isEditing = viewModel.isEditingExistingProgram()

            showProgramNameSheet(
                title = if (isEditing) {
                    "Save Changes"
                } else {
                    "Save Program"
                },
                subtitle = if (isEditing) {
                    "Update this program. Active programs will sync to the device."
                } else {
                    "Save this program without loading it to the device."
                },
                primaryButtonText = "Save",
                activateOnDevice = false
            )
        }
    }

    private fun showPreviewDaySheet(
        state: DeviceLightProgramEditorUiState
    ) {
        val sheet = LightPreviewDaySheet.create(
            context = requireContext()
        )

        previewDaySheet = sheet

        sheet.show(
            initialSpeed = state.previewSpeed,
            initialProgressPercent = state.previewProgressPercent,
            initialSimulatedTimeLabel = state.previewSimulationTime?.label,
            isPreviewRunning = state.isPreviewRunning,
            onStartPreview = { speed ->
                viewModel.startPreview(speed)
            },
            onStopPreview = {
                viewModel.stopPreview()
            },
            onDismiss = {
                if (previewDaySheet === sheet) {
                    previewDaySheet = null
                }
            }
        )
    }

    private fun setupSliders() {
        binding.sliderRed.addOnChangeListener { _, value, fromUser ->
            if (isRendering || !fromUser) return@addOnChangeListener

            viewModel.updateChannelValues(
                viewModel.uiState.value.channelValues.copy(
                    red = value.toInt()
                )
            )
        }

        binding.sliderGreen.addOnChangeListener { _, value, fromUser ->
            if (isRendering || !fromUser) return@addOnChangeListener

            viewModel.updateChannelValues(
                viewModel.uiState.value.channelValues.copy(
                    green = value.toInt()
                )
            )
        }

        binding.sliderBlue.addOnChangeListener { _, value, fromUser ->
            if (isRendering || !fromUser) return@addOnChangeListener

            viewModel.updateChannelValues(
                viewModel.uiState.value.channelValues.copy(
                    blue = value.toInt()
                )
            )
        }

        binding.sliderWhite.addOnChangeListener { _, value, fromUser ->
            if (isRendering || !fromUser) return@addOnChangeListener

            viewModel.updateChannelValues(
                viewModel.uiState.value.channelValues.copy(
                    white = value.toInt()
                )
            )
        }
    }

    private fun showTimePickerSheet(
        title: String,
        point: LightCurvePoint,
        onSelected: (LightCurvePoint) -> Unit
    ) {
        LightCurveTimePickerSheet
            .create(requireContext())
            .show(
                title = title,
                initialHour = point.hour,
                initialMinute = point.minute
            ) { hour, minute ->
                onSelected(
                    LightCurvePoint.of(
                        hour = hour,
                        minute = minute
                    )
                )
            }
    }

    private fun showTransitionModeSheet() {
        val state = viewModel.uiState.value

        LightTransitionVariantSheet
            .create(requireContext())
            .show(
                initialMode = state.transitionMode
            ) { selectedMode ->
                viewModel.updateTransitionMode(selectedMode)
            }
    }

    private fun showProgramNameSheet(
        title: String,
        subtitle: String,
        primaryButtonText: String,
        activateOnDevice: Boolean
    ) {
        LightProgramNameSheet
            .create(requireContext())
            .show(
                title = title,
                subtitle = subtitle,
                primaryButtonText = primaryButtonText,
                initialName = viewModel.currentProgramName()
            ) { name ->
                viewModel.saveProgram(
                    name = name,
                    activateOnDevice = activateOnDevice
                )
            }
    }

    private fun renderRepeatMode(
        state: DeviceLightProgramEditorUiState
    ) {
        val selectedBg = R.drawable.bg_light_filter_selected
        val transparentBg = android.R.color.transparent

        val selectedText =
            requireContext().getColor(R.color.light_button_on_primary)

        val normalText =
            requireContext().getColor(R.color.light_text_secondary)

        val disabledText =
            requireContext().getColor(R.color.light_text_disabled)

        setRepeatOptionState(
            view = binding.repeatEvery,
            selected = state.repeatMode == RepeatMode.EVERY,
            enabled = true,
            selectedBg = selectedBg,
            transparentBg = transparentBg,
            selectedText = selectedText,
            normalText = normalText,
            disabledText = disabledText
        )

        setRepeatOptionState(
            view = binding.repeatWeekdays,
            selected = state.repeatMode == RepeatMode.WEEK,
            enabled = state.repeatSelectionEnabled,
            selectedBg = selectedBg,
            transparentBg = transparentBg,
            selectedText = selectedText,
            normalText = normalText,
            disabledText = disabledText
        )

        setRepeatOptionState(
            view = binding.repeatWeekend,
            selected = state.repeatMode == RepeatMode.WEEKEND,
            enabled = state.repeatSelectionEnabled,
            selectedBg = selectedBg,
            transparentBg = transparentBg,
            selectedText = selectedText,
            normalText = normalText,
            disabledText = disabledText
        )

        setRepeatOptionState(
            view = binding.repeatCustom,
            selected = state.repeatMode == RepeatMode.CUSTOM,
            enabled = state.repeatSelectionEnabled,
            selectedBg = selectedBg,
            transparentBg = transparentBg,
            selectedText = selectedText,
            normalText = normalText,
            disabledText = disabledText
        )

        binding.tvRepeatFirmwareHint.visibility = if (state.repeatUnavailableReason.isNullOrBlank()) {
            View.GONE
        } else {
            View.VISIBLE
        }

        binding.tvRepeatFirmwareHint.text = state.repeatUnavailableReason.orEmpty()
    }

    private fun setRepeatOptionState(
        view: TextView,
        selected: Boolean,
        enabled: Boolean,
        selectedBg: Int,
        transparentBg: Int,
        selectedText: Int,
        normalText: Int,
        disabledText: Int
    ) {
        view.setBackgroundResource(
            if (selected) selectedBg else transparentBg
        )

        view.setTextColor(
            when {
                selected -> selectedText
                enabled -> normalText
                else -> disabledText
            }
        )

        view.isEnabled = enabled
        view.isClickable = enabled
        view.alpha = if (enabled || selected) {
            1f
        } else {
            REPEAT_OPTION_DISABLED_ALPHA
        }
    }

    private fun renderTransitionSummary(
        state: DeviceLightProgramEditorUiState
    ) {
        binding.actionTransitionSmoothing.root
            .findViewById<TextView>(R.id.tvActionTitle)
            ?.text = LightTransitionModeUiText.title(state.transitionMode)

        binding.actionTransitionSmoothing.root
            .findViewById<TextView>(R.id.tvActionSubtitle)
            ?.text = LightTransitionModeUiText.subtitle(state.transitionMode)
    }

    override fun onDestroyView() {
        showDeviceLoading(false)

        previewDaySheet?.dismiss()
        previewDaySheet = null

        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_PROGRAM_ID = "programId"

        private const val REPEAT_OPTION_DISABLED_ALPHA = 0.46f
    }
}
