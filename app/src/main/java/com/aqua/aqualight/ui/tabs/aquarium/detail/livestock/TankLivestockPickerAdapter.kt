package com.aqua.aqualight.ui.tabs.aquarium.detail.livestock

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.LivestockCatalogItem
import com.aqua.aqualight.databinding.ItemLivestockCatalogBinding
import com.aqua.aqualight.i18n.AppLanguageController
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.localizedName

data class TankLivestockPickerItem(
    val entry: LivestockCatalogItem,
    val selected: Boolean
)

class TankLivestockPickerAdapter(
    private val onEntryClick: (LivestockCatalogItem) -> Unit
) : ListAdapter<TankLivestockPickerItem, TankLivestockPickerAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemLivestockCatalogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(
            binding = binding,
            onEntryClick = onEntryClick
        )
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemLivestockCatalogBinding,
        private val onEntryClick: (LivestockCatalogItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: TankLivestockPickerItem
        ) {
            val context = binding.root.context
            val entry = item.entry
            val displayName = entry.localizedName(context)
            val parameterSummary = entry.parameterSummary()

            binding.cardRoot.strokeColor = ContextCompat.getColor(
                context,
                if (item.selected) {
                    R.color.aqua_card_accent
                } else {
                    R.color.aqua_card_outline
                }
            )
            binding.cardRoot.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (item.selected) {
                        R.color.aqua_card_surface_pressed
                    } else {
                        R.color.aqua_card_surface
                    }
                )
            )

            binding.ivCategoryIcon.setImageResource(
                LivestockCategories.iconRes(entry.category)
            )
            binding.ivCategoryIcon.setColorFilter(
                ContextCompat.getColor(
                    context,
                    R.color.aqua_content_on_dark
                )
            )
            binding.ivCategoryIcon.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(
                    ContextCompat.getColor(
                        context,
                        LivestockCategories.colorRes(entry.category)
                    )
                )
                cornerRadius = context.resources.getDimensionPixelOffset(
                    R.dimen.aqua_size_14
                ).toFloat()
            }

            binding.tvCategory.text = context.getString(
                LivestockCategories.labelRes(entry.category)
            )
            binding.tvName.text = displayName

            binding.tvScientificName.isVisible = entry.scientificName.isNullOrBlank().not()
            binding.tvScientificName.text = entry.scientificName.orEmpty()

            binding.tvParameters.isVisible = parameterSummary.isNotBlank()
            binding.tvParameters.text = parameterSummary

            binding.tvSelection.text = if (item.selected) {
                context.getString(R.string.aqua_selected_symbol)
            } else {
                ""
            }
            binding.tvSelection.setBackgroundResource(
                if (item.selected) {
                    R.drawable.bg_material_check_selected
                } else {
                    R.drawable.bg_material_check_unselected
                }
            )
            binding.tvSelection.contentDescription = context.getString(
                if (item.selected) {
                    R.string.livestock_picker_item_selected
                } else {
                    R.string.livestock_picker_item_not_selected
                }
            )

            binding.root.contentDescription = listOfNotNull(
                displayName,
                entry.scientificName,
                parameterSummary.takeIf(String::isNotBlank)
            ).joinToString(separator = ", ")

            binding.root.setOnClickListener {
                onEntryClick(entry)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TankLivestockPickerItem>() {
        override fun areItemsTheSame(
            oldItem: TankLivestockPickerItem,
            newItem: TankLivestockPickerItem
        ): Boolean {
            return oldItem.entry.id == newItem.entry.id
        }

        override fun areContentsTheSame(
            oldItem: TankLivestockPickerItem,
            newItem: TankLivestockPickerItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}