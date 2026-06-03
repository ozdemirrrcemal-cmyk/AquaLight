package com.aqua.aqualight.ui.tabs.devices.detail.light.programs

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightProgramsBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.adapter.LightProgramsAdapter
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.sheet.LightProgramOptionsSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.ProgramFilter

class DeviceLightProgramsFragment : Fragment(R.layout.fragment_device_light_programs) {

    private var _binding: FragmentDeviceLightProgramsBinding? = null
    private val binding get() = _binding!!

    private lateinit var programsAdapter: LightProgramsAdapter

    private var selectedFilter = ProgramFilter.ALL
    private var allPrograms: List<LightProgramListItem> = emptyList()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightProgramsBinding.bind(view)

        setupHeader()
        setupRecyclerView()
        setupClicks()
        renderPreviewPrograms()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            AquaHeaderConfig(
                title = "Programs",
                showBackButton = true,
                onBackClick = {
                    findNavController().popBackStack()
                },
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_add,
                        contentDescription = "Add program",
                        onClick = {
                            openProgramEditor()
                        }
                    )
                )
            )
        )
    }

    private fun setupRecyclerView() {
        programsAdapter = LightProgramsAdapter(
            onProgramClick = {
                openProgramEditor()
            },
            onProgramOptionsClick = {
                program ->
                showProgramOptionsSheet(program)
            }
        )

        binding.programsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = programsAdapter
        }
    }

    private fun setupClicks() {
        binding.btnAddFirstProgram.setOnClickListener {
            openProgramEditor()
        }

        binding.filterAll.setOnClickListener {
            applyFilter(ProgramFilter.ALL)
        }

        binding.filterActive.setOnClickListener {
            applyFilter(ProgramFilter.ACTIVE)
        }

        binding.filterDisabled.setOnClickListener {
            applyFilter(ProgramFilter.DISABLED)
        }
    }

    private fun renderPreviewPrograms() {
        val programs = listOf(
            LightProgramListItem(
                id = 1L,
                name = "Nature Day",
                subtitle = "Every day schedule",
                isActive = true,
                startTime = "08:00",
                endTime = "20:00",
                rampText = "Ramp 60m",
                pointText = "4 Points",
                peakText = "Peak 42W",
                red = 80,
                green = 85,
                blue = 100,
                white = 60
            ),
            LightProgramListItem(
                id = 2L,
                name = "Evening Soft",
                subtitle = "Weekend schedule",
                isActive = false,
                startTime = "17:30",
                endTime = "22:00",
                rampText = "Ramp 30m",
                pointText = "4 Points",
                peakText = "Peak 24W",
                red = 70,
                green = 45,
                blue = 30,
                white = 35
            )
        )

        if (programs.isEmpty()) {
            renderEmptyState()
        } else {
            allPrograms = programs
            applyFilter(selectedFilter)
        }
    }

    private fun applyFilter(filter: ProgramFilter) {
        selectedFilter = filter
        updateFilterUi(filter)

        val filteredPrograms = when (filter) {
            ProgramFilter.ALL -> allPrograms
            ProgramFilter.ACTIVE -> allPrograms.filter {
                it.isActive
            }
            ProgramFilter.DISABLED -> allPrograms.filter {
                !it.isActive
            }
        }

        if (filteredPrograms.isEmpty()) {
            binding.programsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyProgramsContainer.visibility = View.GONE
            binding.programFilterBar.visibility = View.VISIBLE
            binding.programsRecyclerView.visibility = View.VISIBLE
            programsAdapter.submitList(filteredPrograms)
        }
    }

    private fun updateFilterUi(filter: ProgramFilter) {
        val selectedBg = R.drawable.bg_light_filter_selected
        val transparentBg = android.R.color.transparent

        binding.filterAll.setBackgroundResource(
            if (filter == ProgramFilter.ALL) selectedBg else transparentBg
        )
        binding.filterActive.setBackgroundResource(
            if (filter == ProgramFilter.ACTIVE) selectedBg else transparentBg
        )
        binding.filterDisabled.setBackgroundResource(
            if (filter == ProgramFilter.DISABLED) selectedBg else transparentBg
        )

        val selectedText = requireContext().getColor(R.color.light_button_on_primary)
        val normalText = requireContext().getColor(R.color.light_text_secondary)

        binding.filterAll.setTextColor(if (filter == ProgramFilter.ALL) selectedText else normalText)
        binding.filterActive.setTextColor(if (filter == ProgramFilter.ACTIVE) selectedText else normalText)
        binding.filterDisabled.setTextColor(if (filter == ProgramFilter.DISABLED) selectedText else normalText)
    }

    private fun renderEmptyState() {
        binding.emptyProgramsContainer.visibility = View.VISIBLE
        binding.programFilterBar.visibility = View.GONE
        binding.programsRecyclerView.visibility = View.GONE
    }

    private fun showProgramOptionsSheet(program: LightProgramListItem) {
        LightProgramOptionsSheet
        .create(requireContext())
        .show(
            programName = program.name,
            subtitle = "${program.subtitle} · ${program.startTime} → ${program.endTime}",
            isActive = program.isActive,
            onActiveChanged = {
                Toast.makeText(requireContext(), "Program active: $it", Toast.LENGTH_SHORT).show()
            },
            onDuplicate = {
                Toast.makeText(requireContext(), "Duplicate ${program.name}", Toast.LENGTH_SHORT).show()
            },
            onRename = {
                Toast.makeText(requireContext(), "Rename ${program.name}", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                Toast.makeText(requireContext(), "Delete ${program.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun openProgramEditor() {
    findNavController().navigate(
        R.id.action_deviceLightProgramsFragment_to_deviceLightProgramEditorFragment
    )
}

    override fun onDestroyView() {
        binding.programsRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}