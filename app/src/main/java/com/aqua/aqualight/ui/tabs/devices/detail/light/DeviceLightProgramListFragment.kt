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
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.ProgramFilter
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramAction
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramActionSheetModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramActionsBottomSheet
import com.google.android.material.card.MaterialCardView
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.data.LightProgramDraftStore
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.mapper.SavedLightProgramListMapper

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

    private var currentState: LightProgramListUiState =
    LightProgramListUiState()

    private val programsAdapter =
    LightProgramsAdapter(
        onProgramClick = {
            program ->
            openProgramEditor(
                programId = program.id,
                programName = program.title
            )
        },
        onProgramLongClick = {
            program ->
            showProgramActions(
                program = program
            )
        },
        onProgramEnabledChanged = {
            program, isChecked ->
            if (isChecked) {
                LightProgramDraftStore.setActiveProgram(
                    deviceId = deviceId,
                    programId = program.id
                )
            } else {
                LightProgramDraftStore.clearActiveProgram(
                    deviceId = deviceId
                )
            }

            loadPrograms()
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

        loadPrograms()
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

    override fun onResume() {
        super.onResume()

        if (_binding != null) {
            loadPrograms()
        }
    }

    private fun loadPrograms() {
        val savedPrograms =
        LightProgramDraftStore.getPrograms(
            deviceId = deviceId
        )

        val uiState =
        SavedLightProgramListMapper.map(
            context = requireContext(),
            programs = savedPrograms
        )

        renderState(
            state = uiState
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
                state.programs.filter {
                    program ->
                    program.isEnabled
                }
            }

            ProgramFilter.DISABLED -> {
                state.programs.filter {
                    program ->
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
            onAction = {
                action ->
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
                // Preview Day daha sonra gerçek program verisiyle bağlanacak.
            }

            LightProgramAction.DUPLICATE -> {
                LightProgramDraftStore.duplicateProgram(
                    deviceId = deviceId,
                    programId = program.id
                )

                loadPrograms()
            }

            LightProgramAction.SET_ACTIVE -> {
                LightProgramDraftStore.setActiveProgram(
                    deviceId = deviceId,
                    programId = program.id
                )

                loadPrograms()
            }

            LightProgramAction.TOGGLE_ENABLED -> {
                if (program.isEnabled) {
                    LightProgramDraftStore.clearActiveProgram(
                        deviceId = deviceId
                    )
                } else {
                    LightProgramDraftStore.setActiveProgram(
                        deviceId = deviceId,
                        programId = program.id
                    )
                }

                loadPrograms()
            }

            LightProgramAction.DELETE -> {
                LightProgramDraftStore.deleteProgram(
                    deviceId = deviceId,
                    programId = program.id
                )

                loadPrograms()
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
            children.forEach {
                child ->
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
        return requireContext().getColor(
            colorRes
        )
    }

    override fun onDestroyView() {
        _binding = null

        super.onDestroyView()
    }
}