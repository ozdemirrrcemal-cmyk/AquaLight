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

    private var isSimpleCurveMode: Boolean = true

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightProgramEditorBinding.bind(view)

        setupHeader()
        setupCurvePointRows()
        setupActionRows()
        setupClicks()
        updateEditorModeUi()
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

    private fun setupCurvePointRows() {
    data class CurvePointPreview(
        val title: String,
        val subtitle: String,
        val time: String,
        val brightness: String
    )

    val rows = listOf(
        CurvePointPreview("Start", "Sunrise begins", "07:00", "0%"),
        CurvePointPreview("Peak Start", "Ramp reaches target output", "09:00", "80%"),
        CurvePointPreview("Peak End", "Stable peak lighting ends", "17:00", "80%"),
        CurvePointPreview("End", "Lights fade out", "20:00", "0%")
    )

    for (index in 0 until binding.curvePointsContainer.childCount) {
        val row = binding.curvePointsContainer.getChildAt(index)
        val data = rows.getOrNull(index) ?: continue

        row.findViewById<TextView>(R.id.tvPointTitle)?.text = data.title
        row.findViewById<TextView>(R.id.tvPointSubtitle)?.text = data.subtitle
        row.findViewById<TextView>(R.id.tvPointTime)?.text = data.time
        row.findViewById<TextView>(R.id.tvPointBrightness)?.text = data.brightness
    }
}

    private fun setupActionRows() {
        bindActionRow(
            row = binding.actionCloudSimulation.root,
            icon = "☁",
            title = "Cloud Simulation",
            subtitle = "Natural light variation"
        )

        bindActionRow(
            row = binding.actionMoonlight.root,
            icon = "◐",
            title = "Moonlight",
            subtitle = "Soft output after sunset"
        )

        bindActionRow(
            row = binding.actionProgramAdvanced.root,
            icon = "⚙",
            title = "Advanced Options",
            subtitle = "Fine-tune program behavior"
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
        binding.btnSimpleCurve.setOnClickListener {
            isSimpleCurveMode = true
            updateEditorModeUi()
        }

        binding.btnProChannels.setOnClickListener {
            isSimpleCurveMode = false
            updateEditorModeUi()
        }

        binding.btnRenameProgram.setOnClickListener {
            Toast.makeText(requireContext(), "Rename program", Toast.LENGTH_SHORT).show()
        }

        binding.cardPresetSelector.setOnClickListener {
            Toast.makeText(requireContext(), "Select preset", Toast.LENGTH_SHORT).show()
        }

        binding.btnPreviewDay.setOnClickListener {
            Toast.makeText(requireContext(), "Preview Day", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddCurvePoint.setOnClickListener {
            Toast.makeText(requireContext(), "Add point", Toast.LENGTH_SHORT).show()
        }

        binding.actionCloudSimulation.root.setOnClickListener {
            Toast.makeText(requireContext(), "Cloud Simulation", Toast.LENGTH_SHORT).show()
        }

        binding.actionMoonlight.root.setOnClickListener {
            Toast.makeText(requireContext(), "Moonlight", Toast.LENGTH_SHORT).show()
        }

        binding.actionProgramAdvanced.root.setOnClickListener {
            Toast.makeText(requireContext(), "Advanced Options", Toast.LENGTH_SHORT).show()
        }

        binding.btnDiscardProgram.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnSaveProgram.setOnClickListener {
            Toast.makeText(requireContext(), "Program saved", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private fun updateEditorModeUi() {
        val selectedBg = R.drawable.bg_light_filter_selected
        val transparentBg = android.R.color.transparent

        val selectedText = requireContext().getColor(R.color.light_button_on_primary)
        val normalText = requireContext().getColor(R.color.light_text_secondary)

        binding.btnSimpleCurve.setBackgroundResource(
            if (isSimpleCurveMode) selectedBg else transparentBg
        )
        binding.btnSimpleCurve.setTextColor(
            if (isSimpleCurveMode) selectedText else normalText
        )

        binding.btnProChannels.setBackgroundResource(
            if (!isSimpleCurveMode) selectedBg else transparentBg
        )
        binding.btnProChannels.setTextColor(
            if (!isSimpleCurveMode) selectedText else normalText
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}