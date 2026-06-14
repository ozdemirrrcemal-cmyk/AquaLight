package com.aqua.aqualight.ui.tabs.devices.detail.light.presets.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemLightPresetCardBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetItem

class LightPresetsAdapter(
    private val onPresetClick: (LightPresetItem) -> Unit,
    private val onPresetOptionsClick: (LightPresetItem) -> Unit
) : RecyclerView.Adapter<LightPresetsAdapter.PresetViewHolder>() {

    private val items = mutableListOf<LightPresetItem>()

    fun submitList(newItems: List<LightPresetItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PresetViewHolder {
        val binding = ItemLightPresetCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return PresetViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: PresetViewHolder,
        position: Int
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PresetViewHolder(
        private val binding: ItemLightPresetCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LightPresetItem) {
            binding.tvPresetTitle.text = item.title
            binding.tvPresetSubtitle.text = item.subtitle
            binding.tvPresetCategory.text = item.category.label

            val channels = item.channels

            binding.tvPresetRed.text = "R${channels.safeRed}"
            binding.tvPresetGreen.text = "G${channels.safeGreen}"
            binding.tvPresetBlue.text = "B${channels.safeBlue}"
            binding.tvPresetWhite.text = "W${channels.safeWhite}"

            binding.viewPresetColor.background = createColorPreviewDrawable(item)

            binding.presetCardRoot.setOnClickListener {
                onPresetClick(item)
            }

            binding.btnPresetMore.setOnClickListener {
                onPresetOptionsClick(item)
            }

            binding.presetCardRoot.setOnLongClickListener {
                onPresetOptionsClick(item)
                true
            }
        }

        private fun createColorPreviewDrawable(
            item: LightPresetItem
        ): GradientDrawable {
            val color = item.previewColor

            val strokeWidth = binding.root.resources.displayMetrics.density
                .toInt()
                .coerceAtLeast(1)
            val strokeColor = binding.root.context.getColor(R.color.light_stroke)

            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(strokeWidth, strokeColor)
            }
        }

    }
}