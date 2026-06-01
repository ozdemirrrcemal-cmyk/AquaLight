package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_DEVICE_ID
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_DEVICE_TITLE
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_PROGRAM_ID
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_PROGRAM_NAME
import com.aqua.aqualight.ui.tabs.devices.detail.light.sheet.LightTemporaryModeBottomSheet

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
                getString(R.string.light_default_device_title)
            }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightBinding.bind(view)

        setupHeader()
        setupClicks()
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
            // TODO: Connect refresh action when ESP32 data layer is enabled.
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
                programId = null,
                programName = getString(R.string.light_default_program_name)
            )
        }

        cardQuickSetup.setOnClickListener {
            openQuickSetup()
        }

        cardPresets.setOnClickListener {
            openPresets()
        }

        programEveryDayRow.setOnClickListener {
            openProgramEditor(
                programId = null,
                programName = getString(R.string.light_default_program_name)
            )
        }

        btnAddProgram.setOnClickListener {
            openProgramEditor(
                programId = null,
                programName = getString(R.string.light_default_program_name)
            )
        }

        cardTemporaryModes.setOnClickListener {
            showTemporaryModeSheet()
        }

        cardDeviceHealth.setOnClickListener {
            openDeviceSettings()
        }

        switchProgramEveryDay.setOnCheckedChangeListener { _, _ ->
            // TODO: Connect program enable / disable action when ESP32 command layer is enabled.
        }

        btnScenePhoto.setOnClickListener {
            showTemporaryModeSheet()
        }

        btnSceneMaintenance.setOnClickListener {
            showTemporaryModeSheet()
        }

        btnSceneEvening.setOnClickListener {
            showTemporaryModeSheet()
        }
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

    private fun showTemporaryModeSheet() {
        LightTemporaryModeBottomSheet.show(
            fragment = this,
            onApply = { _, _ ->
                // TODO: Send selected temporary mode to ViewModel when ESP32 command layer is enabled.
            },
            onRestoreAuto = {
                // TODO: Restore auto program when ESP32 command layer is enabled.
            }
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(
            deviceId: Long,
            deviceTitle: String
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