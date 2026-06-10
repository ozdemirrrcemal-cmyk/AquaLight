package com.aqua.aqualight.ui.tabs.devices.detail.light.presets

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightPresetsBinding
import com.aqua.aqualight.data.devices.light.presets.model.SavedLightPreset
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceConfirmBottomSheet
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceConfirmTone
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceFeedbackType
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceLoading
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceSnack
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.adapter.LightPresetsAdapter
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.catalog.BuiltInLightPresets
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.DeviceLightPresetsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetCategory
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.sheet.LightPresetOptionsSheet
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

class DeviceLightPresetsFragment :
    Fragment(R.layout.fragment_device_light_presets) {

    private var _binding: FragmentDeviceLightPresetsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightPresetsViewModel by viewModels()

    private lateinit var presetsAdapter: LightPresetsAdapter

    private var selectedFilter = PresetFilter.ALL
    private var allPresets: List<LightPresetItem> = emptyList()
    private var activePreset: LightPresetItem? = null

    private val deviceId: Long
        get() = arguments?.getLong(ARG_DEVICE_ID, 0L) ?: 0L

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightPresetsBinding.bind(view)

        viewModel.initialize(deviceId)

        setupHeader()
        setupRecyclerView()
        setupClicks()
        observePresets()
        observeEvents()
    }

    override fun onResume() {
        super.onResume()
        syncActivePresetFromRuntime()
    }

    private fun setupHeader() {
    binding.appHeader.setupAquaHeader(
        fragment = this,
        config = AquaHeaderConfig(
            titleOverride = "Presets & Scenes"
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

    private fun observePresets() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.presetsFlow.collect { savedPresets ->
                    val customPresets = savedPresets.map { preset ->
                        toCustomPresetItem(preset)
                    }

                    allPresets = BuiltInLightPresets.presets + customPresets

                    syncActivePresetFromRuntime()
                    applyFilter(selectedFilter)
                }
            }
        }
    }


    private fun toCustomPresetItem(
        preset: SavedLightPreset
    ): LightPresetItem {
        return LightPresetItem(
            id = preset.id,
            title = preset.name,
            subtitle = "Custom saved preset",
            category = LightPresetCategory.CUSTOM,
            red = preset.red,
            green = preset.green,
            blue = preset.blue,
            white = preset.white
        )
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is DeviceLightPresetsEvent.ShowMessage -> {
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.SUCCESS
                            )
                        }

                        is DeviceLightPresetsEvent.ShowError -> {
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.ERROR
                            )
                        }

                        is DeviceLightPresetsEvent.SetLoading -> {
                            showDeviceLoading(event.isLoading)
                        }

                        DeviceLightPresetsEvent.NavigateToManualControl -> {
                            navigateToManualControl()
                        }
                    }
                }
            }
        }
    }

    private fun syncActivePresetFromRuntime() {
        if (_binding == null) {
            return
        }

        if (deviceId <= 0L) {
            renderActivePreset(activePreset)
            return
        }

        val runtime = viewModel.currentManualRuntime()

        if (!runtime.isManualScene) {
            activePreset = null
            renderActivePreset(null)
            return
        }

        val matchedPreset = allPresets.firstOrNull { preset ->
            preset.title == runtime.activeSceneName &&
                preset.red == runtime.red &&
                preset.green == runtime.green &&
                preset.blue == runtime.blue &&
                preset.white == runtime.white
        }

        activePreset = matchedPreset ?: LightPresetItem(
            id = "runtime_manual_scene_${runtime.deviceId}",
            title = runtime.activeSceneName.orEmpty().ifBlank {
                "Manual Scene"
            },
            subtitle = "Applied manual scene",
            category = LightPresetCategory.CUSTOM,
            red = runtime.red,
            green = runtime.green,
            blue = runtime.blue,
            white = runtime.white
        )

        renderActivePreset(activePreset)
    }

    private fun applyFilter(
        filter: PresetFilter
    ) {
        selectedFilter = filter
        updateFilterUi(filter)

        val filtered = when (filter) {
            PresetFilter.ALL -> {
                allPresets
            }

            PresetFilter.BUILT_IN -> {
                allPresets.filter { preset ->
                    preset.category == LightPresetCategory.BUILT_IN
                }
            }

            PresetFilter.CUSTOM -> {
                allPresets.filter { preset ->
                    preset.category == LightPresetCategory.CUSTOM
                }
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

        val selectedText =
            requireContext().getColor(R.color.light_button_on_primary)

        val normalText =
            requireContext().getColor(R.color.light_text_secondary)

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
                onDeleteRequested = {
                    confirmDeletePreset(preset)
                }
            )
    }

    private fun applyPresetToDevice(
        preset: LightPresetItem
    ) {
        viewModel.applyPresetToDevice(preset)
    }

    private fun confirmDeletePreset(
        preset: LightPresetItem
    ) {
        if (!preset.isCustom) {
            return
        }

        val isCurrentlyActive =
            activePreset?.id == preset.id

        val message = if (isCurrentlyActive) {
            "This preset is currently active. Deleting it removes it from saved presets, but current light output will not change."
        } else {
            "This removes the preset from your saved presets."
        }

        DeviceConfirmBottomSheet
            .create(requireContext())
            .show(
                title = "Delete preset?",
                message = message,
                confirmText = "Delete",
                cancelText = "Cancel",
                tone = DeviceConfirmTone.DANGER,
                onConfirm = {
                    deletePreset(preset)
                }
            )
    }

    private fun deletePreset(
        preset: LightPresetItem
    ) {
        if (!preset.isCustom) {
            return
        }

        viewModel.deletePreset(preset)
    }

    private fun navigateToManualControl() {
        val bundle = Bundle().apply {
            putLong(ARG_DEVICE_ID, deviceId)
        }

        val navOptions = NavOptions.Builder()
            .setPopUpTo(
                R.id.deviceLightPresetsFragment,
                true
            )
            .build()

        findNavController().navigate(
            R.id.action_deviceLightPresetsFragment_to_deviceLightManualFragment,
            bundle,
            navOptions
        )
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

        val max = maxOf(
            linearRed,
            linearGreen,
            linearBlue,
            1.0
        )

        fun gammaCorrect(
            value: Double
        ): Int {
            val normalized =
                (value / max).coerceIn(0.0, 1.0)

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

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
    }
}