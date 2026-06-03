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
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.calculator.LightCurveStatsCalculator
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveGraphState
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCurveTimePickerSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCustomDaysSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.MoonlightSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightMoonlightSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.MoonlightChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CloudFrequency
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CloudSimulationSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightCloudSimulationSheet

class DeviceLightProgramEditorFragment :
Fragment(R.layout.fragment_device_light_program_editor) {

    private var _binding: FragmentDeviceLightProgramEditorBinding? = null
    private val binding get() = _binding!!

    private var startPoint = LightCurvePoint.of(7, 0)
    private var peakStartPoint = LightCurvePoint.of(9, 0)
    private var peakEndPoint = LightCurvePoint.of(17, 0)
    private var endPoint = LightCurvePoint.of(20, 0)
    private var selectedRepeatMode = RepeatMode.EVERY
    private var selectedCustomDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)
	private var moonlightSettings = MoonlightSettings()
	private var cloudSimulationSettings = CloudSimulationSettings()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightProgramEditorBinding.bind(view)

        setupHeader()
        renderTimeRows()
        setupGraph()
        setupProgramSettingsRows()
		renderCloudSimulationSummary()
		renderMoonlightSummary()
        setupClicks()
        setupSliders()
        renderRepeatMode()
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

    private fun setupGraph() {
        updateGraph()
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
            title = "Transition Smoothing",
            subtitle = "Make ramps feel more natural"
        )

        bindActionRow(
            row = binding.actionNaturalVariation.root,
            icon = "✦",
            title = "Natural Variation",
            subtitle = "Subtle randomized daily output"
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
            Toast.makeText(requireContext(), "Preview program", Toast.LENGTH_SHORT).show()
        }

        binding.tvTimeStart.setOnClickListener {
            showTimePickerSheet(
                title = "Start Time",
                point = startPoint
            ) {
                selectedPoint ->
                startPoint = selectedPoint
                renderTimeRows()
                updateGraph()
            }
        }

        binding.tvTimePeakStart.setOnClickListener {
            showTimePickerSheet(
                title = "Peak Start Time",
                point = peakStartPoint
            ) {
                selectedPoint ->
                peakStartPoint = selectedPoint
                renderTimeRows()
                updateGraph()
            }
        }

        binding.tvTimePeakEnd.setOnClickListener {
            showTimePickerSheet(
                title = "Peak End Time",
                point = peakEndPoint
            ) {
                selectedPoint ->
                peakEndPoint = selectedPoint
                renderTimeRows()
                updateGraph()
            }
        }

        binding.tvTimeEnd.setOnClickListener {
            showTimePickerSheet(
                title = "End Time",
                point = endPoint
            ) {
                selectedPoint ->
                endPoint = selectedPoint
                renderTimeRows()
                updateGraph()
            }
        }

        binding.repeatEvery.setOnClickListener {
            selectedRepeatMode = RepeatMode.EVERY
            renderRepeatMode()
        }

        binding.repeatWeekdays.setOnClickListener {
            selectedRepeatMode = RepeatMode.WEEK
            renderRepeatMode()
        }

        binding.repeatWeekend.setOnClickListener {
            selectedRepeatMode = RepeatMode.WEEKEND
            renderRepeatMode()
        }

        binding.repeatCustom.setOnClickListener {
            LightCustomDaysSheet
            .create(requireContext())
            .show(
                selectedDays = selectedCustomDays
            ) {
                days ->
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
            Toast.makeText(requireContext(), "Transition Smoothing", Toast.LENGTH_SHORT).show()
        }

        binding.actionNaturalVariation.root.setOnClickListener {
            Toast.makeText(requireContext(), "Natural Variation", Toast.LENGTH_SHORT).show()
        }

        binding.btnLoadToDevice.setOnClickListener {
            Toast.makeText(requireContext(), "Load to device", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        binding.btnSaveAs.setOnClickListener {
            Toast.makeText(requireContext(), "Save as", Toast.LENGTH_SHORT).show()
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
        ) {
            hour, minute ->
            onSelected(LightCurvePoint.of(hour, minute))
        }
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

    private fun renderTimeRows() {
        binding.tvTimeStartValue.text = startPoint.label
        binding.tvTimePeakStartValue.text = peakStartPoint.label
        binding.tvTimePeakEndValue.text = peakEndPoint.label
        binding.tvTimeEndValue.text = endPoint.label
    }

    private fun buildCurrentGraphState(): LightCurveGraphState {
        return LightCurveGraphState(
            start = startPoint,
            peakStart = peakStartPoint,
            peakEnd = peakEndPoint,
            end = endPoint,
            channelValues = LightCurveChannelValues(
                red = binding.sliderRed.value.toInt(),
                green = binding.sliderGreen.value.toInt(),
                blue = binding.sliderBlue.value.toInt(),
                white = binding.sliderWhite.value.toInt()
            ),
            currentTime = LightCurvePoint.of(13, 28)
        )
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

    private fun updateGraph() {
        val state = buildCurrentGraphState()
        binding.lightCurveGraphView.setState(state)
        renderGraphStats(state)
    }

    private fun renderGraphStats(state: LightCurveGraphState) {
        val stats = LightCurveStatsCalculator.calculate(state)

        binding.tvCurveOutput.text = "${stats.outputPercent}%"
        binding.tvCurvePower.text = "%.1fW".format(stats.estimatedPowerWatts)
        binding.tvCurveDuration.text = formatDuration(stats.durationHours)
    }

    private fun formatDuration(hours: Double): String {
        val wholeHours = hours.toInt()
        val minutes = ((hours - wholeHours) * 60).toInt()

        return if (minutes == 0) {
            "${wholeHours}h"
        } else {
            "${wholeHours}h ${minutes}m"
        }
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

    private fun setupSliders() {
        binding.sliderRed.addOnChangeListener {
            _, value, _ ->
            binding.tvRedValue.text = "${value.toInt()}%"
            updateGraph()
        }

        binding.sliderGreen.addOnChangeListener {
            _, value, _ ->
            binding.tvGreenValue.text = "${value.toInt()}%"
            updateGraph()
        }

        binding.sliderBlue.addOnChangeListener {
            _, value, _ ->
            binding.tvBlueValue.text = "${value.toInt()}%"
            updateGraph()
        }

        binding.sliderWhite.addOnChangeListener {
            _, value, _ ->
            binding.tvWhiteValue.text = "${value.toInt()}%"
            updateGraph()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}