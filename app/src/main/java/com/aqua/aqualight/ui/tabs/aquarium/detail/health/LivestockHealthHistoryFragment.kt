package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.FragmentLivestockHealthHistoryBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthPastFollowupBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class LivestockHealthHistoryFragment :
    Fragment(R.layout.fragment_livestock_health_history) {

    private val args: LivestockHealthHistoryFragmentArgs by navArgs()
    private val tankViewModel: AquariumTankViewModel by activityViewModels()
    private val healthViewModel: LivestockHealthViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthHistoryBinding? = null
    private val binding get() = _binding!!
    private var isNavigating = false
    private var livestock: List<AquariumLivestock> = emptyList()
    private var entries: List<ClosedLivestockFollowupUi> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthHistoryBinding.bind(view)
        setupHeader()
        observeHistory()
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.livestock_health_past_followups_section),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun observeHistory() {
        tankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            livestock = tanks
                .firstOrNull { it.id == args.tankId }
                ?.livestock
                .orEmpty()
            renderHistory(livestock, entries)
        }
        healthViewModel.observationsForTank(args.tankId).observe(viewLifecycleOwner) { records ->
            entries = records.filter { it.closedAtMillis != null }.map { it.toClosedUi() }
            renderHistory(livestock, entries)
        }
    }

    private fun renderHistory(
        livestock: List<AquariumLivestock>,
        entries: List<ClosedLivestockFollowupUi>
    ) {
        binding.historyContainer.removeAllViews()
        binding.tvEmpty.isVisible = entries.isEmpty()

        entries.forEach { entry ->
            val itemLivestock = livestock.firstOrNull { it.id == entry.livestockId }
                ?: return@forEach
            val item = ItemLivestockHealthPastFollowupBinding.inflate(
                LayoutInflater.from(requireContext()),
                binding.historyContainer,
                false
            )
            bindPastFollowupCard(item, itemLivestock, entry)
            item.root.setOnClickListener { openHistory(entry) }
            binding.historyContainer.addView(item.root)
        }
    }

    private fun openHistory(entry: ClosedLivestockFollowupUi) {
        if (!isNavigating) {
            isNavigating = findNavController().navigateSafelyFrom(
                sourceDestinationId = R.id.livestockHealthHistoryFragment,
                directions = LivestockHealthHistoryFragmentDirections
                    .actionLivestockHealthHistoryFragmentToLivestockHealthFollowUpFragment(
                        observationId = entry.observationId,
                        tankId = args.tankId,
                        livestockId = entry.livestockId,
                        symptomKey = entry.symptomKey,
                        affectedCount = entry.affectedCount,
                        readOnly = true,
                        closeReason = entry.closeReason
                    )
            )
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
