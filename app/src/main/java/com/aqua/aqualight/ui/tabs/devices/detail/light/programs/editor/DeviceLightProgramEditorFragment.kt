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
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CloudFrequency
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramTimeMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.MoonlightChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCloudSimulationSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCurveTimePickerSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCustomDaysSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightMoonlightSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightPreviewDaySheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightTransitionVariantSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramNameSheet
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class DeviceLightProgramEditorFragment :
    Fragment(R.layout.fragment_device_light_program_editor) {

    private var _binding: FragmentDeviceLightProgramEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightProgramEditorViewModel by viewModels()

    private var isRendering = false
    private var previewDaySheet: LightPreviewDaySheet? = null

    private val deviceId: Long
        get() = arguments?.getLong(ARG_DEVICE_ID, 0L) ?: 0L

    private val programId: String?
        get() = arguments?.getString(ARG_PROGRAM_ID)

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
            row = binding.actionMoonlight.root,
            iconRes = R.drawable.ic_light_moon_24,
            title = "Moonlight",
            subtitle = "Soft output after sunset"
        )

        bindActionRow(
            row = binding.actionCloudSimulation.root,
            iconRes = R.drawable.ic_light_cloud_24,
            title = "Cloud Simulation",
            subtitle = "Natural light variation"
        )

        bindActionRow(
            row = binding.actionTransitionSmoothing.root,
            iconRes = R.drawable.ic_light_waves_24,
            title = "Transition Variant",
            subtitle = "Make ramps feel more natural"
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
            renderMoonlightSummary(state)
            renderCloudSimulationSummary(state)
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
            viewModel.updateRepeatWeekdays()
        }

        binding.repeatWeekend.setOnClickListener {
            viewModel.updateRepeatWeekend()
        }

        binding.repeatCustom.setOnClickListener {
            val state = viewModel.uiState.value

            LightCustomDaysSheet
                .create(requireContext())
                .show(
                    selectedDays = state.selectedDays
                ) { days ->
                    viewModel.updateCustomDays(days)
                }
        }

        binding.actionMoonlight.root.setOnClickListener {
            val state = viewModel.uiState.value

            LightMoonlightSheet
                .create(requireContext())
                .show(
                    initialSettings = state.moonlightSettings
                ) { settings ->
                    viewModel.updateMoonlight(settings)
                }
        }

        binding.actionCloudSimulation.root.setOnClickListener {
            val state = viewModel.uiState.value

            LightCloudSimulationSheet
                .create(requireContext())
                .show(
                    initialSettings = state.cloudSimulationSettings
                ) { settings ->
                    viewModel.updateCloudSimulation(settings)
                }
        }

        binding.actionTransitionSmoothing.root.setOnClickListener {
            val state = viewModel.uiState.value

            LightTransitionVariantSheet
                .create(requireContext())
                .show(
                    initialMode = state.transitionMode
                ) { mode ->
                    viewModel.updateTransitionMode(mode)
                }
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
    }

    private fun renderMoonlightSummary(
        state: DeviceLightProgramEditorUiState
    ) {
        val settings = state.moonlightSettings

        val subtitle = if (settings.enabled) {
            val channelText = when (settings.channel) {
                MoonlightChannel.BLUE -> "Blue"
                MoonlightChannel.WHITE -> "White"
                MoonlightChannel.BLUE_WHITE -> "Blue + White"
            }

            "$channelText • ${settings.intensityPercent}% • Until ${settings.endTime.label}"
        } else {
            "Soft output after sunset"
        }

        binding.actionMoonlight.root
            .findViewById<TextView>(R.id.tvActionSubtitle)
            ?.text = subtitle
    }

    private fun renderCloudSimulationSummary(
        state: DeviceLightProgramEditorUiState
    ) {
        val settings = state.cloudSimulationSettings

        val subtitle = if (settings.enabled) {
            val frequencyText = when (settings.frequency) {
                CloudFrequency.RARE -> "Rare"
                CloudFrequency.NORMAL -> "Normal"
                CloudFrequency.FREQUENT -> "Frequent"
            }

            "Coverage ${settings.coveragePercent}% • $frequencyText"
        } else {
            "Natural light variation"
        }

        binding.actionCloudSimulation.root
            .findViewById<TextView>(R.id.tvActionSubtitle)
            ?.text = subtitle
    }

    private fun renderTransitionSummary(
        state: DeviceLightProgramEditorUiState
    ) {
        val subtitle = when (state.transitionMode) {
            LightCurveTransitionMode.LINEAR -> "Linear ramp curve"
            LightCurveTransitionMode.SMOOTH -> "Smooth start and finish"
            LightCurveTransitionMode.NATURAL -> "Sunrise-like natural ramp"
        }

        binding.actionTransitionSmoothing.root
            .findViewById<TextView>(R.id.tvActionSubtitle)
            ?.text = subtitle
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