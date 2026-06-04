package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightQuickSetupBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.DeviceLightQuickSetupEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupRecommendation
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupUiState
import kotlinx.coroutines.launch

class DeviceLightQuickSetupFragment :
Fragment(R.layout.fragment_device_light_quick_setup) {

    private var _binding: FragmentDeviceLightQuickSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightQuickSetupViewModel by viewModels()

    private val deviceId: Long
    get() = requireArguments().getLong(ARG_DEVICE_ID, 0L)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightQuickSetupBinding.bind(view)

        setupHeader()
        setupClicks()
        observeUiState()
        observeEvents()

        viewModel.initialize(deviceId)
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            AquaHeaderConfig(
                title = "Smart Quick Setup",
                showBackButton = true,
                onBackClick = {
                    findNavController().popBackStack()
                }
            )
        )
    }

    private fun setupClicks() {
        binding.btnSaveProgram.setOnClickListener {
            viewModel.saveProgram()
        }

        binding.btnLoadToDevice.setOnClickListener {
            viewModel.loadToDevice()
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
                        is DeviceLightQuickSetupEvent.ShowMessage -> {
                            Toast.makeText(
                                requireContext(),
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        is DeviceLightQuickSetupEvent.ShowError -> {
                            Toast.makeText(
                                requireContext(),
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        DeviceLightQuickSetupEvent.NavigateBack -> {
                            findNavController().popBackStack()
                        }
                    }
                }
            }
        }
    }

    private fun renderUiState(
        state: QuickSetupUiState
    ) {
        val recommendation = state.recommendation

        if (recommendation == null) {
            renderNoLinkedTank(
                message = state.errorMessage
                ?: "Link this light device to a tank to generate a smart lighting setup."
            )
            return
        }

        binding.noLinkedTankContainer.visibility = View.GONE
        binding.recommendationContainer.visibility = View.VISIBLE
        binding.bottomActionsBar.visibility = View.VISIBLE

        renderRecommendation(recommendation)
        renderActionButtons(state)
    }

    private fun renderNoLinkedTank(
        message: String
    ) {
        binding.noLinkedTankContainer.visibility = View.VISIBLE
        binding.recommendationContainer.visibility = View.GONE
        binding.bottomActionsBar.visibility = View.GONE

        binding.tvNoLinkedTankMessage.text = message
    }

    private fun renderRecommendation(
        recommendation: QuickSetupRecommendation
    ) {
        binding.tvRecommendationTitle.text = recommendation.title
        binding.tvRecommendationSubtitle.text =
        "${recommendation.profileLabel} · ${recommendation.goalLabel}"

        binding.tvDurationValue.text = recommendation.durationLabel
        binding.tvIntensityValue.text = recommendation.intensityLabel

        renderTankSummaryChips(
            container = binding.tankSummaryContainer,
            items = recommendation.tankSummary
        )

        binding.tvProgramGoal.text =
        "${recommendation.goalLabel} · ${recommendation.confidenceLabel}"

        binding.tvStartTime.text = recommendation.start.label
        binding.tvPeakStartTime.text = recommendation.peakStart.label
        binding.tvPeakEndTime.text = recommendation.peakEnd.label
        binding.tvEndTime.text = recommendation.end.label

        binding.tvRedValue.text = "R${recommendation.channelValues.red}"
        binding.tvGreenValue.text = "G${recommendation.channelValues.green}"
        binding.tvBlueValue.text = "B${recommendation.channelValues.blue}"
        binding.tvWhiteValue.text = "W${recommendation.channelValues.white}"

        renderCompactInfoRows(
            container = binding.reasoningContainer,
            items = recommendation.reasoningNotes
        )

        if (recommendation.warnings.isEmpty()) {
            binding.cardWarnings.visibility = View.GONE
        } else {
            binding.cardWarnings.visibility = View.VISIBLE

            renderCompactInfoRows(
                container = binding.warningsContainer,
                items = recommendation.warnings
            )
        }
    }

    private fun renderActionButtons(
        state: QuickSetupUiState
    ) {
        binding.btnSaveProgram.isEnabled =
        !state.isSaving && state.recommendation != null

        binding.btnLoadToDevice.isEnabled =
        !state.isSaving && state.recommendation != null

        binding.btnSaveProgram.text = if (state.isProgramSaved) {
            "Update Program"
        } else {
            "Save Program"
        }

        binding.btnLoadToDevice.text = if (state.isProgramLoaded) {
            "Reload to Device"
        } else {
            "Load to Device"
        }
    }

    private fun renderTankSummaryChips(
        container: LinearLayout,
        items: List<String>
    ) {
        container.removeAllViews()

        items.chunked(2).forEach {
            rowItems ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 8.dp())
                }
            }

            rowItems.forEachIndexed {
                index, text ->
                val chip = createSummaryChip(text)

                chip.layoutParams = LinearLayout.LayoutParams(
                    0,
                    36.dp(),
                    1f
                ).apply {
                    if (index == 0) {
                        setMargins(0, 0, 5.dp(), 0)
                    } else {
                        setMargins(5.dp(), 0, 0, 0)
                    }
                }

                row.addView(chip)
            }

            if (rowItems.size == 1) {
                val spacer = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        36.dp(),
                        1f
                    ).apply {
                        setMargins(5.dp(), 0, 0, 0)
                    }
                }

                row.addView(spacer)
            }

            container.addView(row)
        }
    }

    private fun createSummaryChip(
        text: String
    ): TextView {
        return TextView(requireContext()).apply {
            setTextAppearance(R.style.TextAppearance_Aqua_Light_ProgramMetaChip)
            setBackgroundResource(R.drawable.bg_light_program_time_panel)

            this.text = text
            .replace("Setup phase:", "Phase:")
            .replace("Tech level:", "Tech:")
            .replace("Livestock:", "Stock:")

            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setPadding(10.dp(), 0, 10.dp(), 0)
        }
    }

    private fun renderCompactInfoRows(
        container: LinearLayout,
        items: List<String>
    ) {
        container.removeAllViews()

        items.forEach {
            text ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_light_program_time_panel)
                setPadding(12.dp(), 9.dp(), 12.dp(), 9.dp())

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 8.dp())
                }
            }

            val dot = TextView(requireContext()).apply {
                this.text = "•"
                setTextAppearance(R.style.TextAppearance_Aqua_Light_Caption)
                gravity = Gravity.TOP

                layoutParams = LinearLayout.LayoutParams(
                    16.dp(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val label = TextView(requireContext()).apply {
                setTextAppearance(R.style.TextAppearance_Aqua_Light_ActionSubtitle)
                this.text = text
                includeFontPadding = false
                setLineSpacing(0f, 1.0f)
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            row.addView(dot)
            row.addView(label)
            container.addView(row)
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
    }
}