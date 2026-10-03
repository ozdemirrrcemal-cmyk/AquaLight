package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
 
private data class LivestockHealthSymptomSlot(
    val card: MaterialCardView,
    val icon: android.widget.ImageView,
    val label: android.widget.TextView
)

internal fun com.aqua.aqualight.databinding.FragmentLivestockHealthObservationBinding
    .bindLivestockSymptomOptions(
        options: List<LivestockHealthSymptomOption>,
        selectedSymptoms: Set<String>,
        onToggle: (String) -> Unit
    ) {
    val slots = listOf(
        LivestockHealthSymptomSlot(cardSymptomSurface, ivSymptomSurface, tvSymptomSurface),
        LivestockHealthSymptomSlot(cardSymptomAppetite, ivSymptomAppetite, tvSymptomAppetite),
        LivestockHealthSymptomSlot(cardSymptomSwimming, ivSymptomSwimming, tvSymptomSwimming),
        LivestockHealthSymptomSlot(cardSymptomSpot, ivSymptomSpot, tvSymptomSpot),
        LivestockHealthSymptomSlot(cardSymptomFins, ivSymptomFins, tvSymptomFins),
        LivestockHealthSymptomSlot(cardSymptomOther, ivSymptomOther, tvSymptomOther)
    )
    val context = root.context
    val selectedStroke = ContextCompat.getColor(context, R.color.aqua_button_blue)
    val normalStroke = ContextCompat.getColor(context, R.color.aqua_card_outline)
    val selectedSurface = ContextCompat.getColor(context, R.color.aqua_surface_action)
    val normalSurface = ContextCompat.getColor(context, R.color.aqua_card_surface)

    slots.forEachIndexed { index, slot ->
        val option = options.getOrNull(index)
        slot.card.isVisible = option != null
        if (option == null) {
            slot.card.setOnClickListener(null)
            return@forEachIndexed
        }

        slot.icon.setImageResource(option.iconRes)
        slot.label.setText(option.labelRes)
        val selected = option.key in selectedSymptoms
        slot.card.strokeWidth = root.resources.getDimensionPixelSize(
            if (selected) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
        )
        slot.card.setStrokeColor(if (selected) selectedStroke else normalStroke)
        slot.card.setCardBackgroundColor(if (selected) selectedSurface else normalSurface)
        slot.card.setOnClickListener { onToggle(option.key) }
    }
}

internal fun List<AquariumLivestock>.selectedLivestock(
    selectedLivestockId: Long
): AquariumLivestock? = firstOrNull { item -> item.id == selectedLivestockId }
