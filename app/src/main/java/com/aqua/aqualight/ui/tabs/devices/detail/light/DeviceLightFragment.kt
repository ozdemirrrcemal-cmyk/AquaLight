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
            hasCurveData = state.isTimelineActive && !state.isLoading
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
                programId = currentProgramId(),
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
                    programId = currentProgramId(),
                    programName = currentProgramName()
                )
            }
        }

        btnAddProgram.setOnClickListener {
            openProgramEditor(
                programId = null,
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

            setActiveProgramEnabled(
                enabled = isChecked
            )
        }

        btnScenePhoto.setOnClickListener {
            applyTemporaryScene(
                scene = TemporaryLightScene.photoMode(),
                duration = TemporaryDuration.minutes(30)
            )
        }

        btnSceneMaintenance.setOnClickListener {
            applyTemporaryScene(
                scene = TemporaryLightScene.careMode(),
                duration = TemporaryDuration.minutes(45)
            )
        }

        btnSceneEvening.setOnClickListener {
            applyTemporaryScene(
                scene = TemporaryLightScene.eveningMode(),
                duration = TemporaryDuration.hours(2)
            )
        }
    }

    private fun setActiveProgramEnabled(
        enabled: Boolean
    ) {
        val programId = currentProgramId()

        // Veri bağlanınca program enable/disable komutu programId ile gönderilmeli.
        // Şu an mevcut ViewModel imzasını bozmamak için aktif program üzerinden çalışıyor.
        viewModel.setProgramEnabled(
            enabled = enabled
        )

        showMessage(
            if (enabled) {
                "Program enable command sent"
            } else {
                "Program disable command sent"
            }
        )
    }

    private fun openManual() {
        findNavController().navigate(
            R.id.deviceLightManualFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId,
                ARG_DEVICE_TITLE to deviceTitle
            )
        )
    }

    private fun openPrograms() {
        findNavController().navigate(
            R.id.deviceLightProgramListFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId,
                ARG_DEVICE_TITLE to deviceTitle
            )
        )
    }

    private fun openProgramEditor(
        programId: String?,
        programName: String
    ) {
        findNavController().navigate(
            R.id.deviceLightProgramEditorFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId,
                ARG_DEVICE_TITLE to deviceTitle,
                ARG_PROGRAM_ID to programId,
                ARG_PROGRAM_NAME to programName
            )
        )
    }

    private fun openQuickSetup() {
        findNavController().navigate(
            R.id.deviceLightQuickSetupFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId,
                ARG_DEVICE_TITLE to deviceTitle
            )
        )
    }

    private fun openPresets() {
        findNavController().navigate(
            R.id.deviceLightPresetsFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId,
                ARG_DEVICE_TITLE to deviceTitle
            )
        )
    }

    private fun openDeviceSettings() {
        findNavController().navigate(
            R.id.deviceLightDeviceSettingsFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId,
                ARG_DEVICE_TITLE to deviceTitle
            )
        )
    }

    private fun applyTemporaryScene(
        scene: TemporaryLightScene,
        duration: TemporaryDuration
    ) {
        val resumeLabel =
            if (duration.untilNextEvent) {
                "Auto resumes at next event"
            } else {
                "Auto resumes in ${duration.label}"
            }

        // Veri bağlanınca burada ESP32'ye şu model gönderilmeli:
        // scene.master, scene.red, scene.green, scene.blue, scene.white,
        // duration.minutes veya duration.untilNextEvent.
        //
        // Şu an mevcut ViewModel imzası korunuyor.
        viewModel.applyTemporaryScene(
            sceneName = scene.name,
            outputPercent = scene.master,
            durationLabel = duration.label,
            resumeLabel = resumeLabel
        )

        showMessage("${scene.name} command sent")
    }

    private fun showTemporaryModeSheet() {
        val dialog = BottomSheetDialog(requireContext())

        val sheetView = layoutInflater.inflate(
            R.layout.bottom_sheet_light_temporary_mode,
            null
        )

        var selectedScene = TemporaryLightScene.photoMode()
        var selectedDuration = TemporaryDuration.minutes(30)

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

        btnPhoto.setOnClickListener {
            selectedScene = TemporaryLightScene.photoMode()
            showMessage("${selectedScene.name} selected")
        }

        btnMaintenance.setOnClickListener {
            selectedScene = TemporaryLightScene.careMode()
            showMessage("${selectedScene.name} selected")
        }

        btnEvening.setOnClickListener {
            selectedScene = TemporaryLightScene.eveningMode()
            showMessage("${selectedScene.name} selected")
        }

        btnMoonlight.setOnClickListener {
            selectedScene = TemporaryLightScene.moonlight()
            showMessage("${selectedScene.name} selected")
        }

        chip15.setOnClickListener {
            selectedDuration = TemporaryDuration.minutes(15)
            tvTempDurationValue.text = selectedDuration.displayLabel
        }

        chip30.setOnClickListener {
            selectedDuration = TemporaryDuration.minutes(30)
            tvTempDurationValue.text = selectedDuration.displayLabel
        }

        chip60.setOnClickListener {
            selectedDuration = TemporaryDuration.minutes(60)
            tvTempDurationValue.text = selectedDuration.displayLabel
        }

        chipNext.setOnClickListener {
            selectedDuration = TemporaryDuration.nextEvent()
            tvTempDurationValue.text = selectedDuration.displayLabel
        }

        sheetView.findViewById<TextView>(
            R.id.btnTempModeApply
        ).setOnClickListener {
            dialog.dismiss()

            applyTemporaryScene(
                scene = selectedScene,
                duration = selectedDuration
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

    private fun currentProgramId(): String? {
        // Veri bağlanınca LightOverviewUiState içine activeProgramId eklenip buradan döndürülmeli.
        // Örnek:
        // return viewModel.uiState.value.activeProgramId
        return null
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

        try {
            switchProgramEveryDay.isChecked = checked
        } finally {
            isProgrammaticSwitchChange = false
        }
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

    private data class TemporaryLightScene(
        val name: String,
        val master: Int,
        val red: Int,
        val green: Int,
        val blue: Int,
        val white: Int
    ) {

        companion object {
            fun photoMode(): TemporaryLightScene {
                return TemporaryLightScene(
                    name = "Photo Mode",
                    master = 100,
                    red = 90,
                    green = 92,
                    blue = 90,
                    white = 100
                )
            }

            fun careMode(): TemporaryLightScene {
                return TemporaryLightScene(
                    name = "Care Mode",
                    master = 55,
                    red = 70,
                    green = 70,
                    blue = 70,
                    white = 85
                )
            }

            fun eveningMode(): TemporaryLightScene {
                return TemporaryLightScene(
                    name = "Evening Mode",
                    master = 45,
                    red = 80,
                    green = 55,
                    blue = 35,
                    white = 30
                )
            }

            fun moonlight(): TemporaryLightScene {
                return TemporaryLightScene(
                    name = "Moonlight",
                    master = 12,
                    red = 10,
                    green = 18,
                    blue = 70,
                    white = 5
                )
            }
        }
    }

    private data class TemporaryDuration(
        val label: String,
        val displayLabel: String,
        val minutes: Int?,
        val untilNextEvent: Boolean
    ) {

        companion object {
            fun minutes(
                value: Int
            ): TemporaryDuration {
                return TemporaryDuration(
                    label = "$value min",
                    displayLabel = "$value min",
                    minutes = value,
                    untilNextEvent = false
                )
            }

            fun hours(
                value: Int
            ): TemporaryDuration {
                return TemporaryDuration(
                    label = "$value hours",
                    displayLabel = "$value hours",
                    minutes = value * 60,
                    untilNextEvent = false
                )
            }

            fun nextEvent(): TemporaryDuration {
                return TemporaryDuration(
                    label = DURATION_NEXT_EVENT,
                    displayLabel = "Until next event",
                    minutes = null,
                    untilNextEvent = true
                )
            }
        }
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_PROGRAM_ID = "programId"
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