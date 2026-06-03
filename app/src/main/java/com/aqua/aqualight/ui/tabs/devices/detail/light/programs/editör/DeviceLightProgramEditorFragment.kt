package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightProgramEditorBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class DeviceLightProgramEditorFragment :
    Fragment(R.layout.fragment_device_light_program_editor) {

    private var _binding: FragmentDeviceLightProgramEditorBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightProgramEditorBinding.bind(view)

        setupHeader()
        setupProgramSettingsRows()
        setupClicks()
        setupSliders()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            AquaHeaderConfig(
                title = "Program Editor",
                showBackButton = true,
                onBackClick = {
                    findNavController().popBackStack()
                }
            )
        )
    }

    private fun setupProgramSettingsRows() {
        bindActionRow(
            row = binding.actionMoonlight.root,
            icon = "◐",
            title = "Moonlight",
            subtitle = "Soft output after sunset"
        )

        bindActionRow(
            row = binding.actionCloudSimulation.root,
            icon = "☁",
            title = "Cloud Simulation",
            subtitle = "Natural light variation"
        )

        bindActionRow(
            row = binding.actionTransitionSmoothing.root,
            icon = "≈",
            title = "Transition Smoothing",
            subtitle = "Make ramps feel more natural"
        )

        bindActionRow(
            row = binding.actionNaturalVariation.root,
            icon = "✦",
            title = "Natural Variation",
            subtitle = "Subtle randomized daily output"
        )
    }

    private fun bindActionRow(
        row: View,
        icon: String,
        title: String,
        subtitle: String
    ) {
        row.findViewById<TextView>(R.id.tvActionIcon)?.text = icon
        row.findViewById<TextView>(R.id.tvActionTitle)?.text = title
        row.findViewById<TextView>(R.id.tvActionSubtitle)?.text = subtitle
    }

    private fun setupClicks() {
        binding.btnPreviewProgram.setOnClickListener {
            Toast.makeText(requireContext(), "Preview program", Toast.LENGTH_SHORT).show()
        }

        binding.tvTimeStart.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Start time", Toast.LENGTH_SHORT).show()
        }

        binding.tvTimePeakStart.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Peak Start time", Toast.LENGTH_SHORT).show()
        }

        binding.tvTimePeakEnd.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Peak End time", Toast.LENGTH_SHORT).show()
        }

        binding.tvTimeEnd.setOnClickListener {
            Toast.makeText(requireContext(), "Edit End time", Toast.LENGTH_SHORT).show()
        }

        binding.repeatEvery.setOnClickListener {
            Toast.makeText(requireContext(), "Repeat: Every", Toast.LENGTH_SHORT).show()
        }

        binding.repeatWeekdays.setOnClickListener {
            Toast.makeText(requireContext(), "Repeat: Weekdays", Toast.LENGTH_SHORT).show()
        }

        binding.repeatWeekend.setOnClickListener {
            Toast.makeText(requireContext(), "Repeat: Weekend", Toast.LENGTH_SHORT).show()
        }

        binding.repeatCustom.setOnClickListener {
            Toast.makeText(requireContext(), "Custom days", Toast.LENGTH_SHORT).show()
        }

        binding.actionMoonlight.root.setOnClickListener {
            Toast.makeText(requireContext(), "Moonlight", Toast.LENGTH_SHORT).show()
        }

        binding.actionCloudSimulation.root.setOnClickListener {
            Toast.makeText(requireContext(), "Cloud Simulation", Toast.LENGTH_SHORT).show()
        }

        binding.actionTransitionSmoothing.root.setOnClickListener {
            Toast.makeText(requireContext(), "Transition Smoothing", Toast.LENGTH_SHORT).show()
        }

        binding.actionNaturalVariation.root.setOnClickListener {
            Toast.makeText(requireContext(), "Natural Variation", Toast.LENGTH_SHORT).show()
        }

        binding.btnLoadToDevice.setOnClickListener {
            Toast.makeText(requireContext(), "Load to device", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        binding.btnSaveAs.setOnClickListener {
            Toast.makeText(requireContext(), "Save as", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSliders() {
        binding.sliderRed.addOnChangeListener { _, value, _ ->
            binding.tvRedValue.text = "Red ${value.toInt()}%"
        }

        binding.sliderGreen.addOnChangeListener { _, value, _ ->
            binding.tvGreenValue.text = "Green ${value.toInt()}%"
        }

        binding.sliderBlue.addOnChangeListener { _, value, _ ->
            binding.tvBlueValue.text = "Blue ${value.toInt()}%"
        }

        binding.sliderWhite.addOnChangeListener { _, value, _ ->
            binding.tvWhiteValue.text = "White ${value.toInt()}%"
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}