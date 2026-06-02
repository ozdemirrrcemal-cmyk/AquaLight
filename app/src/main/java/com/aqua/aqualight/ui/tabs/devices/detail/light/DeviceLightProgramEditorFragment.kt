package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
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
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.GeneratedQuickSetupCurvePointKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.GeneratedQuickSetupProgramDraft
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChartData
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveSeries
import androidx.core.os.bundleOf
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.data.LightProgramDraftStore
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgramBalance
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgramCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgramCurvePointKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgramMode

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
    private var loadedSavedProgram: SavedLightProgram? = null

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
        ).forEach {
            slider ->
            slider.valueFrom = MIN_PERCENT.toFloat()
            slider.valueTo = MAX_PERCENT.toFloat()
            slider.stepSize = SLIDER_STEP_SIZE
            slider.setLabelFormatter {
                value ->
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
        slider.addOnChangeListener {
            _, value, _ ->
            valueView.text =
            getString(
                R.string.light_channel_value_format,
                getString(channelLabelRes),
                value.roundToInt()
            )

            // TODO: Update local editor draft when editor state layer is enabled.
        }
    }

    private fun setupClicks() = with(binding) {
        btnSimpleMode.setOnClickListener {
            setEditorMode(
                mode = ProgramEditorMode.SIMPLE
            )
        }

        btnProMode.setOnClickListener {
            setEditorMode(
                mode = ProgramEditorMode.PRO
            )
        }

        chipProRed.setOnClickListener {
            selectProChannel(
                channel = ProChannelUi.RED
            )
        }

        chipProGreen.setOnClickListener {
            selectProChannel(
                channel = ProChannelUi.GREEN
            )
        }

        chipProBlue.setOnClickListener {
            selectProChannel(
                channel = ProChannelUi.BLUE
            )
        }

        chipProWhite.setOnClickListener {
            selectProChannel(
                channel = ProChannelUi.WHITE
            )
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

            setRepeatMode(
                mode = ProgramRepeatMode.EVERY_DAY
            )
        }

        chipRepeatWeekdays.setOnClickListener {
            selectedRepeatDays = LightRepeatDay.weekdays()

            setRepeatMode(
                mode = ProgramRepeatMode.WEEKDAYS
            )
        }

        chipRepeatWeekend.setOnClickListener {
            selectedRepeatDays = LightRepeatDay.weekend()

            setRepeatMode(
                mode = ProgramRepeatMode.WEEKEND
            )
        }

        chipRepeatCustom.setOnClickListener {
            showCustomDayPickerSheet()
        }

        chipRampLinear.setOnClickListener {
            setRampSmoothing(
                smoothing = ProgramRampSmoothing.LINEAR
            )
        }

        chipRampSoft.setOnClickListener {
            setRampSmoothing(
                smoothing = ProgramRampSmoothing.SOFT
            )
        }

        chipRampNatural.setOnClickListener {
            setRampSmoothing(
                smoothing = ProgramRampSmoothing.NATURAL
            )
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
            saveProgram()
        }
    }

    private fun renderInitialUi() {
        clearDynamicText()

        val generatedDraft = quickSetupGeneratedDraft()

        if (generatedDraft != null) {
            applyQuickSetupGeneratedDraft(
                generatedDraft = generatedDraft
            )

            return
        }

        val savedProgram = savedProgramFromStore()

        if (savedProgram != null) {
            applySavedLightProgram(
                savedProgram = savedProgram
            )

            return
        }

        setEditorMode(
            mode = ProgramEditorMode.SIMPLE
        )

        setRepeatMode(
            mode = ProgramRepeatMode.EVERY_DAY
        )

        setRampSmoothing(
            smoothing = ProgramRampSmoothing.LINEAR
        )

        renderAcclimationValue()
    }


    @Suppress("DEPRECATION")
    private fun quickSetupGeneratedDraft(): GeneratedQuickSetupProgramDraft? {
        return requireArguments()
        .getSerializable(
            GeneratedQuickSetupProgramDraft.ARG_QUICK_SETUP_GENERATED_DRAFT
        ) as? GeneratedQuickSetupProgramDraft
    }

    private fun savedProgramFromStore(): SavedLightProgram? {
        val id = programId ?: return null

        return LightProgramDraftStore.getProgram(
            deviceId = deviceId,
            programId = id
        )
    }

    private fun applySavedLightProgram(
        savedProgram: SavedLightProgram
    ) {
        loadedSavedProgram = savedProgram

        binding.deviceHeader.tvTitle.text = savedProgram.title

        setEditorMode(
            mode =
            when (savedProgram.mode) {
                SavedLightProgramMode.SIMPLE -> {
                    ProgramEditorMode.SIMPLE
                }

                SavedLightProgramMode.PRO -> {
                    ProgramEditorMode.PRO
                }
            }
        )

        selectedRepeatDays =
        savedProgram.repeatDays.toEditorRepeatDays()

        selectedRepeatMode =
        selectedRepeatDays.toRepeatMode()

        renderRepeatChips()

        setRampSmoothing(
            smoothing = ProgramRampSmoothing.LINEAR
        )

        renderSavedProgramChannelBalance(
            savedProgram = savedProgram
        )

        renderSavedProgramCurvePoints(
            savedProgram = savedProgram
        )

        renderAcclimationValue()
    }

    private fun renderSavedProgramChannelBalance(
        savedProgram: SavedLightProgram
    ) = with(binding) {
        val balance = savedProgram.balance

        sliderProgramRed.value = balance.red
        .coerceIn(MIN_PERCENT, MAX_PERCENT)
        .toFloat()

        sliderProgramGreen.value = balance.green
        .coerceIn(MIN_PERCENT, MAX_PERCENT)
        .toFloat()

        sliderProgramBlue.value = balance.blue
        .coerceIn(MIN_PERCENT, MAX_PERCENT)
        .toFloat()

        sliderProgramWhite.value = balance.white
        .coerceIn(MIN_PERCENT, MAX_PERCENT)
        .toFloat()

        tvProgramRedValue.text =
        getString(
            R.string.light_channel_value_format,
            getString(R.string.light_channel_red),
            balance.red
        )

        tvProgramGreenValue.text =
        getString(
            R.string.light_channel_value_format,
            getString(R.string.light_channel_green),
            balance.green
        )

        tvProgramBlueValue.text =
        getString(
            R.string.light_channel_value_format,
            getString(R.string.light_channel_blue),
            balance.blue
        )

        tvProgramWhiteValue.text =
        getString(
            R.string.light_channel_value_format,
            getString(R.string.light_channel_white),
            balance.white
        )
    }

    private fun renderSavedProgramCurvePoints(
        savedProgram: SavedLightProgram
    ) = with(binding) {
        val sortedPoints =
        savedProgram.curvePoints
        .sortedBy {
            point ->
            point.minuteOfDay
        }

        val startPoint =
        sortedPoints.firstOrNull {
            point ->
            point.kind == SavedLightProgramCurvePointKind.START
        }

        val peakStartPoint =
        sortedPoints.firstOrNull {
            point ->
            point.kind == SavedLightProgramCurvePointKind.PEAK_START
        }

        val peakEndPoint =
        sortedPoints.firstOrNull {
            point ->
            point.kind == SavedLightProgramCurvePointKind.PEAK_END
        }

        val endPoint =
        sortedPoints.firstOrNull {
            point ->
            point.kind == SavedLightProgramCurvePointKind.END
        }

        if (
            startPoint == null ||
            peakStartPoint == null ||
            peakEndPoint == null ||
            endPoint == null
        ) {
            viewProgramEditorCurve.clear()
            return@with
        }

        tvCurveStartSummary.text =
        minutesToTime(
            minutes = startPoint.minuteOfDay
        )

        tvCurvePeakSummary.text =
        getString(
            R.string.common_percent_value,
            savedProgram.peakIntensityPercent
        )

        tvCurveEndSummary.text =
        minutesToTime(
            minutes = endPoint.minuteOfDay
        )

        tvPointStartTime.text =
        minutesToTime(
            minutes = startPoint.minuteOfDay
        )

        tvPointStartLabel.setText(
            R.string.light_editor_point_label_start
        )

        tvPointStartPercent.text =
        getString(
            R.string.common_percent_value,
            startPoint.masterPercent
        )

        tvPointPeakStartTime.text =
        minutesToTime(
            minutes = peakStartPoint.minuteOfDay
        )

        tvPointPeakStartLabel.setText(
            R.string.light_editor_point_label_peak_start
        )

        tvPointPeakStartPercent.text =
        getString(
            R.string.common_percent_value,
            peakStartPoint.masterPercent
        )

        tvPointPeakEndTime.text =
        minutesToTime(
            minutes = peakEndPoint.minuteOfDay
        )

        tvPointPeakEndLabel.setText(
            R.string.light_editor_point_label_peak_end
        )

        tvPointPeakEndPercent.text =
        getString(
            R.string.common_percent_value,
            peakEndPoint.masterPercent
        )

        tvPointEndTime.text =
        minutesToTime(
            minutes = endPoint.minuteOfDay
        )

        tvPointEndLabel.setText(
            R.string.light_editor_point_label_end
        )

        tvPointEndPercent.text =
        getString(
            R.string.common_percent_value,
            endPoint.masterPercent
        )

        viewProgramEditorCurve.submitData(
            data =
            LightCurveChartData(
                series =
                listOf(
                    LightCurveSeries(
                        channel = LightCurveChannel.MASTER,
                        isActive = true,
                        points =
                        sortedPoints.map {
                            point ->
                            LightCurvePoint(
                                minuteOfDay = point.minuteOfDay,
                                intensityPercent = point.masterPercent,
                                isMajor = point.kind != SavedLightProgramCurvePointKind.CUSTOM
                            )
                        }
                    )
                ),
                currentTimeMinutes = null
            )
        )
    }

    private fun applyQuickSetupGeneratedDraft(
        generatedDraft: GeneratedQuickSetupProgramDraft
    ) {
        setEditorMode(
            mode = ProgramEditorMode.SIMPLE
        )

        selectedRepeatDays =
        generatedDraft.repeatDays.toEditorRepeatDays()

        selectedRepeatMode =
        selectedRepeatDays.toRepeatMode()

        renderRepeatChips()

        setRampSmoothing(
            smoothing = ProgramRampSmoothing.LINEAR
        )

        renderQuickSetupChannelBalance(
            generatedDraft = generatedDraft
        )

        renderQuickSetupCurvePoints(
            generatedDraft = generatedDraft
        )

        renderAcclimationValue()
    }

    private fun renderQuickSetupChannelBalance(
        generatedDraft: GeneratedQuickSetupProgramDraft
    ) = with(binding) {
        val balance = generatedDraft.balance

        sliderProgramRed.value = balance.red.toFloat()
        sliderProgramGreen.value = balance.green.toFloat()
        sliderProgramBlue.value = balance.blue.toFloat()
        sliderProgramWhite.value = balance.white.toFloat()

        tvProgramRedValue.text =
        getString(
            R.string.light_channel_value_format,
            getString(R.string.light_channel_red),
            balance.red
        )

        tvProgramGreenValue.text =
        getString(
            R.string.light_channel_value_format,
            getString(R.string.light_channel_green),
            balance.green
        )

        tvProgramBlueValue.text =
        getString(
            R.string.light_channel_value_format,
            getString(R.string.light_channel_blue),
            balance.blue
        )

        tvProgramWhiteValue.text =
        getString(
            R.string.light_channel_value_format,
            getString(R.string.light_channel_white),
            balance.white
        )
    }

    private fun renderQuickSetupCurvePoints(
        generatedDraft: GeneratedQuickSetupProgramDraft
    ) = with(binding) {
        val startPoint =
        generatedDraft.curvePoints.firstOrNull {
            point ->
            point.kind == GeneratedQuickSetupCurvePointKind.START
        }

        val peakStartPoint =
        generatedDraft.curvePoints.firstOrNull {
            point ->
            point.kind == GeneratedQuickSetupCurvePointKind.PEAK_START
        }

        val peakEndPoint =
        generatedDraft.curvePoints.firstOrNull {
            point ->
            point.kind == GeneratedQuickSetupCurvePointKind.PEAK_END
        }

        val endPoint =
        generatedDraft.curvePoints.firstOrNull {
            point ->
            point.kind == GeneratedQuickSetupCurvePointKind.END
        }

        if (
            startPoint == null ||
            peakStartPoint == null ||
            peakEndPoint == null ||
            endPoint == null
        ) {
            viewProgramEditorCurve.clear()
            return@with
        }

        tvCurveStartSummary.text =
        minutesToTime(
            minutes = startPoint.timeMinutes
        )

        tvCurvePeakSummary.text =
        getString(
            R.string.common_percent_value,
            generatedDraft.peakIntensityPercent
        )

        tvCurveEndSummary.text =
        minutesToTime(
            minutes = endPoint.timeMinutes
        )

        tvPointStartTime.text =
        minutesToTime(
            minutes = startPoint.timeMinutes
        )

        tvPointStartLabel.setText(
            R.string.light_editor_point_label_start
        )

        tvPointStartPercent.text =
        getString(
            R.string.common_percent_value,
            startPoint.masterPercent
        )

        tvPointPeakStartTime.text =
        minutesToTime(
            minutes = peakStartPoint.timeMinutes
        )

        tvPointPeakStartLabel.setText(
            R.string.light_editor_point_label_peak_start
        )

        tvPointPeakStartPercent.text =
        getString(
            R.string.common_percent_value,
            peakStartPoint.masterPercent
        )

        tvPointPeakEndTime.text =
        minutesToTime(
            minutes = peakEndPoint.timeMinutes
        )

        tvPointPeakEndLabel.setText(
            R.string.light_editor_point_label_peak_end
        )

        tvPointPeakEndPercent.text =
        getString(
            R.string.common_percent_value,
            peakEndPoint.masterPercent
        )

        tvPointEndTime.text =
        minutesToTime(
            minutes = endPoint.timeMinutes
        )

        tvPointEndLabel.setText(
            R.string.light_editor_point_label_end
        )

        tvPointEndPercent.text =
        getString(
            R.string.common_percent_value,
            endPoint.masterPercent
        )

        viewProgramEditorCurve.submitData(
            LightCurveChartData(
                series =
                listOf(
                    LightCurveSeries(
                        channel = LightCurveChannel.MASTER,
                        isActive = true,
                        points =
                        listOf(
                            LightCurvePoint(
                                minuteOfDay = startPoint.timeMinutes,
                                intensityPercent = startPoint.masterPercent,
                                isMajor = true
                            ),
                            LightCurvePoint(
                                minuteOfDay = peakStartPoint.timeMinutes,
                                intensityPercent = peakStartPoint.masterPercent,
                                isMajor = true
                            ),
                            LightCurvePoint(
                                minuteOfDay = peakEndPoint.timeMinutes,
                                intensityPercent = peakEndPoint.masterPercent,
                                isMajor = true
                            ),
                            LightCurvePoint(
                                minuteOfDay = endPoint.timeMinutes,
                                intensityPercent = endPoint.masterPercent,
                                isMajor = true
                            )
                        )
                    )
                ),
                currentTimeMinutes = null
            )
        )
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

                selectProChannel(
                    channel = selectedProChannel
                )
            }
        }
    }

    private fun selectProChannel(
        channel: ProChannelUi
    ) = with(binding) {
        selectedProChannel = channel

        tvCurveTitle.text =
        getString(
            R.string.light_editor_curve_title_pro_channel,
            getString(channel.labelRes)
        )

        tvCurveSubtitle.text =
        getString(
            R.string.light_editor_curve_subtitle_pro_channel,
            getString(channel.labelRes).lowercase()
        )

        renderProChannelChips()

        viewProgramEditorCurve.clear()
    }

    private fun renderProChannelChips() = with(binding) {
        chipProRed.applyProChannelStyle(
            channel = ProChannelUi.RED
        )

        chipProGreen.applyProChannelStyle(
            channel = ProChannelUi.GREEN
        )

        chipProBlue.applyProChannelStyle(
            channel = ProChannelUi.BLUE
        )

        chipProWhite.applyProChannelStyle(
            channel = ProChannelUi.WHITE
        )
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
            onSave = {
                days ->
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
            onSave = {
                state ->
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
            getString(
                R.string.light_acclimation_value_off
            )
        }
    }

    private fun showEditPointSheet() {
        LightPointEditorBottomSheet.show(
            fragment = this,
            model =
            LightPointEditorSheetModel(
                titleRes = R.string.light_point_editor_title_edit,
                descriptionRes = R.string.light_point_editor_description_default,
                saveButtonTextRes = R.string.light_point_editor_save,
                pointName = "",
                timeLabel = "",
                intensityPercent = null,
                canRename = false,
                canDelete = false
            ),
            onSave = {
                _, _, _ ->
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
            model =
            LightPointEditorSheetModel(
                titleRes = R.string.light_point_editor_title_add,
                descriptionRes = R.string.light_point_editor_description_add,
                saveButtonTextRes = R.string.light_point_editor_add,
                pointName = "",
                timeLabel = "",
                intensityPercent = null,
                canRename = true,
                canDelete = false
            ),
            onSave = {
                _, _, _ ->
                // TODO: Add custom curve point when editor state layer is enabled.
            },
            onDelete = {
                // No-op.
            }
        )
    }

    private fun Set<Int>.toEditorRepeatDays(): Set<LightRepeatDay> {
        return mapNotNull {
            day ->
            when (day) {
                DAY_MON -> LightRepeatDay.MONDAY
                DAY_TUE -> LightRepeatDay.TUESDAY
                DAY_WED -> LightRepeatDay.WEDNESDAY
                DAY_THU -> LightRepeatDay.THURSDAY
                DAY_FRI -> LightRepeatDay.FRIDAY
                DAY_SAT -> LightRepeatDay.SATURDAY
                DAY_SUN -> LightRepeatDay.SUNDAY
                else -> null
            }
        }.toSet()
        .ifEmpty {
            LightRepeatDay.everyDay()
        }
    }

    private fun Set<LightRepeatDay>.toRepeatMode(): ProgramRepeatMode {
        return when (this) {
            LightRepeatDay.everyDay() -> ProgramRepeatMode.EVERY_DAY
            LightRepeatDay.weekdays() -> ProgramRepeatMode.WEEKDAYS
            LightRepeatDay.weekend() -> ProgramRepeatMode.WEEKEND
            else -> ProgramRepeatMode.CUSTOM
        }
    }

    private fun saveProgram() {
        val savedProgram =
        quickSetupGeneratedDraft()
        ?.toSavedLightProgram()
        ?: loadedSavedProgram
        ?.toUpdatedSavedProgramFromCurrentUi()
        ?: createDefaultSavedProgram()

        LightProgramDraftStore.upsertProgram(
            program = savedProgram
        )

        val navController = findNavController()

        val returnedToProgramList =
        navController.popBackStack(
            R.id.deviceLightProgramListFragment,
            false
        )

        if (!returnedToProgramList) {
            navController.navigate(
                R.id.deviceLightProgramListFragment,
                bundleOf(
                    ARG_DEVICE_ID to deviceId,
                    ARG_DEVICE_TITLE to deviceTitle
                )
            )
        }
    }

    private fun SavedLightProgram.toUpdatedSavedProgramFromCurrentUi(): SavedLightProgram {
        val balance =
        SavedLightProgramBalance(
            red = binding.sliderProgramRed.value
            .roundToInt()
            .coerceIn(MIN_PERCENT, MAX_PERCENT),
            green = binding.sliderProgramGreen.value
            .roundToInt()
            .coerceIn(MIN_PERCENT, MAX_PERCENT),
            blue = binding.sliderProgramBlue.value
            .roundToInt()
            .coerceIn(MIN_PERCENT, MAX_PERCENT),
            white = binding.sliderProgramWhite.value
            .roundToInt()
            .coerceIn(MIN_PERCENT, MAX_PERCENT)
        )

        return copy(
            title = programName,
            mode =
            when (editorMode) {
                ProgramEditorMode.SIMPLE -> {
                    SavedLightProgramMode.SIMPLE
                }

                ProgramEditorMode.PRO -> {
                    SavedLightProgramMode.PRO
                }
            },
            repeatDays = selectedRepeatDays.toSavedRepeatDays(),
            balance = balance,
            curvePoints =
            curvePoints.map {
                point ->
                val safeMasterPercent =
                point.masterPercent.coerceIn(
                    MIN_PERCENT,
                    MAX_PERCENT
                )

                point.copy(
                    red =
                    scaledChannelOutput(
                        channelPercent = balance.red,
                        masterPercent = safeMasterPercent
                    ),
                    green =
                    scaledChannelOutput(
                        channelPercent = balance.green,
                        masterPercent = safeMasterPercent
                    ),
                    blue =
                    scaledChannelOutput(
                        channelPercent = balance.blue,
                        masterPercent = safeMasterPercent
                    ),
                    white =
                    scaledChannelOutput(
                        channelPercent = balance.white,
                        masterPercent = safeMasterPercent
                    )
                )
            },
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    private fun GeneratedQuickSetupProgramDraft.toSavedLightProgram(): SavedLightProgram {
        return SavedLightProgram(
            id = programId ?: createProgramId(),
            deviceId = deviceId,
            title = programName,
            isEnabled = true,
            isActive = true,
            mode = SavedLightProgramMode.SIMPLE,
            repeatDays = repeatDays,
            rampMinutes = rampMinutes,
            peakIntensityPercent = peakIntensityPercent,
            balance =
            SavedLightProgramBalance(
                red = balance.red,
                green = balance.green,
                blue = balance.blue,
                white = balance.white
            ),
            curvePoints =
            curvePoints.map {
                point ->
                SavedLightProgramCurvePoint(
                    kind = point.kind.toSavedCurvePointKind(),
                    minuteOfDay = point.timeMinutes,
                    masterPercent = point.masterPercent,
                    red = point.channelOutput.red,
                    green = point.channelOutput.green,
                    blue = point.channelOutput.blue,
                    white = point.channelOutput.white
                )
            }
        )
    }

    private fun createDefaultSavedProgram(): SavedLightProgram {
        val balance =
        SavedLightProgramBalance(
            red =
            sliderValueOrDefault(
                value = binding.sliderProgramRed.value.roundToInt(),
                defaultValue = DEFAULT_RED
            ),
            green =
            sliderValueOrDefault(
                value = binding.sliderProgramGreen.value.roundToInt(),
                defaultValue = DEFAULT_GREEN
            ),
            blue =
            sliderValueOrDefault(
                value = binding.sliderProgramBlue.value.roundToInt(),
                defaultValue = DEFAULT_BLUE
            ),
            white =
            sliderValueOrDefault(
                value = binding.sliderProgramWhite.value.roundToInt(),
                defaultValue = DEFAULT_WHITE
            )
        )

        return SavedLightProgram(
            id = programId ?: createProgramId(),
            deviceId = deviceId,
            title = programName,
            isEnabled = true,
            isActive = true,
            mode = SavedLightProgramMode.SIMPLE,
            repeatDays = selectedRepeatDays.toSavedRepeatDays(),
            rampMinutes = DEFAULT_RAMP_MINUTES,
            peakIntensityPercent = MAX_PERCENT,
            balance = balance,
            curvePoints =
            listOf(
                createSavedCurvePoint(
                    kind = SavedLightProgramCurvePointKind.START,
                    minuteOfDay = DEFAULT_START_MINUTES,
                    masterPercent = MIN_PERCENT,
                    balance = balance
                ),
                createSavedCurvePoint(
                    kind = SavedLightProgramCurvePointKind.PEAK_START,
                    minuteOfDay = DEFAULT_PEAK_START_MINUTES,
                    masterPercent = MAX_PERCENT,
                    balance = balance
                ),
                createSavedCurvePoint(
                    kind = SavedLightProgramCurvePointKind.PEAK_END,
                    minuteOfDay = DEFAULT_PEAK_END_MINUTES,
                    masterPercent = MAX_PERCENT,
                    balance = balance
                ),
                createSavedCurvePoint(
                    kind = SavedLightProgramCurvePointKind.END,
                    minuteOfDay = DEFAULT_END_MINUTES,
                    masterPercent = MIN_PERCENT,
                    balance = balance
                )
            )
        )
    }

    private fun createSavedCurvePoint(
        kind: SavedLightProgramCurvePointKind,
        minuteOfDay: Int,
        masterPercent: Int,
        balance: SavedLightProgramBalance
    ): SavedLightProgramCurvePoint {
        val safeMasterPercent =
        masterPercent.coerceIn(
            MIN_PERCENT,
            MAX_PERCENT
        )

        return SavedLightProgramCurvePoint(
            kind = kind,
            minuteOfDay =
            minuteOfDay.coerceIn(
                MINUTES_IN_DAY_MIN,
                MINUTES_IN_DAY_MAX
            ),
            masterPercent = safeMasterPercent,
            red =
            scaledChannelOutput(
                channelPercent = balance.red,
                masterPercent = safeMasterPercent
            ),
            green =
            scaledChannelOutput(
                channelPercent = balance.green,
                masterPercent = safeMasterPercent
            ),
            blue =
            scaledChannelOutput(
                channelPercent = balance.blue,
                masterPercent = safeMasterPercent
            ),
            white =
            scaledChannelOutput(
                channelPercent = balance.white,
                masterPercent = safeMasterPercent
            )
        )
    }

    private fun GeneratedQuickSetupCurvePointKind.toSavedCurvePointKind(): SavedLightProgramCurvePointKind {
        return when (this) {
            GeneratedQuickSetupCurvePointKind.START -> {
                SavedLightProgramCurvePointKind.START
            }

            GeneratedQuickSetupCurvePointKind.PEAK_START -> {
                SavedLightProgramCurvePointKind.PEAK_START
            }

            GeneratedQuickSetupCurvePointKind.PEAK_END -> {
                SavedLightProgramCurvePointKind.PEAK_END
            }

            GeneratedQuickSetupCurvePointKind.END -> {
                SavedLightProgramCurvePointKind.END
            }
        }
    }

    private fun Set<LightRepeatDay>.toSavedRepeatDays(): Set<Int> {
        return map {
            day ->
            when (day) {
                LightRepeatDay.MONDAY -> DAY_MON
                LightRepeatDay.TUESDAY -> DAY_TUE
                LightRepeatDay.WEDNESDAY -> DAY_WED
                LightRepeatDay.THURSDAY -> DAY_THU
                LightRepeatDay.FRIDAY -> DAY_FRI
                LightRepeatDay.SATURDAY -> DAY_SAT
                LightRepeatDay.SUNDAY -> DAY_SUN
            }
        }
        .toSet()
        .ifEmpty {
            setOf(
                DAY_MON,
                DAY_TUE,
                DAY_WED,
                DAY_THU,
                DAY_FRI,
                DAY_SAT,
                DAY_SUN
            )
        }
    }

    private fun scaledChannelOutput(
        channelPercent: Int,
        masterPercent: Int
    ): Int {
        val safeChannel =
        channelPercent.coerceIn(
            MIN_PERCENT,
            MAX_PERCENT
        )

        val safeMaster =
        masterPercent.coerceIn(
            MIN_PERCENT,
            MAX_PERCENT
        )

        return ((safeChannel * safeMaster) / 100f)
        .roundToInt()
        .coerceIn(
            MIN_PERCENT,
            MAX_PERCENT
        )
    }

    private fun sliderValueOrDefault(
        value: Int,
        defaultValue: Int
    ): Int {
        return if (value <= MIN_PERCENT) {
            defaultValue
        } else {
            value.coerceIn(
                MIN_PERCENT,
                MAX_PERCENT
            )
        }
    }

    private fun createProgramId(): String {
        return "$PROGRAM_ID_PREFIX${System.currentTimeMillis()}"
    }

    private fun minutesToTime(
        minutes: Int
    ): String {
        val safeMinutes =
        ((minutes % MINUTES_IN_DAY) + MINUTES_IN_DAY) % MINUTES_IN_DAY

        val hour = safeMinutes / MINUTES_IN_HOUR
        val minute = safeMinutes % MINUTES_IN_HOUR

        return "%02d:%02d".format(
            hour,
            minute
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
        return requireContext().getColor(
            colorRes
        )
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

        private const val MINUTES_IN_HOUR = 60
        private const val MINUTES_IN_DAY = 24 * MINUTES_IN_HOUR

        private const val DAY_MON = 1
        private const val DAY_TUE = 2
        private const val DAY_WED = 3
        private const val DAY_THU = 4
        private const val DAY_FRI = 5
        private const val DAY_SAT = 6
        private const val DAY_SUN = 7

        private const val MINUTES_IN_DAY_MIN = 0
        private const val MINUTES_IN_DAY_MAX = MINUTES_IN_DAY - 1

        private const val DEFAULT_RAMP_MINUTES = 60

        private const val DEFAULT_START_MINUTES = 9 * MINUTES_IN_HOUR
        private const val DEFAULT_PEAK_START_MINUTES = 10 * MINUTES_IN_HOUR
        private const val DEFAULT_PEAK_END_MINUTES = (18 * MINUTES_IN_HOUR) + 15
        private const val DEFAULT_END_MINUTES = (19 * MINUTES_IN_HOUR) + 15

        private const val DEFAULT_RED = 80
        private const val DEFAULT_GREEN = 84
        private const val DEFAULT_BLUE = 79
        private const val DEFAULT_WHITE = 65

        private const val PROGRAM_ID_PREFIX = "light_program_"
    }
}