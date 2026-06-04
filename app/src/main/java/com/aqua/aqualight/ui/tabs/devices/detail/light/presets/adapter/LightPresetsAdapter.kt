package com.aqua.aqualight.ui.tabs.devices.detail.light.presets.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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

            binding.tvPresetRed.text = "R${item.red}"
            binding.tvPresetGreen.text = "G${item.green}"
            binding.tvPresetBlue.text = "B${item.blue}"
            binding.tvPresetWhite.text = "W${item.white}"

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
            val color = calculatePreviewColor(
                red = item.red,
                green = item.green,
                blue = item.blue,
                white = item.white
            )

            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }

        private fun calculatePreviewColor(
            red: Int,
            green: Int,
            blue: Int,
            white: Int
        ): Int {
            val r = red.coerceIn(0, 100) / 100.0
            val g = green.coerceIn(0, 100) / 100.0
            val b = blue.coerceIn(0, 100) / 100.0
            val w = white.coerceIn(0, 100) / 100.0

            val redColor = Triple(1.00, 0.08, 0.03)
            val greenColor = Triple(0.12, 1.00, 0.20)
            val blueColor = Triple(0.05, 0.28, 1.00)
            val whiteColor = Triple(0.92, 0.96, 1.00)

            val linearRed =
                redColor.first * r +
                    greenColor.first * g +
                    blueColor.first * b +
                    whiteColor.first * w

            val linearGreen =
                redColor.second * r +
                    greenColor.second * g +
                    blueColor.second * b +
                    whiteColor.second * w

            val linearBlue =
                redColor.third * r +
                    greenColor.third * g +
                    blueColor.third * b +
                    whiteColor.third * w

            val max = maxOf(
                linearRed,
                linearGreen,
                linearBlue,
                1.0
            )

            fun gammaCorrect(value: Double): Int {
                val normalized = (value / max).coerceIn(0.0, 1.0)
                return (255.0 * Math.pow(normalized, 1.0 / 2.2))
                    .toInt()
                    .coerceIn(0, 255)
            }

            return Color.rgb(
                gammaCorrect(linearRed),
                gammaCorrect(linearGreen),
                gammaCorrect(linearBlue)
            )
        }
    }
}