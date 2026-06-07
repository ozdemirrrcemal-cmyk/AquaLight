package com.aqua.aqualight.ui.tabs.devices.detail.light.programs

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightProgramsBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.adapter.LightProgramsAdapter
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.ProgramFilter
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramNameSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.sheet.LightProgramOptionsSheet
import kotlinx.coroutines.launch

class DeviceLightProgramsFragment :
    Fragment(R.layout.fragment_device_light_programs) {

    private var _binding: FragmentDeviceLightProgramsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightProgramsViewModel by viewModels()

    private lateinit var programsAdapter: LightProgramsAdapter

    private val deviceId: Long
        get() = arguments?.getLong(ARG_DEVICE_ID, 0L) ?: 0L

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightProgramsBinding.bind(view)

        setupHeader()
        setupRecyclerView()
        setupClicks()
        observeUiState()
        observeEvents()

        viewModel.initialize(deviceId)
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
            onProgramClick = { program ->
                openProgramEditor(program.id)
            },
            onProgramOptionsClick = { program ->
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
            viewModel.applyFilter(ProgramFilter.ALL)
        }

        binding.filterActive.setOnClickListener {
            viewModel.applyFilter(ProgramFilter.ACTIVE)
        }

        binding.filterDisabled.setOnClickListener {
            viewModel.applyFilter(ProgramFilter.DISABLED)
        }
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
                        is LightProgramsEvent.ShowMessage -> {
                            Toast.makeText(
                                requireContext(),
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        is LightProgramsEvent.ShowError -> {
                            Toast.makeText(
                                requireContext(),
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun renderUiState(
        state: LightProgramListUiState
    ) {
        updateFilterUi(
            filter = state.selectedFilter
        )

        binding.emptyProgramsContainer.visibility = if (state.showEmptyState) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.programFilterBar.visibility = if (state.showFilterBar) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.programsRecyclerView.visibility = if (state.showProgramList) {
            View.VISIBLE
        } else {
            View.GONE
        }

        programsAdapter.submitList(
            state.visiblePrograms
        )
    }

    private fun updateFilterUi(
        filter: ProgramFilter
    ) {
        val selectedBg = R.drawable.bg_light_filter_selected
        val transparentBg = android.R.color.transparent

        binding.filterAll.setBackgroundResource(
            if (filter == ProgramFilter.ALL) {
                selectedBg
            } else {
                transparentBg
            }
        )

        binding.filterActive.setBackgroundResource(
            if (filter == ProgramFilter.ACTIVE) {
                selectedBg
            } else {
                transparentBg
            }
        )

        binding.filterDisabled.setBackgroundResource(
            if (filter == ProgramFilter.DISABLED) {
                selectedBg
            } else {
                transparentBg
            }
        )

        val selectedText =
            requireContext().getColor(R.color.light_button_on_primary)

        val normalText =
            requireContext().getColor(R.color.light_text_secondary)

        binding.filterAll.setTextColor(
            if (filter == ProgramFilter.ALL) {
                selectedText
            } else {
                normalText
            }
        )

        binding.filterActive.setTextColor(
            if (filter == ProgramFilter.ACTIVE) {
                selectedText
            } else {
                normalText
            }
        )

        binding.filterDisabled.setTextColor(
            if (filter == ProgramFilter.DISABLED) {
                selectedText
            } else {
                normalText
            }
        )
    }

    private fun showProgramOptionsSheet(
        program: LightProgramListItem
    ) {
        LightProgramOptionsSheet
            .create(requireContext())
            .show(
                programName = program.name,
                subtitle = "${program.subtitle} · ${program.startTime} → ${program.endTime}",
                isActive = program.isActive,
                onActiveChanged = { isActive ->
                    viewModel.setProgramActive(
                        programId = program.id,
                        isActive = isActive
                    )
                },
                onDuplicate = {
                    viewModel.duplicateProgram(
                        programId = program.id
                    )
                },
                onRename = {
                    showRenameProgramSheet(program)
                },
                onDelete = {
                    viewModel.deleteProgram(
                        programId = program.id
                    )
                }
            )
    }

    private fun showRenameProgramSheet(
        program: LightProgramListItem
    ) {
        LightProgramNameSheet
            .create(requireContext())
            .show(
                title = "Rename Program",
                subtitle = "Update this program name.",
                primaryButtonText = "Rename",
                initialName = program.name
            ) { newName ->
                viewModel.renameProgram(
                    programId = program.id,
                    newName = newName
                )
            }
    }

    private fun openProgramEditor(
        programId: String? = null
    ) {
        val bundle = Bundle().apply {
            putLong(ARG_DEVICE_ID, deviceId)

            if (!programId.isNullOrBlank()) {
                putString(ARG_PROGRAM_ID, programId)
            }
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

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_PROGRAM_ID = "programId"
    }
}