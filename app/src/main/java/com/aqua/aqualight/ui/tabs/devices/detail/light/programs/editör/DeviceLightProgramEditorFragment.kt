package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
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
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CloudFrequency
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.MoonlightChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.PreviewSpeed
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCloudSimulationSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCurveTimePickerSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCustomDaysSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightMoonlightSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightPreviewDaySheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightTransitionVariantSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramNameSheet
import kotlinx.coroutines.launch

class DeviceLightProgramEditorFragment :
    Fragment(R.layout.fragment_device_light_program_editor) {

    private var _binding: FragmentDeviceLightProgramEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightProgramEditorViewModel by viewModels()

    private var isRendering = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightProgramEditorBinding.bind(view)

        setupHeader()
        setupProgramSettingsRows()
        setupClicks()
        setupSliders()
        observeUiState()
        observeEvents()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            AquaHeaderConfig(
                title = "Program Editor",
                showBackButton = true,
                onBackClick = {
                    findNavController().popBackStack()
                }
            )
        )
    }

    private fun setupProgramSettingsRows() {
        bindActionRow(
            row = binding.actionMoonlight.root,
            icon = "◐",
            title = "Moonlight",
            subtitle = "Soft output after sunset"
        )

        bindActionRow(
            row = binding.actionCloudSimulation.root,
            icon = "☁",
            title = "Cloud Simulation",
            subtitle = "Natural light variation"
        )

        bindActionRow(
            row = binding.actionTransitionSmoothing.root,
            icon = "≈",
            title = "Transition Variant",
            subtitle = "Make ramps feel more natural"
        )
    }

    private fun bindActionRow(
        row: View,
        icon: String,
        title: String,
        subtitle: String
    ) {
        row.findViewById<TextView>(R.id.tvActionIcon)?.text = icon
        row.findViewById<TextView>(R.id.tvActionTitle)?.text = title
        row.findViewById<TextView>(R.id.tvActionSubtitle)?.text = subtitle
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
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                        }

                        is DeviceLightProgramEditorEvent.ShowError -> {
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
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

        binding.tvTimeStartValue.text = state.start.label
        binding.tvTimePeakStartValue.text = state.peakStart.label
        binding.tvTimePeakEndValue.text = state.peakEnd.label
        binding.tvTimeEndValue.text = state.end.label

        binding.sliderRed.value = state.channelValues.red.toFloat()
        binding.sliderGreen.value = state.channelValues.green.toFloat()
        binding.sliderBlue.value = state.channelValues.blue.toFloat()
        binding.sliderWhite.value = state.channelValues.white.toFloat()

        binding.tvRedValue.text = "${state.channelValues.red}%"
        binding.tvGreenValue.text = "${state.channelValues.green}%"
        binding.tvBlueValue.text = "${state.channelValues.blue}%"
        binding.tvWhiteValue.text = "${state.channelValues.white}%"

        binding.lightCurveGraphView.setState(state.graphState)

        renderRepeatMode(state)
        renderMoonlightSummary(state)
        renderCloudSimulationSummary(state)
        renderTransitionSummary(state)

        isRendering = false
    }

    private fun setupClicks() {
        binding.btnPreviewProgram.setOnClickListener {
            val state = viewModel.uiState.value

            LightPreviewDaySheet
                .create(requireContext())
                .show(
                    initialSpeed = state.previewSpeed
                ) { speed ->
                    viewModel.startPreview(speed)
                }
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
            showProgramNameSheet(
                title = "Load to Device",
                subtitle = "Name this program before loading it to the device.",
                primaryButtonText = "Load",
                isActive = true
            )
        }

        binding.btnSaveAs.setOnClickListener {
            showProgramNameSheet(
                title = "Save As",
                subtitle = "Save this program without activating it.",
                primaryButtonText = "Save",
                isActive = false
            )
        }
    }

    private fun setupSliders() {
        binding.sliderRed.addOnChangeListener { _, value, _ ->
            if (isRendering) return@addOnChangeListener

            val current = viewModel.uiState.value.channelValues
            viewModel.updateChannelValues(
                current.copy(red = value.toInt())
            )
        }

        binding.sliderGreen.addOnChangeListener { _, value, _ ->
            if (isRendering) return@addOnChangeListener

            val current = viewModel.uiState.value.channelValues
            viewModel.updateChannelValues(
                current.copy(green = value.toInt())
            )
        }

        binding.sliderBlue.addOnChangeListener { _, value, _ ->
            if (isRendering) return@addOnChangeListener

            val current = viewModel.uiState.value.channelValues
            viewModel.updateChannelValues(
                current.copy(blue = value.toInt())
            )
        }

        binding.sliderWhite.addOnChangeListener { _, value, _ ->
            if (isRendering) return@addOnChangeListener

            val current = viewModel.uiState.value.channelValues
            viewModel.updateChannelValues(
                current.copy(white = value.toInt())
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
                onSelected(LightCurvePoint.of(hour, minute))
            }
    }

    private fun showProgramNameSheet(
        title: String,
        subtitle: String,
        primaryButtonText: String,
        isActive: Boolean
    ) {
        LightProgramNameSheet
            .create(requireContext())
            .show(
                title = title,
                subtitle = subtitle,
                primaryButtonText = primaryButtonText,
                initialName = ""
            ) { name ->
                viewModel.saveProgram(
                    name = name,
                    isActive = isActive
                )
            }
    }

    private fun renderRepeatMode(
        state: DeviceLightProgramEditorUiState
    ) {
        val selectedBg = R.drawable.bg_light_filter_selected
        val transparentBg = android.R.color.transparent

        val selectedText = requireContext().getColor(R.color.light_button_on_primary)
        val normalText = requireContext().getColor(R.color.light_text_secondary)

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
        _binding = null
        super.onDestroyView()
    }
}