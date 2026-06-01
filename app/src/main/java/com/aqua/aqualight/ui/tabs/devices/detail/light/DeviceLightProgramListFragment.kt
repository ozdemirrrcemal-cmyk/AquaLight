package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightProgramListBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_DEVICE_ID
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_DEVICE_TITLE
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_PROGRAM_ID
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_PROGRAM_NAME
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.adapter.LightProgramsAdapter
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.data.LightProgramDraftStore
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.ProgramFilter
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgramCurvePointKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramAction
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramActionSheetModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramActionsBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChartData
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveSeries
import com.google.android.material.card.MaterialCardView

class DeviceLightProgramListFragment :
    Fragment(R.layout.fragment_device_light_program_list) {

    private var _binding: FragmentDeviceLightProgramListBinding? = null
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

    private var currentFilter: ProgramFilter = ProgramFilter.ALL
    private var currentState: LightProgramListUiState = LightProgramListUiState()

    private val programsAdapter =
        LightProgramsAdapter(
            onProgramClick = { program ->
                openProgramEditor(
                    programId = program.id,
                    programName = program.title
                )
            },
            onProgramLongClick = { program ->
                showProgramActions(
                    program = program
                )
            },
            onProgramEnabledChanged = { _, _ ->
                // TODO: Enable / disable will be connected to real program storage layer later.
                loadProgramsFromStore()
            }
        )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceLightProgramListBinding.bind(view)

        setupHeader()
        setupRecyclerView()
        setupClicks()
        loadProgramsFromStore()
    }

    override fun onResume() {
        super.onResume()

        if (_binding != null) {
            loadProgramsFromStore()
        }
    }

    private fun setupHeader() = with(binding.deviceHeader) {
        tvTitle.text = getString(R.string.light_programs_title)

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        headerActionsContainer.visibility = View.VISIBLE

        btnActionOne.visibility = View.VISIBLE
        btnActionOne.setImageResource(R.drawable.ic_add_24)
        btnActionOne.contentDescription = getString(R.string.light_add_program)
        btnActionOne.setOnClickListener {
            openProgramEditor(
                programId = null,
                programName = getString(R.string.light_program_new_program)
            )
        }

        btnActionTwo.visibility = View.GONE
        btnActionThree.visibility = View.GONE
    }

    private fun setupRecyclerView() = with(binding.programsRecyclerView) {
        layoutManager = LinearLayoutManager(requireContext())
        adapter = programsAdapter
        itemAnimator = null
        isNestedScrollingEnabled = false
    }

    private fun setupClicks() = with(binding) {
        chipProgramsAll.setOnClickListener {
            setFilter(
                filter = ProgramFilter.ALL
            )
        }

        chipProgramsActive.setOnClickListener {
            setFilter(
                filter = ProgramFilter.ACTIVE
            )
        }

        chipProgramsDisabled.setOnClickListener {
            setFilter(
                filter = ProgramFilter.DISABLED
            )
        }

        btnEmptyAddProgram.setOnClickListener {
            openProgramEditor(
                programId = null,
                programName = getString(R.string.light_program_new_program)
            )
        }
    }

    private fun loadProgramsFromStore() {
        val savedPrograms =
            LightProgramDraftStore.programsForDevice(
                deviceId = deviceId
            )

        val listItems =
            savedPrograms.map { program ->
                program.toListItem()
            }

        val activeProgramId =
            savedPrograms.firstOrNull { program ->
                program.isActive
            }?.id ?: savedPrograms.firstOrNull { program ->
                program.isEnabled
            }?.id

        renderState(
            state =
                LightProgramListUiState(
                    programs = listItems,
                    activeProgram =
                        listItems.firstOrNull { item ->
                            item.id == activeProgramId
                        }
                )
        )
    }

    private fun SavedLightProgram.toListItem(): LightProgramListItem {
        val startPoint =
            curvePoints.firstOrNull { point ->
                point.kind == SavedLightProgramCurvePointKind.START
            }

        val peakStartPoint =
            curvePoints.firstOrNull { point ->
                point.kind == SavedLightProgramCurvePointKind.PEAK_START
            }

        val peakEndPoint =
            curvePoints.firstOrNull { point ->
                point.kind == SavedLightProgramCurvePointKind.PEAK_END
            }

        val endPoint =
            curvePoints.firstOrNull { point ->
                point.kind == SavedLightProgramCurvePointKind.END
            }

        val startMinutes =
            startPoint?.minuteOfDay ?: DEFAULT_START_MINUTES

        val peakStartMinutes =
            peakStartPoint?.minuteOfDay ?: DEFAULT_PEAK_START_MINUTES

        val peakEndMinutes =
            peakEndPoint?.minuteOfDay ?: DEFAULT_PEAK_END_MINUTES

        val endMinutes =
            endPoint?.minuteOfDay ?: DEFAULT_END_MINUTES

        val startTime =
            minutesToTime(
                minutes = startMinutes
            )

        val endTime =
            minutesToTime(
                minutes = endMinutes
            )

        val repeatLabel =
            repeatDaysLabel(
                days = repeatDays
            )

        return LightProgramListItem(
            id = id,
            title = title,
            subtitle = getString(R.string.light_program_generated_subtitle),
            scheduleSummary =
                getString(
                    R.string.light_program_schedule_summary_format,
                    startTime,
                    endTime,
                    repeatLabel
                ),
            isEnabled = isEnabled,
            startTimeLabel = startTime,
            rampLabel =
                getString(
                    R.string.light_quick_setup_ramp_value_format,
                    rampMinutes
                ),
            endTimeLabel = endTime,
            repeatLabel = repeatLabel,
            peakLabel =
                getString(
                    R.string.common_percent_value,
                    peakIntensityPercent
                ),
            redLabel =
                getString(
                    R.string.light_program_channel_short_red_format,
                    balance.red
                ),
            greenLabel =
                getString(
                    R.string.light_program_channel_short_green_format,
                    balance.green
                ),
            blueLabel =
                getString(
                    R.string.light_program_channel_short_blue_format,
                    balance.blue
                ),
            whiteLabel =
                getString(
                    R.string.light_program_channel_short_white_format,
                    balance.white
                ),
            photoperiodLabel =
                photoperiodLabel(
                    startMinutes = startMinutes,
                    endMinutes = endMinutes
                ),
            curveData =
                LightCurveChartData(
                    series =
                        listOf(
                            LightCurveSeries(
                                channel = LightCurveChannel.MASTER,
                                isActive = true,
                                points =
                                    listOf(
                                        LightCurvePoint(
                                            minuteOfDay = startMinutes,
                                            intensityPercent = startPoint?.masterPercent ?: 0,
                                            isMajor = true
                                        ),
                                        LightCurvePoint(
                                            minuteOfDay = peakStartMinutes,
                                            intensityPercent = peakStartPoint?.masterPercent
                                                ?: peakIntensityPercent,
                                            isMajor = true
                                        ),
                                        LightCurvePoint(
                                            minuteOfDay = peakEndMinutes,
                                            intensityPercent = peakEndPoint?.masterPercent
                                                ?: peakIntensityPercent,
                                            isMajor = true
                                        ),
                                        LightCurvePoint(
                                            minuteOfDay = endMinutes,
                                            intensityPercent = endPoint?.masterPercent ?: 0,
                                            isMajor = true
                                        )
                                    )
                            )
                        ),
                    currentTimeMinutes = null
                )
        )
    }

    private fun setFilter(
        filter: ProgramFilter
    ) {
        currentFilter = filter

        renderFilterChips()

        renderProgramList(
            state = currentState
        )
    }

    private fun renderState(
        state: LightProgramListUiState
    ) {
        currentState = state

        renderActiveProgramSummary(
            activeProgram = state.activeProgram
        )

        renderFilterChips()

        renderProgramList(
            state = state
        )
    }

    private fun renderActiveProgramSummary(
        activeProgram: LightProgramListItem?
    ) = with(binding) {
        cardProgramSummary.visibility =
            if (activeProgram == null) {
                View.GONE
            } else {
                View.VISIBLE
            }

        if (activeProgram == null) {
            viewActiveProgramCurve.clear()
            return@with
        }

        tvActiveProgramTitle.text = activeProgram.title
        tvActiveProgramSummary.text = activeProgram.scheduleSummary
        tvActiveProgramChip.text = getString(R.string.light_program_status_active)

        tvProgramSummaryPeak.text = activeProgram.peakLabel
        tvProgramPhotoperiod.text = activeProgram.photoperiodLabel

        viewActiveProgramCurve.submitData(
            data = activeProgram.curveData
        )
    }

    private fun renderProgramList(
        state: LightProgramListUiState
    ) = with(binding) {
        val filteredPrograms =
            when (currentFilter) {
                ProgramFilter.ALL -> {
                    state.programs
                }

                ProgramFilter.ACTIVE -> {
                    state.programs.filter { program ->
                        program.isEnabled
                    }
                }

                ProgramFilter.DISABLED -> {
                    state.programs.filter { program ->
                        !program.isEnabled
                    }
                }
            }

        programFilterRow.visibility =
            if (state.programs.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        programsRecyclerView.visibility =
            if (filteredPrograms.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        programsEmptyState.visibility =
            if (filteredPrograms.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }

        if (state.programs.isEmpty()) {
            tvProgramsEmptyTitle.setText(R.string.light_programs_empty_title)
            tvProgramsEmptyDescription.setText(R.string.light_programs_empty_description)
        } else {
            tvProgramsEmptyTitle.setText(R.string.light_programs_empty_filtered_title)
            tvProgramsEmptyDescription.setText(R.string.light_programs_empty_filtered_description)
        }

        programsAdapter.submitPrograms(
            programs = filteredPrograms,
            activeProgramId = state.activeProgram?.id
        )
    }

    private fun renderFilterChips() = with(binding) {
        chipProgramsAll.applyFilterStyle(
            selected = currentFilter == ProgramFilter.ALL
        )

        chipProgramsActive.applyFilterStyle(
            selected = currentFilter == ProgramFilter.ACTIVE
        )

        chipProgramsDisabled.applyFilterStyle(
            selected = currentFilter == ProgramFilter.DISABLED
        )
    }

    private fun showProgramActions(
        program: LightProgramListItem
    ) {
        LightProgramActionsBottomSheet.show(
            fragment = this,
            model =
                LightProgramActionSheetModel(
                    title = program.title,
                    subtitle = program.scheduleSummary,
                    isEnabled = program.isEnabled,
                    isActiveProgram = currentState.activeProgram?.id == program.id
                ),
            onAction = { action ->
                handleProgramAction(
                    action = action,
                    program = program
                )
            }
        )
    }

    private fun handleProgramAction(
        action: LightProgramAction,
        program: LightProgramListItem
    ) {
        when (action) {
            LightProgramAction.EDIT -> {
                openProgramEditor(
                    programId = program.id,
                    programName = program.title
                )
            }

            LightProgramAction.PREVIEW -> {
                // TODO: Preview Day flow will be connected later.
            }

            LightProgramAction.DUPLICATE -> {
                // TODO: Duplicate will be connected to program storage layer later.
            }

            LightProgramAction.SET_ACTIVE -> {
                // TODO: Set active will be connected to program storage layer later.
            }

            LightProgramAction.TOGGLE_ENABLED -> {
                // TODO: Enable / disable will be connected to program storage layer later.
            }

            LightProgramAction.DELETE -> {
                // TODO: Delete confirmation and storage delete will be connected later.
            }
        }
    }

    private fun openProgramEditor(
        programId: String?,
        programName: String
    ) {
        findNavController().navigate(
            R.id.action_deviceLightProgramListFragment_to_deviceLightProgramEditorFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId,
                ARG_DEVICE_TITLE to deviceTitle,
                ARG_PROGRAM_ID to programId,
                ARG_PROGRAM_NAME to programName
            )
        )
    }

    private fun repeatDaysLabel(
        days: Set<Int>
    ): String {
        return when (days) {
            setOf(
                DAY_MON,
                DAY_TUE,
                DAY_WED,
                DAY_THU,
                DAY_FRI,
                DAY_SAT,
                DAY_SUN
            ) -> {
                getString(R.string.light_quick_setup_days_every_day)
            }

            setOf(
                DAY_MON,
                DAY_TUE,
                DAY_WED,
                DAY_THU,
                DAY_FRI
            ) -> {
                getString(R.string.light_quick_setup_days_weekdays)
            }

            setOf(
                DAY_SAT,
                DAY_SUN
            ) -> {
                getString(R.string.light_quick_setup_days_weekend)
            }

            else -> {
                getString(
                    R.string.light_quick_setup_days_count_format,
                    days.size
                )
            }
        }
    }

    private fun photoperiodLabel(
        startMinutes: Int,
        endMinutes: Int
    ): String {
        val durationMinutes =
            if (endMinutes >= startMinutes) {
                endMinutes - startMinutes
            } else {
                MINUTES_IN_DAY - startMinutes + endMinutes
            }

        val hours = durationMinutes / MINUTES_IN_HOUR
        val minutes = durationMinutes % MINUTES_IN_HOUR

        return if (minutes == 0) {
            getString(
                R.string.light_program_photoperiod_hours_format,
                hours
            )
        } else {
            getString(
                R.string.light_program_photoperiod_hours_minutes_format,
                hours,
                minutes
            )
        }
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

    private fun MaterialCardView.applyFilterStyle(
        selected: Boolean
    ) {
        setCardBackgroundColor(
            color(
                if (selected) {
                    R.color.light_accent_soft
                } else {
                    R.color.light_surface_deep
                }
            )
        )

        strokeColor =
            color(
                if (selected) {
                    R.color.light_accent
                } else {
                    R.color.light_stroke
                }
            )

        findFirstTextView()?.setTextColor(
            color(
                if (selected) {
                    R.color.light_accent
                } else {
                    R.color.settings_text_secondary
                }
            )
        )
    }

    private fun View.findFirstTextView(): TextView? {
        if (this is TextView) {
            return this
        }

        if (this is ViewGroup) {
            children.forEach { child ->
                val result = child.findFirstTextView()

                if (result != null) {
                    return result
                }
            }
        }

        return null
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

    companion object {
        private const val MINUTES_IN_HOUR = 60
        private const val MINUTES_IN_DAY = 24 * MINUTES_IN_HOUR

        private const val DEFAULT_START_MINUTES = 9 * MINUTES_IN_HOUR
        private const val DEFAULT_PEAK_START_MINUTES = 10 * MINUTES_IN_HOUR
        private const val DEFAULT_PEAK_END_MINUTES = (18 * MINUTES_IN_HOUR) + 15
        private const val DEFAULT_END_MINUTES = (19 * MINUTES_IN_HOUR) + 15

        private const val DAY_MON = 1
        private const val DAY_TUE = 2
        private const val DAY_WED = 3
        private const val DAY_THU = 4
        private const val DAY_FRI = 5
        private const val DAY_SAT = 6
        private const val DAY_SUN = 7
    }
}