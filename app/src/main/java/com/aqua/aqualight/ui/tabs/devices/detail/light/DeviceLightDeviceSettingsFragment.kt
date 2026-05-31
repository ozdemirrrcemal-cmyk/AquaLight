package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightDeviceSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.roundToInt

class DeviceLightDeviceSettingsFragment :
    Fragment(R.layout.fragment_device_light_device_settings) {

    private var _binding: FragmentDeviceLightDeviceSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDeviceLightDeviceSettingsBinding.bind(view)

        configureSliderRanges()
        renderPreviewState()
        setupSafetySlider()
        setupClicks()
    }

    fun onHeaderSyncClick() {
        if (_binding == null) {
            return
        }

        showMessage(
            message = "Device data sync will be connected later"
        )
    }

    private fun configureSliderRanges() = with(binding) {
        sliderMaxBrightness.valueFrom = 30f
        sliderMaxBrightness.valueTo = 100f
        sliderMaxBrightness.stepSize = 1f
    }

    private fun renderPreviewState() = with(binding) {
        tvLightDeviceName.text = "WRGB Light"
        tvLightDeviceTank.text = "Assigned to Living Tank"
        tvDeviceOnlineState.text = "ONLINE"

        tvLightDeviceIp.text = "IP\n192.168.1.42"
        tvLightDeviceFirmware.text = "Firmware\n1.0.4"
        tvLightDeviceSignal.text = "Signal\nGood"

        tvCurrentDeviceTime.text = "Device Time\n14:32"
        tvDeviceTimeSource.text = "Source\nPhone Sync"

        sliderMaxBrightness.value = 100f
        tvMaxBrightnessValue.text = "Max Brightness Limit 100%"

        switchTemperatureProtection.isChecked = true
        tvFanControlValue.text = "Auto"
    }

    private fun setupSafetySlider() = with(binding) {
        sliderMaxBrightness.addOnChangeListener { _, value, _ ->
            tvMaxBrightnessValue.text =
                "Max Brightness Limit ${value.roundToInt()}%"
        }
    }

    private fun setupClicks() = with(binding) {
        btnRenameLightDevice.setOnClickListener {
            showRenameDeviceSheet()
        }

        btnSyncDeviceTime.setOnClickListener {
            showMessage(
                message = "Device time sync will be connected later"
            )
        }

        switchTemperatureProtection.setOnCheckedChangeListener { _, isChecked ->
            showMessage(
                message = if (isChecked) {
                    "Temperature protection enabled"
                } else {
                    "Temperature protection disabled"
                }
            )
        }

        rowFanControl.setOnClickListener {
            showFanControlSheet()
        }

        btnRestartLightDevice.setOnClickListener {
            showMessage(
                message = "Restart command will be connected later"
            )
        }

        btnFactoryResetLightSettings.setOnClickListener {
            showMessage(
                message = "Factory reset confirmation will be added"
            )
        }

        btnRemoveLightDevice.setOnClickListener {
            showMessage(
                message = "Remove device confirmation will be added"
            )
        }
    }

    private fun showRenameDeviceSheet() = with(binding) {
        val dialog =
            BottomSheetDialog(
                requireContext()
            )

        val sheetView =
            layoutInflater.inflate(
                R.layout.bottom_sheet_light_rename_device,
                null
            )

        val editDeviceName =
            sheetView.findViewById<EditText>(
                R.id.editLightDeviceName
            )

        val btnSave =
            sheetView.findViewById<TextView>(
                R.id.btnRenameDeviceSave
            )

        val btnCancel =
            sheetView.findViewById<TextView>(
                R.id.btnRenameDeviceCancel
            )

        val currentName =
            tvLightDeviceName.text.toString()

        editDeviceName.setText(
            currentName
        )

        editDeviceName.setSelection(
            editDeviceName.text.length
        )

        btnSave.setOnClickListener {
            val newName =
                editDeviceName.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

            if (newName.isBlank()) {
                showMessage(
                    message = "Device name cannot be empty"
                )
                return@setOnClickListener
            }

            tvLightDeviceName.text = newName

            dialog.dismiss()

            showMessage(
                message = "Device renamed to $newName"
            )
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(
            sheetView
        )

        dialog.show()
    }

    private fun showFanControlSheet() = with(binding) {
        val dialog =
            BottomSheetDialog(
                requireContext()
            )

        val sheetView =
            layoutInflater.inflate(
                R.layout.bottom_sheet_light_fan_control,
                null
            )

        val tvSelectedMode =
            sheetView.findViewById<TextView>(
                R.id.tvFanSelectedMode
            )

        val btnAuto =
            sheetView.findViewById<TextView>(
                R.id.btnFanModeAuto
            )

        val btnSilent =
            sheetView.findViewById<TextView>(
                R.id.btnFanModeSilent
            )

        val btnPerformance =
            sheetView.findViewById<TextView>(
                R.id.btnFanModePerformance
            )

        val btnAlwaysOn =
            sheetView.findViewById<TextView>(
                R.id.btnFanModeAlwaysOn
            )

        val btnOff =
            sheetView.findViewById<TextView>(
                R.id.btnFanModeOff
            )

        val btnSave =
            sheetView.findViewById<TextView>(
                R.id.btnFanControlSave
            )

        val btnCancel =
            sheetView.findViewById<TextView>(
                R.id.btnFanControlCancel
            )

        var selectedMode =
            tvFanControlValue.text
                ?.toString()
                ?.ifBlank {
                    "Auto"
                }
                ?: "Auto"

        fun selectedDescriptionFor(
            mode: String
        ): String {
            return when (mode) {
                "Silent" -> {
                    "Lower fan speed"
                }

                "Performance" -> {
                    "Strong cooling"
                }

                "Always On" -> {
                    "Continuous cooling"
                }

                "Off" -> {
                    "Fan disabled"
                }

                else -> {
                    "Balanced cooling"
                }
            }
        }

        fun updateSelectedMode(
            mode: String
        ) {
            selectedMode = mode
            tvSelectedMode.text =
                "$mode · ${selectedDescriptionFor(mode)}"
        }

        updateSelectedMode(
            mode = selectedMode
        )

        btnAuto.setOnClickListener {
            updateSelectedMode(
                mode = "Auto"
            )
        }

        btnSilent.setOnClickListener {
            updateSelectedMode(
                mode = "Silent"
            )
        }

        btnPerformance.setOnClickListener {
            updateSelectedMode(
                mode = "Performance"
            )
        }

        btnAlwaysOn.setOnClickListener {
            updateSelectedMode(
                mode = "Always On"
            )
        }

        btnOff.setOnClickListener {
            updateSelectedMode(
                mode = "Off"
            )
        }

        btnSave.setOnClickListener {
            tvFanControlValue.text = selectedMode

            dialog.dismiss()

            showMessage(
                message = "Fan mode set to $selectedMode"
            )
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(
            sheetView
        )

        dialog.show()
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

        fun newInstance(
            deviceId: Long
        ): DeviceLightDeviceSettingsFragment {
            return DeviceLightDeviceSettingsFragment().apply {
                arguments = Bundle().apply {
                    putLong(
                        ARG_DEVICE_ID,
                        deviceId
                    )
                }
            }
        }
    }
}