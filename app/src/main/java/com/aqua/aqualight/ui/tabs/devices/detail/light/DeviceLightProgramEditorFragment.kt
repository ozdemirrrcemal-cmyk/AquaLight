package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightProgramEditorBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_DEVICE_ID
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_DEVICE_TITLE
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_PROGRAM_ID
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_PROGRAM_NAME
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightRepeatDay
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramEditorMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramRampSmoothing
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramRepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightAcclimationBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightAcclimationSheetState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightDayPickerBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightPointEditorBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightPointEditorSheetModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet.LightPreviewDayBottomSheet
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class DeviceLightProgramEditorFragment :
    Fragment(R.layout.fragment_device_light_program_editor) {

    private var _binding: FragmentDeviceLightProgramEditorBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceTitle: String
        get() = requireArguments()
            .getString(ARG_DEVICE_TITLE)
            .orEmpty()
            .ifBlank {
                getString(R.string.light_default_device_title)
            }

    private val programId: String?
        get() = requireArguments().getString(ARG_PROGRAM_ID)

    private val programName: String
        get() = requireArguments()
            .getString(ARG_PROGRAM_NAME)
            .orEmpty()
            .ifBlank {
                getString(R.string.light_program_new_program)
            }

    private var editorMode: ProgramEditorMode = ProgramEditorMode.SIMPLE
    private var selectedProChannel: ProChannelUi = ProChannelUi.RED
    private var selectedRepeatMode: ProgramRepeatMode = ProgramRepeatMode.EVERY_DAY
    private var selectedRampSmoothing: ProgramRampSmoothing = ProgramRampSmoothing.LINEAR
    private var selectedRepeatDays: Set<LightRepeatDay> = LightRepeatDay.everyDay()
    private var acclimationState: LightAcclimationSheetState = LightAcclimationSheetState()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightProgramEditorBinding.bind(view)

        setupHeader()
        configureSliders()
        setupSliderListeners()
        setupClicks()
        renderInitialUi()
    }

    private fun setupHeader() = with(binding.deviceHeader) {
        tvTitle.text = programName

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        headerActionsContainer.visibility = View.GONE
        btnActionOne.visibility = View.GONE
        btnActionTwo.visibility = View.GONE
        btnActionThree.visibility = View.GONE
    }

    private fun configureSliders() = with(binding) {
        listOf(
            sliderProgramRed,
            sliderProgramGreen,
            sliderProgramBlue,
            sliderProgramWhite
        ).forEach { slider ->
            slider.valueFrom = MIN_PERCENT.toFloat()
            slider.valueTo = MAX_PERCENT.toFloat()
            slider.stepSize = SLIDER_STEP_SIZE
            slider.setLabelFormatter { value ->
                getString(
                    R.string.common_percent_value,
                    value.roundToInt()
                )
            }
        }
    }

    private fun setupSliderListeners() = with(binding) {
        bindChannelSlider(
            slider = sliderProgramRed,
            valueView = tvProgramRedValue,
            channelLabelRes = R.string.light_channel_red
        )

        bindChannelSlider(
            slider = sliderProgramGreen,
            valueView = tvProgramGreenValue,
            channelLabelRes = R.string.light_channel_green
        )

        bindChannelSlider(
            slider = sliderProgramBlue,
            valueView = tvProgramBlueValue,
            channelLabelRes = R.string.light_channel_blue
        )

        bindChannelSlider(
            slider = sliderProgramWhite,
            valueView = tvProgramWhiteValue,
            channelLabelRes = R.string.light_channel_white
        )
    }

    private fun bindChannelSlider(
        slider: Slider,
        valueView: TextView,
        channelLabelRes: Int
    ) {
        slider.addOnChangeListener { _, value, _ ->
            valueView.text = getString(
                R.string.light_channel_value_format,
                getString(channelLabelRes),
                value.roundToInt()
            )

            // TODO: Update local editor draft when editor state layer is enabled.
        }
    }

    private fun setupClicks() = with(binding) {
        btnSimpleMode.setOnClickListener {
            setEditorMode(ProgramEditorMode.SIMPLE)
        }

        btnProMode.setOnClickListener {
            setEditorMode(ProgramEditorMode.PRO)
        }

        chipProRed.setOnClickListener {
            selectProChannel(ProChannelUi.RED)
        }

        chipProGreen.setOnClickListener {
            selectProChannel(ProChannelUi.GREEN)
        }

        chipProBlue.setOnClickListener {
            selectProChannel(ProChannelUi.BLUE)
        }

        chipProWhite.setOnClickListener {
            selectProChannel(ProChannelUi.WHITE)
        }

        btnAddCurvePoint.setOnClickListener {
            showAddPointSheet()
        }

        viewProgramEditorCurve.setOnClickListener {
            showEditPointSheet()
        }

        rowPointStart.setOnClickListener {
            showEditPointSheet()
        }

        rowPointPeakStart.setOnClickListener {
            showEditPointSheet()
        }

        rowPointPeakEnd.setOnClickListener {
            showEditPointSheet()
        }

        rowPointEnd.setOnClickListener {
            showEditPointSheet()
        }

        chipRepeatEveryDay.setOnClickListener {
            selectedRepeatDays = LightRepeatDay.everyDay()
            setRepeatMode(ProgramRepeatMode.EVERY_DAY)
        }

        chipRepeatWeekdays.setOnClickListener {
            selectedRepeatDays = LightRepeatDay.weekdays()
            setRepeatMode(ProgramRepeatMode.WEEKDAYS)
        }

        chipRepeatWeekend.setOnClickListener {
            selectedRepeatDays = LightRepeatDay.weekend()
            setRepeatMode(ProgramRepeatMode.WEEKEND)
        }

        chipRepeatCustom.setOnClickListener {
            showCustomDayPickerSheet()
        }

        chipRampLinear.setOnClickListener {
            setRampSmoothing(ProgramRampSmoothing.LINEAR)
        }

        chipRampSoft.setOnClickListener {
            setRampSmoothing(ProgramRampSmoothing.SOFT)
        }

        chipRampNatural.setOnClickListener {
            setRampSmoothing(ProgramRampSmoothing.NATURAL)
        }

        rowAcclimation.setOnClickListener {
            showAcclimationSheet()
        }

        btnPreviewDay.setOnClickListener {
            LightPreviewDayBottomSheet.show(
                fragment = this@DeviceLightProgramEditorFragment
            )
        }

        btnSaveProgram.setOnClickListener {
            // TODO: Validate and save program through ViewModel when program data layer is enabled.
            findNavController().popBackStack()
        }
    }

    private fun renderInitialUi() {
        clearDynamicText()
        setEditorMode(ProgramEditorMode.SIMPLE)
        setRepeatMode(ProgramRepeatMode.EVERY_DAY)
        setRampSmoothing(ProgramRampSmoothing.LINEAR)
        renderAcclimationValue()
    }

    private fun clearDynamicText() = with(binding) {
        tvCurveStartSummary.text = ""
        tvCurvePeakSummary.text = ""
        tvCurveEndSummary.text = ""

        tvPointStartTime.text = ""
        tvPointStartLabel.text = ""
        tvPointStartPercent.text = ""

        tvPointPeakStartTime.text = ""
        tvPointPeakStartLabel.text = ""
        tvPointPeakStartPercent.text = ""

        tvPointPeakEndTime.text = ""
        tvPointPeakEndLabel.text = ""
        tvPointPeakEndPercent.text = ""

        tvPointEndTime.text = ""
        tvPointEndLabel.text = ""
        tvPointEndPercent.text = ""

        tvProgramRedValue.text = ""
        tvProgramGreenValue.text = ""
        tvProgramBlueValue.text = ""
        tvProgramWhiteValue.text = ""

        tvCurveHint.text = ""
        tvCurveHint.visibility = View.GONE

        viewProgramEditorCurve.clear()
    }

    private fun setEditorMode(
        mode: ProgramEditorMode
    ) = with(binding) {
        editorMode = mode

        applyTextChipState(
            chip = btnSimpleMode,
            selected = mode == ProgramEditorMode.SIMPLE
        )

        applyTextChipState(
            chip = btnProMode,
            selected = mode == ProgramEditorMode.PRO
        )

        when (mode) {
            ProgramEditorMode.SIMPLE -> {
                proChannelSelectorRow.visibility = View.GONE
                cardProgramChannelBalance.visibility = View.VISIBLE

                tvEditorModeDescription.setText(
                    R.string.light_editor_mode_simple_description
                )

                tvCurveTitle.setText(
                    R.string.light_editor_curve_title_simple
                )

                tvCurveSubtitle.setText(
                    R.string.light_editor_curve_subtitle_simple
                )

                tvCurvePointsSubtitle.setText(
                    R.string.light_editor_curve_points_subtitle_simple
                )

                tvChannelBalanceTitle.setText(
                    R.string.light_editor_channel_balance_title_simple
                )

                tvChannelBalanceSubtitle.setText(
                    R.string.light_editor_channel_balance_subtitle_simple
                )

                // TODO: Submit SIMPLE curve data to LightCurveChartView when editor state is available.
                viewProgramEditorCurve.clear()
            }

            ProgramEditorMode.PRO -> {
                proChannelSelectorRow.visibility = View.VISIBLE
                cardProgramChannelBalance.visibility = View.GONE

                tvEditorModeDescription.setText(
                    R.string.light_editor_mode_pro_description
                )

                tvCurvePointsSubtitle.setText(
                    R.string.light_editor_curve_points_subtitle_pro
                )

                selectProChannel(selectedProChannel)
            }
        }
    }

    private fun selectProChannel(
        channel: ProChannelUi
    ) = with(binding) {
        selectedProChannel = channel

        tvCurveTitle.text = getString(
            R.string.light_editor_curve_title_pro_channel,
            getString(channel.labelRes)
        )

        tvCurveSubtitle.text = getString(
            R.string.light_editor_curve_subtitle_pro_channel,
            getString(channel.labelRes).lowercase()
        )

        renderProChannelChips()

        // TODO: Submit selected PRO channel curve data to LightCurveChartView when editor state is available.
        viewProgramEditorCurve.clear()
    }

    private fun renderProChannelChips() = with(binding) {
        chipProRed.applyProChannelStyle(ProChannelUi.RED)
        chipProGreen.applyProChannelStyle(ProChannelUi.GREEN)
        chipProBlue.applyProChannelStyle(ProChannelUi.BLUE)
        chipProWhite.applyProChannelStyle(ProChannelUi.WHITE)
    }

    private fun setRepeatMode(
        mode: ProgramRepeatMode
    ) {
        selectedRepeatMode = mode
        renderRepeatChips()
    }

    private fun renderRepeatChips() = with(binding) {
        applyTextChipState(
            chip = chipRepeatEveryDay,
            selected = selectedRepeatMode == ProgramRepeatMode.EVERY_DAY
        )

        applyTextChipState(
            chip = chipRepeatWeekdays,
            selected = selectedRepeatMode == ProgramRepeatMode.WEEKDAYS
        )

        applyTextChipState(
            chip = chipRepeatWeekend,
            selected = selectedRepeatMode == ProgramRepeatMode.WEEKEND
        )

        applyTextChipState(
            chip = chipRepeatCustom,
            selected = selectedRepeatMode == ProgramRepeatMode.CUSTOM
        )
    }

    private fun setRampSmoothing(
        smoothing: ProgramRampSmoothing
    ) {
        selectedRampSmoothing = smoothing
        renderRampSmoothingChips()

        // TODO: Re-render curve with smoothing mode when curve data exists.
    }

    private fun renderRampSmoothingChips() = with(binding) {
        applyTextChipState(
            chip = chipRampLinear,
            selected = selectedRampSmoothing == ProgramRampSmoothing.LINEAR
        )

        applyTextChipState(
            chip = chipRampSoft,
            selected = selectedRampSmoothing == ProgramRampSmoothing.SOFT
        )

        applyTextChipState(
            chip = chipRampNatural,
            selected = selectedRampSmoothing == ProgramRampSmoothing.NATURAL
        )
    }

    private fun showCustomDayPickerSheet() {
        LightDayPickerBottomSheet.show(
            fragment = this,
            initialDays = selectedRepeatDays,
            onSave = { days ->
                selectedRepeatDays = days
                selectedRepeatMode = ProgramRepeatMode.CUSTOM
                renderRepeatChips()

                // TODO: Store selected repeat days in editor draft when state layer is enabled.
            }
        )
    }

    private fun showAcclimationSheet() {
        LightAcclimationBottomSheet.show(
            fragment = this,
            initialState = acclimationState,
            onSave = { state ->
                acclimationState = state
                renderAcclimationValue()

                // TODO: Store acclimation settings in editor draft when state layer is enabled.
            }
        )
    }

    private fun renderAcclimationValue() = with(binding) {
        tvAcclimationValue.text =
            if (acclimationState.enabled) {
                getString(
                    R.string.light_acclimation_value_on,
                    acclimationState.durationDays,
                    acclimationState.startIntensityPercent
                )
            } else {
                getString(R.string.light_acclimation_value_off)
            }
    }

    private fun showEditPointSheet() {
        LightPointEditorBottomSheet.show(
            fragment = this,
            model = LightPointEditorSheetModel(
                titleRes = R.string.light_point_editor_title_edit,
                descriptionRes = R.string.light_point_editor_description_default,
                saveButtonTextRes = R.string.light_point_editor_save,
                pointName = "",
                timeLabel = "",
                intensityPercent = null,
                canRename = false,
                canDelete = false
            ),
            onSave = { _, _, _ ->
                // TODO: Update selected curve point when editor state layer is enabled.
            },
            onDelete = {
                // No-op for default/static placeholder point.
            }
        )
    }

    private fun showAddPointSheet() {
        LightPointEditorBottomSheet.show(
            fragment = this,
            model = LightPointEditorSheetModel(
                titleRes = R.string.light_point_editor_title_add,
                descriptionRes = R.string.light_point_editor_description_add,
                saveButtonTextRes = R.string.light_point_editor_add,
                pointName = "",
                timeLabel = "",
                intensityPercent = null,
                canRename = true,
                canDelete = false
            ),
            onSave = { _, _, _ ->
                // TODO: Add custom curve point when editor state layer is enabled.
            },
            onDelete = {
                // No-op.
            }
        )
    }

    private fun applyTextChipState(
        chip: TextView,
        selected: Boolean
    ) {
        chip.setBackgroundResource(
            if (selected) {
                R.drawable.bg_light_editor_chip_selected
            } else {
                R.drawable.bg_light_editor_chip_unselected
            }
        )

        chip.setTextColor(
            color(
                if (selected) {
                    R.color.background_color
                } else {
                    R.color.settings_text_secondary
                }
            )
        )
    }

    private fun MaterialCardView.applyProChannelStyle(
        channel: ProChannelUi
    ) {
        val selected = selectedProChannel == channel

        setCardBackgroundColor(
            color(
                if (selected) {
                    R.color.light_accent_soft
                } else {
                    R.color.light_surface
                }
            )
        )

        strokeColor =
            color(
                if (selected) {
                    channel.colorRes
                } else {
                    R.color.light_stroke
                }
            )

        val textView = getChildAt(0) as? TextView

        textView?.setTextColor(
            color(
                if (selected) {
                    channel.colorRes
                } else {
                    R.color.settings_text_secondary
                }
            )
        )
    }

    private fun color(
        @ColorRes colorRes: Int
    ): Int {
        return requireContext().getColor(colorRes)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private enum class ProChannelUi(
        val labelRes: Int,
        @ColorRes val colorRes: Int
    ) {
        RED(
            labelRes = R.string.light_channel_red,
            colorRes = R.color.light_red
        ),

        GREEN(
            labelRes = R.string.light_channel_green,
            colorRes = R.color.light_green
        ),

        BLUE(
            labelRes = R.string.light_channel_blue,
            colorRes = R.color.light_blue
        ),

        WHITE(
            labelRes = R.string.light_channel_white,
            colorRes = R.color.light_white
        )
    }

    companion object {
        private const val MIN_PERCENT = 0
        private const val MAX_PERCENT = 100
        private const val SLIDER_STEP_SIZE = 1f
    }
}