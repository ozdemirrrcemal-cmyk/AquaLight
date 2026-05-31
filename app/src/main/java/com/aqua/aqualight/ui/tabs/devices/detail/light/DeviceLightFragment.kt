package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.LightOverviewUiState
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class DeviceLightFragment : Fragment(R.layout.fragment_device_light) {

    private var _binding: FragmentDeviceLightBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceTitle: String
        get() = requireArguments()
            .getString(ARG_DEVICE_TITLE)
            .orEmpty()
            .ifBlank {
                DEFAULT_DEVICE_TITLE
            }

    private val viewModel: DeviceLightViewModel by viewModels {
        DeviceLightViewModel.Factory(
            deviceId = deviceId
        )
    }

    private var isProgrammaticSwitchChange = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightBinding.bind(view)

        setupHeader()
        setupClicks()
        observeUiState()

        viewModel.refresh()
    }

    private fun setupHeader() = with(binding.deviceHeader) {
        tvTitle.text = deviceTitle

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        headerActionsContainer.visibility = View.VISIBLE

        btnActionOne.visibility = View.VISIBLE
        btnActionOne.setImageResource(R.drawable.ic_light_sync_24)
        btnActionOne.contentDescription = getString(R.string.light_cd_sync)
        btnActionOne.setOnClickListener {
            onHeaderSyncClick()
        }

        btnActionTwo.visibility = View.VISIBLE
        btnActionTwo.setImageResource(R.drawable.ic_light_settings_24)
        btnActionTwo.contentDescription = getString(R.string.light_cd_settings)
        btnActionTwo.setOnClickListener {
            openDeviceSettings()
        }

        btnActionThree.visibility = View.VISIBLE
        btnActionThree.setImageResource(R.drawable.ic_light_more_vert_24)
        btnActionThree.contentDescription = getString(R.string.light_cd_more)
        btnActionThree.setOnClickListener {
            showTemporaryModeSheet()
        }
    }

    private fun onHeaderSyncClick() {
        showMessage("Syncing device data")
        viewModel.refresh()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->
                    renderLightState(
                        state = state
                    )
                }
            }
        }
    }

    private fun renderLightState(
        state: LightOverviewUiState
    ) = with(binding) {
        tvLightRunningTitle.text = state.programTitle
        tvLightRunningSubtitle.text = state.programSubtitle
        tvLightMode.text = state.modeLabel
        tvCurrentOutputValue.text = state.currentOutputLabel

        tvChannelRedValue.text = state.redLabel
        tvChannelGreenValue.text = state.greenLabel
        tvChannelBlueValue.text = state.blueLabel
        tvChannelWhiteValue.text = state.whiteLabel

        tvLightNowState.text = state.nowLabel
        tvLightNextState.text = state.nextLabel
        tvLightCurveNow.text = state.curveNowLabel

        tvTimelineStartLabel.text = state.timelineStartLabel
        tvTimelineMidLabel.text = state.timelineMidLabel
        tvTimelineEndLabel.text = state.timelineEndLabel

        tvCurveStartValue.text = state.curveStartLabel
        tvCurvePeakValue.text = state.curvePeakLabel
        tvCurveSunsetValue.text = state.curveSunsetLabel
        tvCurveRampValue.text = state.curveRampLabel

        tvActiveProgramName.text = state.activeProgramName
        tvActiveProgramSchedule.text = state.activeProgramSchedule
        tvActiveProgramChannels.text = state.activeProgramChannels
        tvActiveProgramStatus.text = state.activeProgramStatusLabel

        tvActiveProgramSchedule.visibility =
            if (state.activeProgramSchedule.isBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        tvActiveProgramChannels.visibility =
            if (state.activeProgramChannels.isBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        tvActiveProgramStatus.visibility =
            if (state.isLoading || state.activeProgramStatusLabel.isBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        switchProgramEveryDay.visibility =
            if (state.isLoading) {
                View.GONE
            } else {
                View.VISIBLE
            }

        switchProgramEveryDay.isEnabled = !state.isLoading

        tvDeviceHealthSync.text = state.healthLabel
        tvLightTemperature.text = state.temperatureLabel
        tvLightFan.text = state.fanLabel
        tvLightDeviceTime.text = state.deviceTimeLabel
        tvLightFirmware.text = state.firmwareLabel

        updateTimelineAvailability(
            hasCurveData = !state.isLoading
        )

        setProgramSwitchChecked(
            checked = state.isProgramEnabled
        )
    }

    private fun updateTimelineAvailability(
        hasCurveData: Boolean
    ) = with(binding) {
        val activeAlpha =
            if (hasCurveData) {
                1f
            } else {
                0f
            }

        viewTimelineNightStart.alpha = activeAlpha
        viewTimelineSunrise.alpha = activeAlpha
        viewTimelinePeak.alpha = activeAlpha
        viewTimelineSunset.alpha = activeAlpha
        viewTimelineNightEnd.alpha = activeAlpha
    }

    private fun setupClicks() = with(binding) {
        cardManualControl.setOnClickListener {
            openManual()
        }

        cardProgram.setOnClickListener {
            openPrograms()
        }

        cardActivePrograms.setOnClickListener {
            openPrograms()
        }

        btnEditLightCurve.setOnClickListener {
            openProgramEditor(
                programName = currentProgramName()
            )
        }

        cardQuickSetup.setOnClickListener {
            openQuickSetup()
        }

        cardPresets.setOnClickListener {
            openPresets()
        }

        programEveryDayRow.setOnClickListener {
            if (!viewModel.uiState.value.isLoading) {
                openProgramEditor(
                    programName = currentProgramName()
                )
            }
        }

        btnAddProgram.setOnClickListener {
            openProgramEditor(
                programName = DEFAULT_PROGRAM_NAME
            )
        }

        cardTemporaryModes.setOnClickListener {
            showTemporaryModeSheet()
        }

        cardDeviceHealth.setOnClickListener {
            openDeviceSettings()
        }

        switchProgramEveryDay.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticSwitchChange) {
                return@setOnCheckedChangeListener
            }

            viewModel.setProgramEnabled(
                enabled = isChecked
            )

            showMessage(
                if (isChecked) {
                    "Program enable command sent"
                } else {
                    "Program disable command sent"
                }
            )
        }

        btnScenePhoto.setOnClickListener {
            applyTemporaryScene(
                sceneName = "Photo Mode",
                outputPercent = 100,
                durationLabel = "30 min",
                resumeLabel = "Auto resumes in 30 min"
            )
        }

        btnSceneMaintenance.setOnClickListener {
            applyTemporaryScene(
                sceneName = "Care Mode",
                outputPercent = 55,
                durationLabel = "45 min",
                resumeLabel = "Auto resumes in 45 min"
            )
        }

        btnSceneEvening.setOnClickListener {
            applyTemporaryScene(
                sceneName = "Evening Mode",
                outputPercent = 45,
                durationLabel = "2 hours",
                resumeLabel = "Auto resumes in 2 hours"
            )
        }
    }

    private fun openManual() {
        findNavController().navigate(
            R.id.action_deviceLightFragment_to_deviceLightManualFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId
            )
        )
    }

    private fun openPrograms() {
        findNavController().navigate(
            R.id.action_deviceLightFragment_to_deviceLightProgramListFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId
            )
        )
    }

    private fun openProgramEditor(
        programName: String
    ) {
        findNavController().navigate(
            R.id.action_deviceLightFragment_to_deviceLightProgramEditorFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId,
                ARG_PROGRAM_NAME to programName
            )
        )
    }

    private fun openQuickSetup() {
        findNavController().navigate(
            R.id.action_deviceLightFragment_to_deviceLightQuickSetupFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId
            )
        )
    }

    private fun openPresets() {
        findNavController().navigate(
            R.id.action_deviceLightFragment_to_deviceLightPresetsFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId
            )
        )
    }

    private fun openDeviceSettings() {
        findNavController().navigate(
            R.id.action_deviceLightFragment_to_deviceLightDeviceSettingsFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId
            )
        )
    }

    private fun applyTemporaryScene(
        sceneName: String,
        outputPercent: Int,
        durationLabel: String,
        resumeLabel: String
    ) {
        viewModel.applyTemporaryScene(
            sceneName = sceneName,
            outputPercent = outputPercent,
            durationLabel = durationLabel,
            resumeLabel = resumeLabel
        )

        showMessage("$sceneName command sent")
    }

    private fun showTemporaryModeSheet() {
        val dialog = BottomSheetDialog(requireContext())

        val sheetView = layoutInflater.inflate(
            R.layout.bottom_sheet_light_temporary_mode,
            null
        )

        var selectedSceneName = "Photo Mode"
        var selectedOutputPercent = 100
        var selectedDurationLabel = "30 min"
        var selectedResumeLabel = "Auto resumes in $selectedDurationLabel"

        val tvTempDurationValue =
            sheetView.findViewById<TextView>(
                R.id.tvTempDurationValue
            )

        val btnPhoto =
            sheetView.findViewById<TextView>(
                R.id.btnTempModePhoto
            )

        val btnMaintenance =
            sheetView.findViewById<TextView>(
                R.id.btnTempModeMaintenance
            )

        val btnEvening =
            sheetView.findViewById<TextView>(
                R.id.btnTempModeEvening
            )

        val btnMoonlight =
            sheetView.findViewById<TextView>(
                R.id.btnTempModeMoonlight
            )

        val chip15 =
            sheetView.findViewById<TextView>(
                R.id.chipTempDuration15
            )

        val chip30 =
            sheetView.findViewById<TextView>(
                R.id.chipTempDuration30
            )

        val chip60 =
            sheetView.findViewById<TextView>(
                R.id.chipTempDuration60
            )

        val chipNext =
            sheetView.findViewById<TextView>(
                R.id.chipTempDurationNext
            )

        fun updateResumeLabel() {
            selectedResumeLabel =
                if (selectedDurationLabel == DURATION_NEXT_EVENT) {
                    "Auto resumes at next event"
                } else {
                    "Auto resumes in $selectedDurationLabel"
                }
        }

        btnPhoto.setOnClickListener {
            selectedSceneName = "Photo Mode"
            selectedOutputPercent = 100
            updateResumeLabel()
            showMessage("Photo Mode selected")
        }

        btnMaintenance.setOnClickListener {
            selectedSceneName = "Care Mode"
            selectedOutputPercent = 55
            updateResumeLabel()
            showMessage("Care Mode selected")
        }

        btnEvening.setOnClickListener {
            selectedSceneName = "Evening Mode"
            selectedOutputPercent = 45
            updateResumeLabel()
            showMessage("Evening Mode selected")
        }

        btnMoonlight.setOnClickListener {
            selectedSceneName = "Moonlight"
            selectedOutputPercent = 12
            updateResumeLabel()
            showMessage("Moonlight selected")
        }

        chip15.setOnClickListener {
            selectedDurationLabel = "15 min"
            tvTempDurationValue.text = selectedDurationLabel
            updateResumeLabel()
        }

        chip30.setOnClickListener {
            selectedDurationLabel = "30 min"
            tvTempDurationValue.text = selectedDurationLabel
            updateResumeLabel()
        }

        chip60.setOnClickListener {
            selectedDurationLabel = "60 min"
            tvTempDurationValue.text = selectedDurationLabel
            updateResumeLabel()
        }

        chipNext.setOnClickListener {
            selectedDurationLabel = DURATION_NEXT_EVENT
            tvTempDurationValue.text = "Until next event"
            updateResumeLabel()
        }

        sheetView.findViewById<TextView>(
            R.id.btnTempModeApply
        ).setOnClickListener {
            dialog.dismiss()

            applyTemporaryScene(
                sceneName = selectedSceneName,
                outputPercent = selectedOutputPercent,
                durationLabel = selectedDurationLabel,
                resumeLabel = selectedResumeLabel
            )
        }

        sheetView.findViewById<TextView>(
            R.id.btnTempModeRestoreAuto
        ).setOnClickListener {
            dialog.dismiss()

            viewModel.restoreAutoProgram()
            showMessage("Restore auto command sent")
        }

        sheetView.findViewById<TextView>(
            R.id.btnTempModeCancel
        ).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun currentProgramName(): String {
        val currentName = viewModel.uiState.value.activeProgramName

        return currentName
            .takeIf {
                it.isNotBlank() &&
                    it != LightOverviewUiState.NO_VALUE &&
                    it != "Waiting for program data"
            }
            ?: DEFAULT_PROGRAM_NAME
    }

    private fun setProgramSwitchChecked(
        checked: Boolean
    ) = with(binding) {
        isProgrammaticSwitchChange = true
        switchProgramEveryDay.isChecked = checked
        isProgrammaticSwitchChange = false
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
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_PROGRAM_NAME = "programName"

        private const val DEFAULT_DEVICE_TITLE = "Device"
        private const val DEFAULT_PROGRAM_NAME = "Every Day Program"
        private const val DURATION_NEXT_EVENT = "next event"

        fun newInstance(
            deviceId: Long,
            deviceTitle: String = DEFAULT_DEVICE_TITLE
        ): DeviceLightFragment {
            return DeviceLightFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DEVICE_ID, deviceId)
                    putString(ARG_DEVICE_TITLE, deviceTitle)
                }
            }
        }
    }
}