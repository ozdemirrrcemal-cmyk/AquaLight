package com.aqua.aqualight.ui.tabs.aquarium.detail.livestock

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.LivestockCatalogItem
import com.aqua.aqualight.databinding.FragmentTankLivestockPickerBinding
import com.aqua.aqualight.ui.common.text.setTextSizeResource
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.google.android.material.card.MaterialCardView

internal class TankLivestockPickerRenderer(
    private val context: Context,
    private val binding: FragmentTankLivestockPickerBinding,
    private val adapter: TankLivestockPickerAdapter,
    private val onCategorySelected: (String) -> Unit
) {

    fun renderCategories(
        selectedCategory: String
    ) {
        binding.categoryContainer.removeAllViews()

        LivestockCategories.all.forEach { category ->
            binding.categoryContainer.addView(
                createCategoryChip(
                    category = category,
                    selected = category == selectedCategory
                )
            )
        }

        binding.btnNewLivestock.text = context.getString(
            R.string.livestock_picker_new_category,
            context.getString(LivestockCategories.labelRes(selectedCategory))
        )
    }

    fun renderList(
        entries: List<LivestockCatalogItem>,
        selectedCategory: String,
        selectedEntryId: String?,
        searchQuery: String,
        catalogLoadFailed: Boolean
    ) {
        if (catalogLoadFailed) {
            adapter.submitList(emptyList())
            binding.rvLivestock.isVisible = false
            binding.tvEmptyState.isVisible = true
            binding.tvEmptyState.text = context.getString(
                R.string.livestock_picker_catalog_unavailable
            )
            binding.tvResultCount.text = context.getString(
                R.string.livestock_picker_result_zero
            )
            return
        }

        val filteredEntries = entries.asSequence()
            .filter { entry -> entry.category == selectedCategory }
            .filter { entry -> entry.matches(searchQuery) }
            .toList()

        adapter.submitList(
            filteredEntries.map { entry ->
                TankLivestockPickerItem(
                    entry = entry,
                    selected = entry.id == selectedEntryId
                )
            }
        )

        binding.rvLivestock.isVisible = filteredEntries.isNotEmpty()
        binding.tvEmptyState.isVisible = filteredEntries.isEmpty()
        binding.tvEmptyState.text = context.getString(R.string.livestock_picker_empty)
        binding.tvResultCount.text = context.resources.getQuantityString(
            R.plurals.livestock_picker_result_count,
            filteredEntries.size,
            filteredEntries.size
        )
    }

    fun renderSelection(
        hasSelection: Boolean
    ) {
        binding.tvSelectedCount.text = context.getString(
            if (hasSelection) {
                R.string.livestock_picker_selected_one
            } else {
                R.string.livestock_picker_selected_zero
            }
        )
        binding.btnContinue.isEnabled = hasSelection
    }

    private fun createCategoryChip(
        category: String,
        selected: Boolean
    ): View {
        val card = MaterialCardView(context).apply {
            radius = context.resources.getDimensionPixelOffset(R.dimen.aqua_size_13).toFloat()
            strokeWidth = context.resources.getDimensionPixelOffset(R.dimen.aqua_size_1)
            strokeColor = ContextCompat.getColor(
                context,
                if (selected) R.color.aqua_card_accent else R.color.aqua_card_outline
            )
            setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (selected) {
                        R.color.aqua_card_surface_pressed
                    } else {
                        R.color.aqua_card_surface
                    }
                )
            )
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                context.resources.getDimensionPixelOffset(R.dimen.aqua_size_42)
            ).apply {
                marginEnd = context.resources.getDimensionPixelOffset(R.dimen.aqua_size_8)
            }
            setOnClickListener {
                if (!selected) {
                    onCategorySelected(category)
                }
            }
        }

        card.addView(
            createCategoryLabel(category, selected),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        return card
    }

    private fun createCategoryLabel(
        category: String,
        selected: Boolean
    ): TextView {
        return TextView(context).apply {
            text = context.getString(LivestockCategories.labelRes(category))
            gravity = Gravity.CENTER
            setTextSizeResource(R.dimen.aqua_text_size_caption_plus)
            setTextColor(
                ContextCompat.getColor(
                    context,
                    if (selected) {
                        R.color.aqua_card_text_primary
                    } else {
                        R.color.aqua_card_text_secondary
                    }
                )
            )
            setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = false
            setPadding(
                context.resources.getDimensionPixelOffset(R.dimen.aqua_size_15),
                0,
                context.resources.getDimensionPixelOffset(R.dimen.aqua_size_15),
                0
            )
        }
    }
}
