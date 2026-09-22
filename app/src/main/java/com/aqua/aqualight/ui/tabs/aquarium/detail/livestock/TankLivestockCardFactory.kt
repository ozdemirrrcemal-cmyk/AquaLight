package com.aqua.aqualight.ui.tabs.aquarium.detail.livestock

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity
import com.aqua.aqualight.application.aquarium.LivestockCatalogOperations
import com.aqua.aqualight.databinding.ItemTankLivestockCardBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.localizedName

internal class TankLivestockCardFactory(
    private val context: Context,
    private val catalogOperations: LivestockCatalogOperations,
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

        binding.ivCategoryIcon.setImageResource(
            LivestockCategories.iconRes(livestock.category)
        )
        binding.ivCategoryIcon.background = createIconBackground(livestock.category)
        binding.tvName.text = resolveDisplayName(livestock)
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

    private fun resolveDisplayName(
        livestock: AquariumLivestock
    ): String {
        if (AquariumLivestockIdentity.isCustom(livestock.catalogEntryId)) {
            return livestock.name
        }

        return catalogOperations.findById(livestock.catalogEntryId)
            ?.localizedName(context)
            ?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.livestock_catalog_entry_missing_title)
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

    private fun createIconBackground(
        category: String
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(
                ContextCompat.getColor(
                    context,
                    LivestockCategories.colorRes(category)
                )
            )
            cornerRadius = context.resources.getDimensionPixelOffset(
                R.dimen.aqua_size_16
            ).toFloat()
        }
    }
}
