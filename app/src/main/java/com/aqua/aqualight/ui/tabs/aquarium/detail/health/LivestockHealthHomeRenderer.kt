package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.databinding.FragmentLivestockHealthBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthActiveFollowupBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthPastFollowupBinding

internal class LivestockHealthHomeRenderer(
    private val fragment: Fragment,
    private val binding: FragmentLivestockHealthBinding,
    private val onActiveClick: (ActiveLivestockFollowupUi) -> Unit,
    private val onPastClick: (ClosedLivestockFollowupUi) -> Unit
) {
    fun render(
        tank: AquariumTankSnapshot?,
        activeFollowups: List<ActiveLivestockFollowupUi>,
        closedFollowups: List<ClosedLivestockFollowupUi>
    ) {
        val livestock = tank?.livestock.orEmpty()
        val visibleActive = activeFollowups.filter { entry ->
            livestock.any { item -> item.id == entry.livestockId }
        }
        val hasAnyTracking = visibleActive.isNotEmpty() || closedFollowups.isNotEmpty()

        binding.tvHeroTitle.setText(
            if (hasAnyTracking) {
                R.string.livestock_health_hero_active_title
            } else {
                R.string.livestock_health_hero_empty_title
            }
        )
        binding.tvHeroSubtitle.setText(
            if (hasAnyTracking) {
                R.string.livestock_health_hero_active_subtitle
            } else {
                R.string.livestock_health_hero_empty_subtitle
            }
        )
        binding.cardEmptyFollowups.isVisible = !hasAnyTracking
        binding.cardNoObservationInfo.isVisible = !hasAnyTracking
        binding.filledHealthContent.isVisible = hasAnyTracking
        binding.btnNewObservation.isEnabled = livestock.isNotEmpty()

        if (livestock.isEmpty()) {
            binding.tvEmptyFollowupsTitle.setText(R.string.livestock_health_no_livestock_title)
            binding.tvEmptyFollowupsBody.setText(R.string.livestock_health_no_livestock_body)
        } else {
            binding.tvEmptyFollowupsTitle.setText(R.string.livestock_health_empty_followups_title)
            binding.tvEmptyFollowupsBody.setText(R.string.livestock_health_empty_followups_body)
        }

        renderActive(livestock, visibleActive)
        renderPast(livestock, closedFollowups)
    }

    private fun renderActive(
        livestock: List<com.aqua.aqualight.application.aquarium.AquariumLivestock>,
        entries: List<ActiveLivestockFollowupUi>
    ) {
        binding.activeFollowupsContainer.removeAllViews()
        binding.tvActiveFollowupsTitle.isVisible = entries.isNotEmpty()
        binding.activeFollowupsContainer.isVisible = entries.isNotEmpty()

        entries.forEach { entry ->
            val itemLivestock = livestock.firstOrNull { it.id == entry.livestockId }
                ?: return@forEach
            val item = ItemLivestockHealthActiveFollowupBinding.inflate(
                LayoutInflater.from(fragment.requireContext()),
                binding.activeFollowupsContainer,
                false
            )
            fragment.bindActiveFollowupCard(item, itemLivestock, entry)
            item.root.setOnClickListener { onActiveClick(entry) }
            binding.activeFollowupsContainer.addView(item.root)
        }
    }

    private fun renderPast(
        livestock: List<com.aqua.aqualight.application.aquarium.AquariumLivestock>,
        entries: List<ClosedLivestockFollowupUi>
    ) {
        binding.pastFollowupsContainer.removeAllViews()
        val latest = entries.mapNotNull { entry ->
            livestock.firstOrNull { it.id == entry.livestockId }?.let { entry to it }
        }.take(MAX_HOME_HISTORY)

        binding.tvPastFollowupsEmpty.isVisible = latest.isEmpty()
        binding.btnViewAllPast.isVisible = entries.isNotEmpty()

        latest.forEach { (entry, itemLivestock) ->
            val item = ItemLivestockHealthPastFollowupBinding.inflate(
                LayoutInflater.from(fragment.requireContext()),
                binding.pastFollowupsContainer,
                false
            )
            fragment.bindPastFollowupCard(item, itemLivestock, entry)
            item.root.setOnClickListener { onPastClick(entry) }
            binding.pastFollowupsContainer.addView(item.root)
        }
    }

    private companion object {
        const val MAX_HOME_HISTORY = 2
    }
}
