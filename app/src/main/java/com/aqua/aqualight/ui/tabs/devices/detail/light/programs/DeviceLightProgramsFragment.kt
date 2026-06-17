package com.aqua.aqualight.ui.tabs.devices.detail.light.programs

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightProgramsBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceConfirmBottomSheet
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceConfirmTone
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceFeedbackType
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceLoading
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceSnack
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.adapter.LightProgramsAdapter
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.ProgramFilter
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramNameSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet.LightProgramOptionsSheet
import kotlinx.coroutines.launch

class DeviceLightProgramsFragment :
    Fragment(R.layout.fragment_device_light_programs) {

    private val args: DeviceLightProgramsFragmentArgs by navArgs()

    private var _binding: FragmentDeviceLightProgramsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightProgramsViewModel by viewModels()

    private lateinit var programsAdapter: LightProgramsAdapter

    private val deviceId: Long
    get() = args.deviceId

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


    override fun onStart() {
        super.onStart()
        viewModel.onProgramsVisible()
    }

    override fun onStop() {
        viewModel.onProgramsHidden()
        super.onStop()
    }

    private fun setupHeader() {
    binding.appHeader.setupAquaHeader(
        fragment = this,
        config = AquaHeaderConfig(
            titleOverride = "Programs",
            actions = listOf(
                AquaHeaderAction(
                    iconRes = R.drawable.ic_light_automation_24,
                    contentDescription = "Light automation",
                    onClick = {
                        openAutomation()
                    }
                ),
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
                viewModel.uiState.collect {
                    state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect {
                    event ->
                    when (event) {
                        is LightProgramsEvent.ShowMessage -> {
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.SUCCESS
                            )
                        }

                        is LightProgramsEvent.ShowError -> {
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.ERROR
                            )
                        }

                        is LightProgramsEvent.SetLoading -> {
                            showDeviceLoading(event.isLoading)
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
            onActiveChanged = {
                isActive ->
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
                confirmDeleteProgram(program)
            }
        )
    }

    private fun confirmDeleteProgram(
        program: LightProgramListItem
    ) {
        val message = if (program.isActive || program.isOnDevice) {
            "Delete this program? If it is running on the controller, all device schedule channels will be cleared."
        } else {
            "Delete this program from your saved programs?"
        }

        DeviceConfirmBottomSheet
        .create(requireContext())
        .show(
            title = "Delete program?",
            message = message,
            confirmText = "Delete",
            cancelText = "Cancel",
            tone = DeviceConfirmTone.DANGER,
            onConfirm = {
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
        ) {
            newName ->
            viewModel.renameProgram(
                programId = program.id,
                newName = newName
            )
        }
    }

    private fun openAutomation() {
        if (deviceId <= 0L) {
            showDeviceSnack(
                message = LIGHT_DEVICE_INFORMATION_MISSING,
                type = DeviceFeedbackType.ERROR
            )
            return
        }

        findNavController().navigate(
            DeviceLightProgramsFragmentDirections.actionDeviceLightProgramsFragmentToDeviceLightAutomationFragment(
                deviceId = deviceId
            )
        )
    }

    private fun openProgramEditor(
        programId: String? = null
    ) {
        if (deviceId <= 0L) {
            showDeviceSnack(
                message = LIGHT_DEVICE_INFORMATION_MISSING,
                type = DeviceFeedbackType.ERROR
            )
            return
        }

        findNavController().navigate(
            DeviceLightProgramsFragmentDirections.actionDeviceLightProgramsFragmentToDeviceLightProgramEditorFragment(
                deviceId = deviceId,
                programId = programId
            )
        )
    }

    override fun onDestroyView() {
        showDeviceLoading(false)
        binding.programsRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_PROGRAM_ID = "programId"
    }
}
