package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightProgramListBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.adapter.LightProgramsAdapter
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.ProgramFilter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView

class DeviceLightProgramListFragment :
Fragment(R.layout.fragment_device_light_program_list) {

    private var _binding: FragmentDeviceLightProgramListBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
    get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val programsAdapter = LightProgramsAdapter(
        onProgramClick = {
            program ->
            openProgramEditor(
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
            program, isEnabled ->
            updateProgramEnabled(
                programId = program.id,
                isEnabled = isEnabled
            )
        }
    )

    private var allPrograms: List<LightProgramListItem> = emptyList()
    private var currentFilter: ProgramFilter = ProgramFilter.ALL
    private var activeProgramId: String? = null

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
        renderInitialState()
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
                programName = DEFAULT_NEW_PROGRAM_NAME
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
            applyFilter(
                filter = ProgramFilter.ALL
            )
        }

        chipProgramsActive.setOnClickListener {
            applyFilter(
                filter = ProgramFilter.ACTIVE
            )
        }

        chipProgramsDisabled.setOnClickListener {
            applyFilter(
                filter = ProgramFilter.DISABLED
            )
        }

        btnEmptyAddProgram.setOnClickListener {
            openProgramEditor(
                programName = DEFAULT_NEW_PROGRAM_NAME
            )
        }
    }

    private fun renderInitialState() {
        renderPrograms(
            programs = emptyList(),
            preferredActiveProgramId = null
        )
    }

    private fun renderPrograms(
        programs: List<LightProgramListItem>,
        preferredActiveProgramId: String?
    ) {
        allPrograms = programs

        activeProgramId =
        preferredActiveProgramId
        ?.takeIf {
            id ->
            programs.any {
                program ->
                program.id == id
            }
        }
        ?: programs.firstOrNull {
            program ->
            program.isEnabled
        }?.id

        renderActiveProgramSummary()
        renderFilterChips()
        renderProgramList()
    }

    private fun renderActiveProgramSummary() = with(binding) {
        val activeProgram =
        allPrograms.firstOrNull {
            program ->
            program.id == activeProgramId
        } ?: allPrograms.firstOrNull {
            program ->
            program.isEnabled
        }

        cardProgramSummary.visibility =
        if (activeProgram == null) {
            View.GONE
        } else {
            View.VISIBLE
        }

        if (activeProgram == null) {
            return@with
        }

        tvActiveProgramTitle.text = activeProgram.title
        tvActiveProgramSummary.text = activeProgram.scheduleSummary
        tvActiveProgramChip.text = ACTIVE_PROGRAM_LABEL

        tvProgramSummaryPeak.text = "${activeProgram.peakPercent}%"
        tvProgramPhotoperiod.text = activeProgram.photoperiodLabel

        viewActiveProgramCurve.setCurveDisplayMode(
            mode = LightProgramCurveView.CurveDisplayMode.SIMPLE
        )

        viewActiveProgramCurve.setCurvePoints(
            points = listOf(
                LightProgramCurveView.CurvePoint(
                    time = activeProgram.startTime,
                    intensity = activeProgram.startIntensity
                ),
                LightProgramCurveView.CurvePoint(
                    time = activeProgram.sunriseEndTime,
                    intensity = activeProgram.sunriseEndIntensity
                ),
                LightProgramCurveView.CurvePoint(
                    time = activeProgram.peakEndTime,
                    intensity = activeProgram.peakEndIntensity
                ),
                LightProgramCurveView.CurvePoint(
                    time = activeProgram.endTime,
                    intensity = activeProgram.endIntensity
                )
            )
        )
    }

    private fun renderProgramList() = with(binding) {
        val filteredPrograms =
        when (currentFilter) {
            ProgramFilter.ALL -> {
                allPrograms
            }

            ProgramFilter.ACTIVE -> {
                allPrograms.filter {
                    program ->
                    program.isEnabled
                }
            }

            ProgramFilter.DISABLED -> {
                allPrograms.filter {
                    program ->
                    !program.isEnabled
                }
            }
        }

        programFilterRow.visibility =
        if (allPrograms.isEmpty()) {
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

        programsAdapter.submitPrograms(
            programs = filteredPrograms,
            activeProgramId = activeProgramId
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

    private fun applyFilter(
        filter: ProgramFilter
    ) {
        currentFilter = filter

        renderFilterChips()
        renderProgramList()
    }

    private fun updateProgramEnabled(
        programId: String,
        isEnabled: Boolean
    ) {
        allPrograms =
        allPrograms.map {
            program ->
            if (program.id == programId) {
                program.copy(
                    isEnabled = isEnabled
                )
            } else {
                program
            }
        }

        if (!isEnabled && activeProgramId == programId) {
            activeProgramId =
            allPrograms.firstOrNull {
                program ->
                program.isEnabled && program.id != programId
            }?.id
        }

        if (isEnabled && activeProgramId == null) {
            activeProgramId = programId
        }

        renderActiveProgramSummary()
        renderProgramList()

        val programTitle =
        allPrograms.firstOrNull {
            program ->
            program.id == programId
        }?.title.orEmpty()

        showMessage(
            message =
            if (isEnabled) {
                "$programTitle enabled"
            } else {
                "$programTitle disabled"
            }
        )
    }

    private fun setActiveProgram(
        programId: String
    ) {
        val program =
        allPrograms.firstOrNull {
            item ->
            item.id == programId
        } ?: return

        activeProgramId = programId

        allPrograms =
        allPrograms.map {
            item ->
            if (item.id == programId) {
                item.copy(
                    isEnabled = true
                )
            } else {
                item
            }
        }

        renderActiveProgramSummary()
        renderProgramList()

        showMessage(
            message = "${program.title} set as active"
        )
    }

    private fun duplicateProgram(
        sourceProgram: LightProgramListItem
    ) {
        val copyId = "${sourceProgram.id}_copy_${System.currentTimeMillis()}"

        val copiedProgram =
        sourceProgram.copy(
            id = copyId,
            title = "${sourceProgram.title} Copy",
            isEnabled = false
        )

        allPrograms = allPrograms + copiedProgram
        currentFilter = ProgramFilter.ALL

        renderActiveProgramSummary()
        renderFilterChips()
        renderProgramList()

        openProgramEditor(
            programName = copiedProgram.title
        )
    }

    private fun deleteProgram(
        programId: String
    ) {
        val deletedProgram =
        allPrograms.firstOrNull {
            program ->
            program.id == programId
        } ?: return

        allPrograms =
        allPrograms.filterNot {
            program ->
            program.id == programId
        }

        if (activeProgramId == programId) {
            activeProgramId =
            allPrograms.firstOrNull {
                program ->
                program.isEnabled
            }?.id
        }

        renderActiveProgramSummary()
        renderProgramList()

        showMessage(
            message = "${deletedProgram.title} deleted"
        )
    }

    private fun showProgramActions(
        program: LightProgramListItem
    ) {
        val dialog = BottomSheetDialog(requireContext())

        val sheetView =
        layoutInflater.inflate(
            R.layout.bottom_sheet_light_program_actions,
            null
        )

        sheetView.findViewById<TextView>(
            R.id.tvProgramActionTitle
        ).text = program.title

        sheetView.findViewById<TextView>(
            R.id.tvProgramActionSubtitle
        ).text = program.scheduleSummary

        val toggleButton =
        sheetView.findViewById<TextView>(
            R.id.btnProgramActionToggle
        )

        toggleButton.text =
        if (program.isEnabled) {
            "Disable Program"
        } else {
            "Enable Program"
        }

        val setActiveButton =
        sheetView.findViewById<TextView>(
            R.id.btnProgramActionSetActive
        )

        val isCurrentActiveProgram =
        program.id == activeProgramId

        setActiveButton.text =
        if (isCurrentActiveProgram) {
            "Active Program"
        } else {
            "Set as Active Program"
        }

        setActiveButton.isEnabled = !isCurrentActiveProgram
        setActiveButton.alpha =
        if (isCurrentActiveProgram) {
            DISABLED_ACTION_ALPHA
        } else {
            ENABLED_ACTION_ALPHA
        }

        sheetView.findViewById<TextView>(
            R.id.btnProgramActionEdit
        ).setOnClickListener {
            dialog.dismiss()

            openProgramEditor(
                programName = program.title
            )
        }

        sheetView.findViewById<TextView>(
            R.id.btnProgramActionPreview
        ).setOnClickListener {
            dialog.dismiss()

            showMessage(
                message = "Preview day for ${program.title} will be added"
            )
        }

        sheetView.findViewById<TextView>(
            R.id.btnProgramActionDuplicate
        ).setOnClickListener {
            dialog.dismiss()

            duplicateProgram(
                sourceProgram = program
            )
        }

        setActiveButton.setOnClickListener {
            if (!setActiveButton.isEnabled) {
                return@setOnClickListener
            }

            dialog.dismiss()

            setActiveProgram(
                programId = program.id
            )
        }

        toggleButton.setOnClickListener {
            dialog.dismiss()

            updateProgramEnabled(
                programId = program.id,
                isEnabled = !program.isEnabled
            )
        }

        sheetView.findViewById<TextView>(
            R.id.btnProgramActionDelete
        ).setOnClickListener {
            dialog.dismiss()

            deleteProgram(
                programId = program.id
            )
        }

        sheetView.findViewById<TextView>(
            R.id.btnProgramActionCancel
        ).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun openProgramEditor(
        programName: String
    ) {
        findNavController().navigate(
            R.id.action_deviceLightProgramListFragment_to_deviceLightProgramEditorFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId,
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

    private fun showMessage(
        message: String
    ) {
        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        _binding = null

        super.onDestroyView()
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_PROGRAM_NAME = "programName"
        private const val DEFAULT_NEW_PROGRAM_NAME = "New Program"
        private const val ACTIVE_PROGRAM_LABEL = "ACTIVE"

        private const val DISABLED_ACTION_ALPHA = 0.45f
        private const val ENABLED_ACTION_ALPHA = 1f
    }
}