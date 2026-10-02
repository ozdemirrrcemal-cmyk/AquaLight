package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.ItemLivestockHealthSelectorBinding
import com.google.android.material.card.MaterialCardView

internal fun Fragment.applyLivestockSelectorSelection(
    selectorBindings: Map<Long, ItemLivestockHealthSelectorBinding>,
    selectedLivestockId: Long
) {
    val selectedStroke = ContextCompat.getColor(requireContext(), R.color.aqua_button_blue)
    val normalStroke = ContextCompat.getColor(requireContext(), R.color.aqua_card_outline)
    val selectedSurface = ContextCompat.getColor(requireContext(), R.color.aqua_surface_action)
    val normalSurface = ContextCompat.getColor(requireContext(), R.color.aqua_card_surface)

    selectorBindings.forEach { (livestockId, itemBinding) ->
        val isSelected = livestockId == selectedLivestockId
        itemBinding.root.strokeWidth = resources.getDimensionPixelSize(
            if (isSelected) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
        )
        itemBinding.root.setStrokeColor(if (isSelected) selectedStroke else normalStroke)
        itemBinding.root.setCardBackgroundColor(
            if (isSelected) selectedSurface else normalSurface
        )
        itemBinding.ivSelected.setImageResource(
            if (isSelected) {
                R.drawable.ic_livestock_radio_checked_24
            } else {
                R.drawable.ic_livestock_radio_unchecked_24
            }
        )
    }
}

internal fun Fragment.applyLivestockSymptomSelection(
    cards: Map<String, MaterialCardView>,
    selectedSymptoms: Set<String>
) {
    val selectedStroke = ContextCompat.getColor(requireContext(), R.color.aqua_button_blue)
    val normalStroke = ContextCompat.getColor(requireContext(), R.color.aqua_card_outline)
    val selectedSurface = ContextCompat.getColor(requireContext(), R.color.aqua_surface_action)
    val normalSurface = ContextCompat.getColor(requireContext(), R.color.aqua_card_surface)

    cards.forEach { (key, card) ->
        val selected = key in selectedSymptoms
        card.strokeWidth = resources.getDimensionPixelSize(
            if (selected) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
        )
        card.setStrokeColor(if (selected) selectedStroke else normalStroke)
        card.setCardBackgroundColor(if (selected) selectedSurface else normalSurface)
    }
}

internal fun List<AquariumLivestock>.selectedLivestock(
    selectedLivestockId: Long
): AquariumLivestock? = firstOrNull { item -> item.id == selectedLivestockId }
