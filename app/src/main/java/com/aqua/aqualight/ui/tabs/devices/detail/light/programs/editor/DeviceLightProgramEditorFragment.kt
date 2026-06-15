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
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.LightProgramTimeMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.sheet.LightCurveTimePickerSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCustomDaysSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightPreviewDaySheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightTransitionVariantSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramNameSheet
import com.google.android.material.slider.Slider
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.launch

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
        bindActionRow(
            row = binding.actionTransitionSmoothing.root,
            iconRes = R.drawable.ic_light_waves_24,
            title = "Natural Transition",
            subtitle = "Sunrise-like natural ramp"
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
                progressPercent = state.previewProgressPercent
            )

            renderRepeatMode(state)
            renderTransitionSummary(state)
            renderSaveBarState(state)
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
            viewModel.updateRepeatWeekdays()
        }

        binding.repeatWeekend.setOnClickListener {
            viewModel.updateRepeatWeekend()
        }

        binding.repeatCustom.setOnClickListener {
            val state = viewModel.uiState.value
            if (!state.repeatFeatureEnabled) {
                viewModel.updateCustomDays(state.selectedDays)
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
            val state = viewModel.uiState.value
            if (!state.transitionFeatureEnabled || state.isBusy) {
                return@setOnClickListener
            }

            LightTransitionVariantSheet
                .create(requireContext())
                .show(
                    initialMode = state.transitionMode
                ) { mode ->
                    viewModel.updateTransitionMode(mode)
                }
        }

        binding.btnLoadToDevice.setOnClickListener {
            if (viewModel.isEditingExistingProgram()) {
                viewModel.saveProgram(
                    name = viewModel.currentProgramName(),
                    activateOnDevice = true
                )
                return@setOnClickListener
            }

            showProgramNameSheet(
                title = "Load to Device",
                subtitle = "Name this program before loading it to the device.",
                primaryButtonText = "Load",
                activateOnDevice = true
            )
        }

        binding.btnSaveAs.setOnClickListener {
            if (viewModel.isEditingExistingProgram()) {
                viewModel.saveProgram(
                    name = viewModel.currentProgramName(),
                    activateOnDevice = false
                )
                return@setOnClickListener
            }

            showProgramNameSheet(
                title = "Save Program",
                subtitle = "Save this program without loading it to the device.",
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

        binding.repeatEvery.setBackgroundResource(
            if (state.repeatMode == RepeatMode.EVERY) selectedBg else transparentBg
        )

        binding.repeatWeekdays.setBackgroundResource(
            if (state.repeatMode == RepeatMode.WEEK) selectedBg else transparentBg
        )

        binding.repeatWeekend.setBackgroundResource(
            if (state.repeatMode == RepeatMode.WEEKEND) selectedBg else transparentBg
        )

        binding.repeatCustom.setBackgroundResource(
            if (state.repeatMode == RepeatMode.CUSTOM) selectedBg else transparentBg
        )

        binding.repeatEvery.setTextColor(
            if (state.repeatMode == RepeatMode.EVERY) selectedText else normalText
        )

        binding.repeatWeekdays.setTextColor(
            if (state.repeatMode == RepeatMode.WEEK) selectedText else normalText
        )

        binding.repeatWeekend.setTextColor(
            if (state.repeatMode == RepeatMode.WEEKEND) selectedText else normalText
        )

        binding.repeatCustom.setTextColor(
            if (state.repeatMode == RepeatMode.CUSTOM) selectedText else normalText
        )

        binding.repeatEvery.isEnabled = !state.isBusy
        binding.repeatWeekdays.isEnabled = !state.isBusy
        binding.repeatWeekend.isEnabled = !state.isBusy
        binding.repeatCustom.isEnabled = !state.isBusy

        binding.repeatEvery.alpha = if (state.isBusy) 0.55f else 1f
        val lockedRepeatAlpha = if (state.repeatFeatureEnabled) 1f else 0.42f
        binding.repeatWeekdays.alpha = if (state.isBusy) 0.35f else lockedRepeatAlpha
        binding.repeatWeekend.alpha = if (state.isBusy) 0.35f else lockedRepeatAlpha
        binding.repeatCustom.alpha = if (state.isBusy) 0.35f else lockedRepeatAlpha
    }

    private fun renderTransitionSummary(
        state: DeviceLightProgramEditorUiState
    ) {
        val (title, subtitle) = when (state.transitionMode) {
            LightCurveTransitionMode.LINEAR -> {
                "Linear Transition" to "Even ramp, predictable channel changes"
            }

            LightCurveTransitionMode.SMOOTH -> {
                "Smooth Transition" to "Soft start and stop between light levels"
            }

            LightCurveTransitionMode.NATURAL -> {
                "Natural Transition" to "Sunrise-like natural ramp"
            }
        }

        bindActionRow(
            row = binding.actionTransitionSmoothing.root,
            iconRes = R.drawable.ic_light_waves_24,
            title = title,
            subtitle = subtitle
        )

        binding.actionTransitionSmoothing.root.isEnabled =
            state.transitionFeatureEnabled && !state.isBusy

        binding.actionTransitionSmoothing.root.alpha = if (state.transitionFeatureEnabled) {
            1f
        } else {
            0.45f
        }
    }

    private fun renderSaveBarState(
        state: DeviceLightProgramEditorUiState
    ) {
        binding.btnSaveAs.text = if (state.isEditingExistingProgram) {
            "Save Changes"
        } else {
            "Save As"
        }

        binding.btnLoadToDevice.text = if (state.isEditingExistingProgram) {
            "Load to Device"
        } else {
            "Load to Device"
        }

        binding.btnSaveAs.isEnabled = !state.isBusy
        binding.btnLoadToDevice.isEnabled = !state.isBusy
        binding.btnPreviewProgram.isEnabled = !state.isBusy
        binding.sliderRed.isEnabled = !state.isBusy
        binding.sliderGreen.isEnabled = !state.isBusy
        binding.sliderBlue.isEnabled = !state.isBusy
        binding.sliderWhite.isEnabled = !state.isBusy

        binding.editorContentContainer.alpha = if (state.isBusy) {
            0.55f
        } else {
            1f
        }
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
    }
}
