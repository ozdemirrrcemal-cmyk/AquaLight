package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceFeedbackType
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceLoading
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceSnack
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.DeviceLightQuickSetupEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupRecommendation
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.sheet.QuickSetupInfoBottomSheet
import kotlinx.coroutines.launch

class DeviceLightQuickSetupFragment :
    Fragment(R.layout.fragment_device_light_quick_setup) {

    private var _binding: FragmentDeviceLightQuickSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightQuickSetupViewModel by viewModels()

    private val deviceId: Long
        get() = arguments?.getLong(ARG_DEVICE_ID, 0L) ?: 0L

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

        binding.btnShowAllReasons.setOnClickListener {
            val items = viewModel.uiState.value
                .recommendation
                ?.reasoningNotes
                .orEmpty()

            QuickSetupInfoBottomSheet
                .create(requireContext())
                .show(
                    title = "Why this setup?",
                    subtitle = "All factors used by the smart recommendation engine.",
                    items = items
                )
        }

        binding.btnShowAllWarnings.setOnClickListener {
            val items = viewModel.uiState.value
                .recommendation
                ?.warnings
                .orEmpty()

            QuickSetupInfoBottomSheet
                .create(requireContext())
                .show(
                    title = "Before applying",
                    subtitle = "Important notes before saving or loading this program.",
                    items = items
                )
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is DeviceLightQuickSetupEvent.ShowMessage -> {
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.SUCCESS
                            )
                        }

                        is DeviceLightQuickSetupEvent.ShowError -> {
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.ERROR
                            )
                        }

                        is DeviceLightQuickSetupEvent.SetLoading -> {
                            showDeviceLoading(event.isLoading)
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

        if (state.isLoading) {
            renderNoLinkedTank(
                title = "Preparing setup",
                message = "Analyzing tank profile and preparing a smart light recommendation."
            )
            return
        }

        if (recommendation == null) {
            renderNoLinkedTank(
                title = "No tank linked",
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
        title: String,
        message: String
    ) {
        binding.noLinkedTankContainer.visibility = View.VISIBLE
        binding.recommendationContainer.visibility = View.GONE
        binding.bottomActionsBar.visibility = View.GONE

        binding.tvNoLinkedTankTitle.text = title
        binding.tvNoLinkedTankMessage.text = message
    }

    private fun renderRecommendation(
        recommendation: QuickSetupRecommendation
    ) {
        binding.tvRecommendationTitle.text = recommendation.title
        binding.tvRecommendationSubtitle.text =
            "${recommendation.profileLabel} · ${recommendation.goalLabel}"

        binding.tvSetupPhaseChip.text = recommendation.setupPhaseLabel
        binding.tvTechLevelChip.text = recommendation.techLevelLabel
        binding.tvConfidenceChip.text = recommendation.confidenceLabel

        binding.tvDurationValue.text = recommendation.durationLabel
        binding.tvIntensityValue.text = recommendation.intensityLabel

        renderTankAnalysisRows(
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

        val visibleReasons =
            recommendation.reasoningNotes.take(MAX_VISIBLE_REASON_ROWS)

        renderCompactInfoRows(
            container = binding.reasoningContainer,
            items = visibleReasons,
            maxLines = 2
        )

        binding.btnShowAllReasons.visibility =
            if (recommendation.reasoningNotes.size > MAX_VISIBLE_REASON_ROWS) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.btnShowAllReasons.text =
            "View all ${recommendation.reasoningNotes.size} reasons"

        if (recommendation.warnings.isEmpty()) {
            binding.cardWarnings.visibility = View.GONE
            binding.btnShowAllWarnings.visibility = View.GONE
        } else {
            binding.cardWarnings.visibility = View.VISIBLE

            val visibleWarnings =
                recommendation.warnings.take(MAX_VISIBLE_WARNING_ROWS)

            renderCompactInfoRows(
                container = binding.warningsContainer,
                items = visibleWarnings,
                maxLines = 2
            )

            binding.btnShowAllWarnings.visibility =
                if (recommendation.warnings.size > MAX_VISIBLE_WARNING_ROWS) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            binding.btnShowAllWarnings.text =
                "View all ${recommendation.warnings.size} notes"
        }
    }

    private fun renderActionButtons(
        state: QuickSetupUiState
    ) {
        binding.btnSaveProgram.isEnabled =
            !state.isSaving && state.recommendation != null

        binding.btnLoadToDevice.isEnabled =
            !state.isSaving && state.recommendation != null

        binding.btnSaveProgram.text = when {
            state.isSaving -> "Saving..."
            state.isProgramSaved -> "Update"
            else -> "Save"
        }

        binding.btnLoadToDevice.text = when {
            state.isSaving -> "Please wait"
            state.isProgramLoaded -> "Reload"
            else -> "Load to Device"
        }
    }

    private fun renderTankAnalysisRows(
        container: LinearLayout,
        items: List<String>
    ) {
        container.removeAllViews()

        items.forEach { item ->
            val parsed = parseAnalysisItem(item)

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_light_program_time_panel)
                setPadding(
                    14.dp(),
                    10.dp(),
                    14.dp(),
                    10.dp()
                )

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(
                        0,
                        0,
                        0,
                        7.dp()
                    )
                }
            }

            val label = TextView(requireContext()).apply {
                setTextAppearance(R.style.TextAppearance_Aqua_Light_Caption)
                text = parsed.first
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.85f
                )
            }

            val value = TextView(requireContext()).apply {
                setTextAppearance(R.style.TextAppearance_Aqua_Light_ActionSubtitle)
                text = parsed.second
                gravity = Gravity.END
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.15f
                )
            }

            row.addView(label)
            row.addView(value)
            container.addView(row)
        }
    }

    private fun parseAnalysisItem(
        text: String
    ): Pair<String, String> {
        val cleanText = text
            .replace("Setup phase:", "Phase:")
            .replace("Tech level:", "Tech:")
            .replace("Livestock:", "Stock:")
            .trim()

        val separatorIndex = cleanText.indexOf(":")

        return if (separatorIndex > 0) {
            val label = cleanText
                .substring(0, separatorIndex)
                .trim()

            val value = cleanText
                .substring(separatorIndex + 1)
                .trim()
                .ifBlank {
                    "Unknown"
                }

            label to value
        } else {
            "Profile" to cleanText
        }
    }

    private fun renderCompactInfoRows(
        container: LinearLayout,
        items: List<String>,
        maxLines: Int
    ) {
        container.removeAllViews()

        items.forEachIndexed { index, text ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_light_program_time_panel)
                setPadding(
                    12.dp(),
                    9.dp(),
                    12.dp(),
                    9.dp()
                )

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(
                        0,
                        0,
                        0,
                        7.dp()
                    )
                }
            }

            val indexBadge = TextView(requireContext()).apply {
                setTextAppearance(R.style.TextAppearance_Aqua_Light_Caption)
                this.text = (index + 1).toString()
                gravity = Gravity.CENTER
                includeFontPadding = false
                setBackgroundResource(R.drawable.bg_light_action_icon)

                layoutParams = LinearLayout.LayoutParams(
                    26.dp(),
                    26.dp()
                ).apply {
                    setMargins(
                        0,
                        0,
                        10.dp(),
                        0
                    )
                }
            }

            val label = TextView(requireContext()).apply {
                setTextAppearance(R.style.TextAppearance_Aqua_Light_ActionSubtitle)
                this.text = text
                includeFontPadding = false
                setLineSpacing(
                    0f,
                    1.0f
                )
                this.maxLines = maxLines
                ellipsize = if (maxLines <= 2) {
                    android.text.TextUtils.TruncateAt.END
                } else {
                    null
                }

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            row.addView(indexBadge)
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

        private const val MAX_VISIBLE_REASON_ROWS = 4
        private const val MAX_VISIBLE_WARNING_ROWS = 3
    }
}