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
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RepeatMode
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramNameSheet
import kotlinx.coroutines.launch
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.validation.LightProgramScheduleConflictValidator
import kotlinx.coroutines.flow.first

class DeviceLightProgramsFragment : Fragment(R.layout.fragment_device_light_programs) {

    private var _binding: FragmentDeviceLightProgramsBinding? = null
    private val binding get() = _binding!!

    private lateinit var programsAdapter: LightProgramsAdapter

    private var selectedFilter = ProgramFilter.ALL
    private var allPrograms: List<LightProgramListItem> = emptyList()

    private val lightProgramsDataStoreManager by lazy {
        LightProgramsDataStoreManager(requireContext().applicationContext)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightProgramsBinding.bind(view)

        setupHeader()
        setupRecyclerView()
        setupClicks()
        observePrograms()
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
                program ->
                openProgramEditor(program.id)
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

    private fun observePrograms() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                lightProgramsDataStoreManager.programsFlow.collect {
                    savedPrograms ->
                    val listItems = savedPrograms.map {
                        program ->
                        program.toListItem()
                    }

                    renderPrograms(listItems)
                }
            }
        }
    }

    private fun renderPrograms(
        programs: List<LightProgramListItem>
    ) {
        if (programs.isEmpty()) {
            allPrograms = emptyList()
            renderEmptyState()
        } else {
            allPrograms = programs
            applyFilter(selectedFilter)
        }
    }

    private fun SavedLightProgram.toListItem(): LightProgramListItem {
        val draft = draft

        return LightProgramListItem(
            id = id,
            name = name,
            subtitle = draft.repeatMode.toSubtitle(),
            isActive = isActive,
            startTime = draft.start.label,
            endTime = draft.end.label,
            rampText = "${draft.start.label} → ${draft.peakStart.label}",
            pointText = "4 Points",
            peakText = "Peak ${draft.channelValues.blue.coerceAtLeast(draft.channelValues.white)}%",
            red = draft.channelValues.red,
            green = draft.channelValues.green,
            blue = draft.channelValues.blue,
            white = draft.channelValues.white
        )
    }

    private fun RepeatMode.toSubtitle(): String {
        return when (this) {
            RepeatMode.EVERY -> "Every day schedule"
            RepeatMode.WEEK -> "Weekday schedule"
            RepeatMode.WEEKEND -> "Weekend schedule"
            RepeatMode.CUSTOM -> "Custom schedule"
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

        binding.emptyProgramsContainer.visibility = View.GONE
        binding.programFilterBar.visibility =
        if (allPrograms.isEmpty()) View.GONE else View.VISIBLE

        if (filteredPrograms.isEmpty()) {
            binding.programsRecyclerView.visibility = View.GONE
            programsAdapter.submitList(emptyList())
        } else {
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
                isActive ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val savedProgram = lightProgramsDataStoreManager.getProgram(program.id)

                    if (savedProgram == null) {
                        Toast.makeText(
                            requireContext(),
                            "Program could not be found",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    if (isActive) {
                        val allPrograms = lightProgramsDataStoreManager.programsFlow.first()

                        val conflict = LightProgramScheduleConflictValidator.findConflict(
                            candidate = savedProgram.copy(isActive = true),
                            existingPrograms = allPrograms
                        )

                        if (conflict != null) {
                            Toast.makeText(
                                requireContext(),
                                "This program overlaps with ${conflict.name}",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }
                    }

                    lightProgramsDataStoreManager.saveProgram(
                        savedProgram.copy(
                            isActive = isActive,
                            updatedAt = System.currentTimeMillis()
                        )
                    )

                    Toast.makeText(
                        requireContext(),
                        if (isActive) "Program activated" else "Program disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDuplicate = {
                viewLifecycleOwner.lifecycleScope.launch {
                    val savedProgram = lightProgramsDataStoreManager.getProgram(program.id)

                    if (savedProgram == null) {
                        Toast.makeText(
                            requireContext(),
                            "Program could not be found",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    val duplicatedProgram = savedProgram.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        name = "${savedProgram.name} Copy",
                        isActive = false,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )

                    lightProgramsDataStoreManager.saveProgram(duplicatedProgram)

                    Toast.makeText(
                        requireContext(),
                        "Program duplicated",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onRename = {
                LightProgramNameSheet
                .create(requireContext())
                .show(
                    title = "Rename Program",
                    subtitle = "Update this program name.",
                    primaryButtonText = "Rename",
                    initialName = program.name
                ) {
                    newName ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val savedProgram = lightProgramsDataStoreManager.getProgram(program.id)

                        if (savedProgram == null) {
                            Toast.makeText(
                                requireContext(),
                                "Program could not be found",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }

                        lightProgramsDataStoreManager.saveProgram(
                            savedProgram.copy(
                                name = newName,
                                updatedAt = System.currentTimeMillis()
                            )
                        )

                        Toast.makeText(
                            requireContext(),
                            "Program renamed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDelete = {
                viewLifecycleOwner.lifecycleScope.launch {
                    lightProgramsDataStoreManager.deleteProgram(program.id)

                    Toast.makeText(
                        requireContext(),
                        "Program deleted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun openProgramEditor(
        programId: String? = null
    ) {
        val bundle = Bundle().apply {
            putString("programId", programId)
        }

        findNavController().navigate(
            R.id.action_deviceLightProgramsFragment_to_deviceLightProgramEditorFragment,
            bundle
        )
    }

    override fun onDestroyView() {
        binding.programsRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}