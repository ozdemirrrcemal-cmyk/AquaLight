package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightProgramEditorBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveGraphState
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CloudFrequency
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CloudSimulationSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.MoonlightChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.MoonlightSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.PreviewSpeed
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCloudSimulationSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCurveTimePickerSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCustomDaysSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightMoonlightSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightPreviewDaySheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightTransitionVariantSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.mapper.LightProgramDraftMapper
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramNameSheet

class DeviceLightProgramEditorFragment :
    Fragment(R.layout.fragment_device_light_program_editor) {

    private var _binding: FragmentDeviceLightProgramEditorBinding? = null
    private val binding get() = _binding!!

    private val initialDraft = LightProgramDraft.default()

    private var startPoint = initialDraft.start
    private var peakStartPoint = initialDraft.peakStart
    private var peakEndPoint = initialDraft.peakEnd
    private var endPoint = initialDraft.end

    private var selectedRepeatMode = initialDraft.repeatMode
    private var selectedCustomDays: Set<Int> = initialDraft.selectedDays

    private var moonlightSettings = initialDraft.moonlightSettings
    private var cloudSimulationSettings = initialDraft.cloudSimulationSettings
    private var selectedTransitionMode = initialDraft.transitionMode

    private var selectedPreviewSpeed = PreviewSpeed.ONE_MINUTE

    private var currentDeviceTimePoint = LightCurvePoint.of(13, 28)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightProgramEditorBinding.bind(view)

        setupHeader()
        renderTimeRows()
        setupProgramSettingsRows()
        renderMoonlightSummary()
        renderCloudSimulationSummary()
        renderTransitionSummary()
        setupClicks()
        setupSliders()
        renderSliderValues()
        renderRepeatMode()
        updateGraph()
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

    private fun setupClicks() {
        binding.btnPreviewProgram.setOnClickListener {
            LightPreviewDaySheet
                .create(requireContext())
                .show(
                    initialSpeed = selectedPreviewSpeed
                ) { speed ->
                    selectedPreviewSpeed = speed
                    val draft = buildCurrentProgramDraft()

                    Toast.makeText(
                        requireContext(),
                        "Preview started: ${speed.label}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // TODO: Send draft as temporary preview payload to ESP32.
                }
        }

        binding.tvTimeStart.setOnClickListener {
            showTimePickerSheet(
                title = "Start Time",
                point = startPoint
            ) { selectedPoint ->
                startPoint = selectedPoint
                renderTimeRows()
                updateGraph()
            }
        }

        binding.tvTimePeakStart.setOnClickListener {
            showTimePickerSheet(
                title = "Peak Start Time",
                point = peakStartPoint
            ) { selectedPoint ->
                peakStartPoint = selectedPoint
                renderTimeRows()
                updateGraph()
            }
        }

        binding.tvTimePeakEnd.setOnClickListener {
            showTimePickerSheet(
                title = "Peak End Time",
                point = peakEndPoint
            ) { selectedPoint ->
                peakEndPoint = selectedPoint
                renderTimeRows()
                updateGraph()
            }
        }

        binding.tvTimeEnd.setOnClickListener {
            showTimePickerSheet(
                title = "End Time",
                point = endPoint
            ) { selectedPoint ->
                endPoint = selectedPoint
                renderTimeRows()
                updateGraph()
            }
        }

        binding.repeatEvery.setOnClickListener {
            selectedRepeatMode = RepeatMode.EVERY
            selectedCustomDays = setOf(1, 2, 3, 4, 5, 6, 7)
            renderRepeatMode()
        }

        binding.repeatWeekdays.setOnClickListener {
            selectedRepeatMode = RepeatMode.WEEK
            selectedCustomDays = setOf(1, 2, 3, 4, 5)
            renderRepeatMode()
        }

        binding.repeatWeekend.setOnClickListener {
            selectedRepeatMode = RepeatMode.WEEKEND
            selectedCustomDays = setOf(6, 7)
            renderRepeatMode()
        }

        binding.repeatCustom.setOnClickListener {
            LightCustomDaysSheet
                .create(requireContext())
                .show(
                    selectedDays = selectedCustomDays
                ) { days ->
                    selectedCustomDays = days
                    selectedRepeatMode = RepeatMode.CUSTOM
                    renderRepeatMode()
                }
        }

        binding.actionMoonlight.root.setOnClickListener {
            LightMoonlightSheet
                .create(requireContext())
                .show(
                    initialSettings = moonlightSettings
                ) { settings ->
                    moonlightSettings = settings
                    renderMoonlightSummary()
                }
        }

        binding.actionCloudSimulation.root.setOnClickListener {
            LightCloudSimulationSheet
                .create(requireContext())
                .show(
                    initialSettings = cloudSimulationSettings
                ) { settings ->
                    cloudSimulationSettings = settings
                    renderCloudSimulationSummary()
                }
        }

        binding.actionTransitionSmoothing.root.setOnClickListener {
            LightTransitionVariantSheet
                .create(requireContext())
                .show(
                    initialMode = selectedTransitionMode
                ) { mode ->
                    selectedTransitionMode = mode
                    renderTransitionSummary()
                    updateGraph()
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

    private fun renderTimeRows() {
        binding.tvTimeStartValue.text = startPoint.label
        binding.tvTimePeakStartValue.text = peakStartPoint.label
        binding.tvTimePeakEndValue.text = peakEndPoint.label
        binding.tvTimeEndValue.text = endPoint.label
    }

    private fun setupSliders() {
        binding.sliderRed.addOnChangeListener { _, value, _ ->
            binding.tvRedValue.text = "${value.toInt()}%"
            updateGraph()
        }

        binding.sliderGreen.addOnChangeListener { _, value, _ ->
            binding.tvGreenValue.text = "${value.toInt()}%"
            updateGraph()
        }

        binding.sliderBlue.addOnChangeListener { _, value, _ ->
            binding.tvBlueValue.text = "${value.toInt()}%"
            updateGraph()
        }

        binding.sliderWhite.addOnChangeListener { _, value, _ ->
            binding.tvWhiteValue.text = "${value.toInt()}%"
            updateGraph()
        }
    }

    private fun renderSliderValues() {
        binding.sliderRed.value = initialDraft.channelValues.red.toFloat()
        binding.sliderGreen.value = initialDraft.channelValues.green.toFloat()
        binding.sliderBlue.value = initialDraft.channelValues.blue.toFloat()
        binding.sliderWhite.value = initialDraft.channelValues.white.toFloat()

        binding.tvRedValue.text = "${initialDraft.channelValues.red}%"
        binding.tvGreenValue.text = "${initialDraft.channelValues.green}%"
        binding.tvBlueValue.text = "${initialDraft.channelValues.blue}%"
        binding.tvWhiteValue.text = "${initialDraft.channelValues.white}%"
    }

    private fun buildCurrentGraphState(): LightCurveGraphState {
        return LightCurveGraphState(
            start = startPoint,
            peakStart = peakStartPoint,
            peakEnd = peakEndPoint,
            end = endPoint,
            channelValues = currentChannelValues(),
            currentTime = currentDeviceTimePoint,
            transitionMode = selectedTransitionMode
        )
    }

    private fun buildCurrentProgramDraft(): LightProgramDraft {
        return LightProgramDraft(
            start = startPoint,
            peakStart = peakStartPoint,
            peakEnd = peakEndPoint,
            channelValues = currentChannelValues(),
            repeatMode = selectedRepeatMode,
            selectedDays = selectedCustomDays,
            moonlightSettings = moonlightSettings,
            cloudSimulationSettings = cloudSimulationSettings,
            transitionMode = selectedTransitionMode,
            end = endPoint
        )
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
            val draft = buildCurrentProgramDraft()

            val savedProgram = LightProgramDraftMapper.toSavedProgram(
                draft = draft,
                name = name,
                isActive = isActive
            )

            if (isActive) {
                // TODO: Save program and load savedProgram to ESP32 as active.
            } else {
                // TODO: Save program only as inactive.
            }

            Toast.makeText(
                requireContext(),
                if (isActive) "Program loaded to device" else "Program saved",
                Toast.LENGTH_SHORT
            ).show()

            findNavController().popBackStack()
        }
}

    private fun currentChannelValues(): LightCurveChannelValues {
        return LightCurveChannelValues(
            red = binding.sliderRed.value.toInt(),
            green = binding.sliderGreen.value.toInt(),
            blue = binding.sliderBlue.value.toInt(),
            white = binding.sliderWhite.value.toInt()
        )
    }

    private fun updateGraph() {
        binding.lightCurveGraphView.setState(
            buildCurrentGraphState()
        )
    }

    private fun updateDeviceTime(
        hour: Int,
        minute: Int
    ) {
        currentDeviceTimePoint = LightCurvePoint.of(hour, minute)
        updateGraph()
    }

    private fun renderRepeatMode() {
        val selectedBg = R.drawable.bg_light_filter_selected
        val transparentBg = android.R.color.transparent

        val selectedText = requireContext().getColor(R.color.light_button_on_primary)
        val normalText = requireContext().getColor(R.color.light_text_secondary)

        binding.repeatEvery.setBackgroundResource(
            if (selectedRepeatMode == RepeatMode.EVERY) selectedBg else transparentBg
        )
        binding.repeatWeekdays.setBackgroundResource(
            if (selectedRepeatMode == RepeatMode.WEEK) selectedBg else transparentBg
        )
        binding.repeatWeekend.setBackgroundResource(
            if (selectedRepeatMode == RepeatMode.WEEKEND) selectedBg else transparentBg
        )
        binding.repeatCustom.setBackgroundResource(
            if (selectedRepeatMode == RepeatMode.CUSTOM) selectedBg else transparentBg
        )

        binding.repeatEvery.setTextColor(
            if (selectedRepeatMode == RepeatMode.EVERY) selectedText else normalText
        )
        binding.repeatWeekdays.setTextColor(
            if (selectedRepeatMode == RepeatMode.WEEK) selectedText else normalText
        )
        binding.repeatWeekend.setTextColor(
            if (selectedRepeatMode == RepeatMode.WEEKEND) selectedText else normalText
        )
        binding.repeatCustom.setTextColor(
            if (selectedRepeatMode == RepeatMode.CUSTOM) selectedText else normalText
        )
    }

    private fun renderMoonlightSummary() {
        val subtitle = if (moonlightSettings.enabled) {
            val channelText = when (moonlightSettings.channel) {
                MoonlightChannel.BLUE -> "Blue"
                MoonlightChannel.WHITE -> "White"
                MoonlightChannel.BLUE_WHITE -> "Blue + White"
            }

            "$channelText • ${moonlightSettings.intensityPercent}% • Until ${moonlightSettings.endTime.label}"
        } else {
            "Soft output after sunset"
        }

        binding.actionMoonlight.root
            .findViewById<TextView>(R.id.tvActionSubtitle)
            ?.text = subtitle
    }

    private fun renderCloudSimulationSummary() {
        val subtitle = if (cloudSimulationSettings.enabled) {
            val frequencyText = when (cloudSimulationSettings.frequency) {
                CloudFrequency.RARE -> "Rare"
                CloudFrequency.NORMAL -> "Normal"
                CloudFrequency.FREQUENT -> "Frequent"
            }

            "Coverage ${cloudSimulationSettings.coveragePercent}% • $frequencyText"
        } else {
            "Natural light variation"
        }

        binding.actionCloudSimulation.root
            .findViewById<TextView>(R.id.tvActionSubtitle)
            ?.text = subtitle
    }

    private fun renderTransitionSummary() {
        val subtitle = when (selectedTransitionMode) {
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