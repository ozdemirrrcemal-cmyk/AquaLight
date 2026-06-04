package com.aqua.aqualight.ui.tabs.devices.detail.light.presets

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightPresetsBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.adapter.LightPresetsAdapter
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.catalog.BuiltInLightPresets
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetCategory
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.sheet.LightPresetOptionsSheet
import kotlin.math.pow
import kotlin.math.roundToInt

class DeviceLightPresetsFragment :
    Fragment(R.layout.fragment_device_light_presets) {

    private var _binding: FragmentDeviceLightPresetsBinding? = null
    private val binding get() = _binding!!

    private lateinit var presetsAdapter: LightPresetsAdapter

    private var selectedFilter = PresetFilter.ALL
    private var allPresets: List<LightPresetItem> = emptyList()
    private var activePreset: LightPresetItem? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightPresetsBinding.bind(view)

        setupHeader()
        setupRecyclerView()
        setupClicks()
        loadPresets()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            AquaHeaderConfig(
                title = "Presets & Scenes",
                showBackButton = true,
                onBackClick = {
                    findNavController().popBackStack()
                }
            )
        )
    }

    private fun setupRecyclerView() {
        presetsAdapter = LightPresetsAdapter(
            onPresetClick = { preset ->
                showPresetOptionsSheet(preset)
            },
            onPresetOptionsClick = { preset ->
                showPresetOptionsSheet(preset)
            }
        )

        binding.presetsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = presetsAdapter
        }
    }

    private fun setupClicks() {
        binding.filterAll.setOnClickListener {
            applyFilter(PresetFilter.ALL)
        }

        binding.filterBuiltIn.setOnClickListener {
            applyFilter(PresetFilter.BUILT_IN)
        }

        binding.filterMyPresets.setOnClickListener {
            applyFilter(PresetFilter.CUSTOM)
        }
    }

    private fun loadPresets() {
        // TODO: Add user-created presets from DataStore later.
        val customPresets = emptyList<LightPresetItem>()

        allPresets = BuiltInLightPresets.presets + customPresets

        applyFilter(selectedFilter)
        renderActivePreset(activePreset)
    }

    private fun applyFilter(
        filter: PresetFilter
    ) {
        selectedFilter = filter
        updateFilterUi(filter)

        val filtered = when (filter) {
            PresetFilter.ALL -> allPresets
            PresetFilter.BUILT_IN -> allPresets.filter {
                it.category == LightPresetCategory.BUILT_IN
            }
            PresetFilter.CUSTOM -> allPresets.filter {
                it.category == LightPresetCategory.CUSTOM
            }
        }

        if (filtered.isEmpty()) {
            binding.presetsRecyclerView.visibility = View.GONE
            binding.emptyPresetsContainer.visibility = View.VISIBLE
            presetsAdapter.submitList(emptyList())
        } else {
            binding.emptyPresetsContainer.visibility = View.GONE
            binding.presetsRecyclerView.visibility = View.VISIBLE
            presetsAdapter.submitList(filtered)
        }
    }

    private fun updateFilterUi(
        filter: PresetFilter
    ) {
        val selectedBg = R.drawable.bg_light_filter_selected
        val transparentBg = android.R.color.transparent

        val selectedText = requireContext().getColor(R.color.light_button_on_primary)
        val normalText = requireContext().getColor(R.color.light_text_secondary)

        binding.filterAll.setBackgroundResource(
            if (filter == PresetFilter.ALL) selectedBg else transparentBg
        )
        binding.filterBuiltIn.setBackgroundResource(
            if (filter == PresetFilter.BUILT_IN) selectedBg else transparentBg
        )
        binding.filterMyPresets.setBackgroundResource(
            if (filter == PresetFilter.CUSTOM) selectedBg else transparentBg
        )

        binding.filterAll.setTextColor(
            if (filter == PresetFilter.ALL) selectedText else normalText
        )
        binding.filterBuiltIn.setTextColor(
            if (filter == PresetFilter.BUILT_IN) selectedText else normalText
        )
        binding.filterMyPresets.setTextColor(
            if (filter == PresetFilter.CUSTOM) selectedText else normalText
        )
    }

    private fun showPresetOptionsSheet(
        preset: LightPresetItem
    ) {
        LightPresetOptionsSheet
            .create(requireContext())
            .show(
                presetName = preset.title,
                subtitle = "R${preset.red} · G${preset.green} · B${preset.blue} · W${preset.white}",
                isCustom = preset.isCustom,
                onApply = {
                    applyPresetToDevice(preset)
                },
                onDelete = {
                    deletePreset(preset)
                }
            )
    }

    private fun applyPresetToDevice(
        preset: LightPresetItem
    ) {
        activePreset = preset
        renderActivePreset(preset)

        // TODO: Send preset RGBW values to ESP32 manual output.
        Toast.makeText(
            requireContext(),
            "${preset.title} applied",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun deletePreset(
        preset: LightPresetItem
    ) {
        if (!preset.isCustom) return

        // TODO: Delete custom preset from DataStore later.
        Toast.makeText(
            requireContext(),
            "${preset.title} deleted",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun renderActivePreset(
        preset: LightPresetItem?
    ) {
        if (preset == null) {
            binding.tvActivePresetTitle.text = "No preset applied"
            binding.tvActivePresetChannels.text = "Select a preset to apply"
            binding.viewActivePresetColor.background = createColorDrawable(
                Color.TRANSPARENT
            )
            return
        }

        binding.tvActivePresetTitle.text = preset.title
        binding.tvActivePresetChannels.text =
            "R${preset.red} · G${preset.green} · B${preset.blue} · W${preset.white}"

        binding.viewActivePresetColor.background = createColorDrawable(
            calculatePreviewColor(
                red = preset.red,
                green = preset.green,
                blue = preset.blue,
                white = preset.white
            )
        )
    }

    private fun createColorDrawable(
        color: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun calculatePreviewColor(
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ): Int {
        val r = red.coerceIn(0, 100) / 100.0
        val g = green.coerceIn(0, 100) / 100.0
        val b = blue.coerceIn(0, 100) / 100.0
        val w = white.coerceIn(0, 100) / 100.0

        val redColor = Triple(1.00, 0.08, 0.03)
        val greenColor = Triple(0.12, 1.00, 0.20)
        val blueColor = Triple(0.05, 0.28, 1.00)
        val whiteColor = Triple(0.92, 0.96, 1.00)

        val linearRed =
            redColor.first * r +
                greenColor.first * g +
                blueColor.first * b +
                whiteColor.first * w

        val linearGreen =
            redColor.second * r +
                greenColor.second * g +
                blueColor.second * b +
                whiteColor.second * w

        val linearBlue =
            redColor.third * r +
                greenColor.third * g +
                blueColor.third * b +
                whiteColor.third * w

        val max = maxOf(linearRed, linearGreen, linearBlue, 1.0)

        fun gammaCorrect(value: Double): Int {
            val normalized = (value / max).coerceIn(0.0, 1.0)
            return (255.0 * normalized.pow(1.0 / 2.2))
                .roundToInt()
                .coerceIn(0, 255)
        }

        return Color.rgb(
            gammaCorrect(linearRed),
            gammaCorrect(linearGreen),
            gammaCorrect(linearBlue)
        )
    }

    override fun onDestroyView() {
        binding.presetsRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private enum class PresetFilter {
        ALL,
        BUILT_IN,
        CUSTOM
    }
}