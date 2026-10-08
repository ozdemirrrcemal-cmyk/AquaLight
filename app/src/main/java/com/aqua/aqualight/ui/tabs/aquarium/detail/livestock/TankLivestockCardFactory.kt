package com.aqua.aqualight.ui.tabs.aquarium.detail.livestock

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.ItemTankLivestockCardBinding
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories

internal class TankLivestockCardFactory(
    private val context: Context,
    private val onClick: (Long) -> Unit
) {

    fun create(
        parent: ViewGroup,
        livestock: AquariumLivestock
    ): View {
        val binding = ItemTankLivestockCardBinding.inflate(
            LayoutInflater.from(context),
            parent,
            false
        )

        binding.ivCategoryIcon.bindRecordPhoto(livestock.photoUri)
        binding.tvName.text = livestock.name.ifBlank {
            context.getString(R.string.aquarium_unnamed_livestock)
        }
        binding.tvMeta.text = context.getString(
            R.string.aquarium_livestock_meta_format,
            context.getString(LivestockCategories.labelRes(livestock.category)),
            quantityText(livestock.quantity)
        )
        binding.tvDate.text = addedDateText(livestock.addedDateEpochDay)
        binding.tvNote.isVisible = livestock.note.isNotBlank()
        binding.tvNote.text = livestock.note
        binding.root.contentDescription = binding.tvName.text
        binding.root.setOnClickListener {
            onClick(livestock.id)
        }

        return binding.root
    }

    private fun quantityText(
        quantity: Int
    ): String {
        val safeQuantity = quantity.coerceAtLeast(1)
        return context.resources.getQuantityString(
            R.plurals.aquarium_livestock_quantity_piece,
            safeQuantity,
            safeQuantity
        )
    }

    private fun addedDateText(
        addedDateEpochDay: Long?
    ): String {
        if (addedDateEpochDay == null || addedDateEpochDay <= 0L) {
            return context.getString(R.string.aquarium_livestock_added_date_not_set)
        }

        return context.getString(
            R.string.aquarium_livestock_added_date_format,
            LocaleFormatter.formatDateEpochDay(context, addedDateEpochDay)
        )
    }

}
