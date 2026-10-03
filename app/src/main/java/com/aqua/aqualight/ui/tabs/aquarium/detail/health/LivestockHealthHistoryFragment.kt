package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentLivestockHealthHistoryBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthPastFollowupBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import java.util.concurrent.TimeUnit

class LivestockHealthHistoryFragment :
    Fragment(R.layout.fragment_livestock_health_history) {

    private val args: LivestockHealthHistoryFragmentArgs by navArgs()
    private val tankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthHistoryBinding? = null
    private val binding get() = _binding!!
    private var isNavigating = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthHistoryBinding.bind(view)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.livestock_health_past_followups_section),
                onBackClick = { findNavController().navigateUp() }
            )
        )

        tankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { it.id == args.tankId }
            val handle = findNavController().previousBackStackEntry?.savedStateHandle
            val entries = handle
                ?.let(LivestockHealthUiSessionState::closedFollowups)
                .orEmpty()

            binding.historyContainer.removeAllViews()
            binding.tvEmpty.isVisible = entries.isEmpty()

            entries.forEach { entry ->
                val livestock = tank
                    ?.livestock
                    ?.firstOrNull { item -> item.id == entry.livestockId }
                    ?: return@forEach
                val item = ItemLivestockHealthPastFollowupBinding.inflate(
                    LayoutInflater.from(requireContext()),
                    binding.historyContainer,
                    false
                )
                item.ivLivestock.bindRecordPhoto(
                    livestock.photoUri,
                    LivestockCategories.iconRes(livestock.category)
                )
                item.tvName.text = livestock.name.ifBlank {
                    getString(R.string.aquarium_unnamed_livestock)
                }
                item.tvIssue.setText(
                    LivestockHealthUiText.symptomLabelRes(entry.symptomKey)
                )
                item.tvPeriod.text = getString(
                    R.string.livestock_health_past_period_format,
                    LocaleFormatter.formatDate(requireContext(), entry.startedAtMillis),
                    LocaleFormatter.formatDate(requireContext(), entry.closedAtMillis),
                    durationDays(entry)
                )
                item.tvCloseReason.text = getString(
                    if (
                        entry.closeReason ==
                        LivestockHealthUiSessionState.CLOSE_REASON_RECOVERED
                    ) {
                        R.string.livestock_health_past_recovered_format
                    } else {
                        R.string.livestock_health_past_manual_format
                    },
                    entry.checkCount
                )
                item.tvCloseReason.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (
                            entry.closeReason ==
                            LivestockHealthUiSessionState.CLOSE_REASON_RECOVERED
                        ) {
                            R.color.aqua_status_success
                        } else {
                            R.color.aqua_card_text_secondary
                        }
                    )
                )
                item.root.setOnClickListener { openHistory(entry) }
                binding.historyContainer.addView(item.root)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
    }

    private fun durationDays(entry: ClosedLivestockFollowupUi): Long =
        TimeUnit.MILLISECONDS.toDays(
            (entry.closedAtMillis - entry.startedAtMillis).coerceAtLeast(0L)
        ).coerceAtLeast(1L)

    private fun openHistory(entry: ClosedLivestockFollowupUi) {
        if (isNavigating) return
        val didNavigate = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.livestockHealthHistoryFragment,
            directions = LivestockHealthHistoryFragmentDirections
                .actionLivestockHealthHistoryFragmentToLivestockHealthFollowUpFragment(
                    tankId = args.tankId,
                    livestockId = entry.livestockId,
                    symptomKey = entry.symptomKey,
                    affectedCount = entry.affectedCount,
                    readOnly = true,
                    closeReason = entry.closeReason
                )
        )
        isNavigating = didNavigate
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
