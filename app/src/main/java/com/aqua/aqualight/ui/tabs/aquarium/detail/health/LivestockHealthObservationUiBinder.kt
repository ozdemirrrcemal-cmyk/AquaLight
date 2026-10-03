package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.FragmentLivestockHealthObservationBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthSelectorBinding
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories

internal fun renderObservationLivestockSelectors(
    fragment: Fragment,
    binding: FragmentLivestockHealthObservationBinding,
    livestock: List<AquariumLivestock>,
    selectorBindings: MutableMap<Long, ItemLivestockHealthSelectorBinding>,
    selectedLivestockId: Long,
    onSelected: (AquariumLivestock) -> Unit
) {
    binding.livestockSelectorContainer.removeAllViews()
    selectorBindings.clear()
    binding.tvNoLivestock.isVisible = livestock.isEmpty()
    binding.livestockSelectorScroll.isVisible = livestock.isNotEmpty()

    livestock.forEach { item ->
        val itemBinding = ItemLivestockHealthSelectorBinding.inflate(
            LayoutInflater.from(fragment.requireContext()),
            binding.livestockSelectorContainer,
            false
        )
        itemBinding.ivLivestockPhoto.bindRecordPhoto(
            item.photoUri,
            LivestockCategories.iconRes(item.category)
        )
        val displayName = item.name.ifBlank {
            fragment.getString(R.string.aquarium_unnamed_livestock)
        }
        val nameParts = displayName.split(" / ", limit = 2)
        itemBinding.tvLivestockName.text = nameParts.first()
        itemBinding.tvLivestockSubtitle.isVisible = nameParts.size > 1
        itemBinding.tvLivestockSubtitle.text = nameParts.getOrNull(1).orEmpty()
        itemBinding.tvLivestockQuantity.text = fragment.getString(
            R.string.livestock_health_selector_quantity_format,
            item.quantity.coerceAtLeast(1)
        )
        itemBinding.root.setOnClickListener {
            onSelected(item)
            fragment.applyLivestockSelectorSelection(selectorBindings, item.id)
        }
        selectorBindings[item.id] = itemBinding
        binding.livestockSelectorContainer.addView(itemBinding.root)
    }

    fragment.applyLivestockSelectorSelection(selectorBindings, selectedLivestockId)
}

internal fun FragmentLivestockHealthObservationBinding.bindAffectedCounter(
    fragment: Fragment,
    currentCount: () -> Int,
    maximum: () -> Int,
    onCountChanged: (Int) -> Unit
) {
    btnAffectedMinus.setOnClickListener {
        onCountChanged(
            renderAffectedCounter(
                fragment = fragment,
                count = currentCount() - 1,
                maximum = maximum()
            )
        )
    }
    btnAffectedPlus.setOnClickListener {
        onCountChanged(
            renderAffectedCounter(
                fragment = fragment,
                count = currentCount() + 1,
                maximum = maximum()
            )
        )
    }
}

internal fun FragmentLivestockHealthObservationBinding.renderAffectedCounter(
    fragment: Fragment,
    count: Int,
    maximum: Int
): Int {
    val safeMaximum = maximum.coerceAtLeast(1)
    val normalized = count.coerceIn(1, safeMaximum)
    tvAffectedRegisteredCount.text = fragment.getString(
        R.string.livestock_health_registered_count_format,
        safeMaximum
    )
    tvAffectedCount.text = fragment.getString(
        R.string.livestock_health_affected_counter_format,
        normalized,
        safeMaximum
    )
    btnAffectedMinus.isEnabled = normalized > 1
    btnAffectedPlus.isEnabled = normalized < safeMaximum
    return normalized
}

internal fun FragmentLivestockHealthObservationBinding.renderObservationPhotoSlots(
    photoUris: List<String?>
) {
    renderObservationPhotoSlot(
        photoUris.getOrNull(0),
        ivObservationPhotoPreviewOne,
        tvObservationPhotoSlotPlusOne
    )
    renderObservationPhotoSlot(
        photoUris.getOrNull(1),
        ivObservationPhotoPreviewTwo,
        tvObservationPhotoSlotPlusTwo
    )
    renderObservationPhotoSlot(
        photoUris.getOrNull(2),
        ivObservationPhotoPreviewThree,
        tvObservationPhotoSlotPlusThree
    )
}

private fun renderObservationPhotoSlot(
    photoUri: String?,
    preview: android.widget.ImageView,
    plus: android.widget.TextView
) {
    val hasPhoto = !photoUri.isNullOrBlank()
    preview.isVisible = hasPhoto
    plus.isVisible = !hasPhoto
    if (hasPhoto) {
        preview.bindRecordPhoto(photoUri)
    } else {
        preview.setImageDrawable(null)
    }
}
