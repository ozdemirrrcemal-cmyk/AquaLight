package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemPlantHealthSymptomBinding
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder

internal class PlantSymptomGrid(container: LinearLayout, onToggle: (String) -> Unit) {
    private val tiles = linkedMapOf<String, ItemPlantHealthSymptomBinding>()

    init {
        PlantSymptomOptions.items.chunked(COLUMNS).forEach { options ->
            val row = LinearLayout(container.context).apply { orientation = LinearLayout.HORIZONTAL }
            options.forEachIndexed { index, option ->
                val item = createTile(row, index, option)
                item.root.setOnClickListener { onToggle(option.key) }
                tiles[option.key] = item
                row.addView(item.root)
            }
            container.addView(row)
        }
    }

    private fun createTile(
        parent: LinearLayout,
        index: Int,
        option: PlantSymptomOption
    ): ItemPlantHealthSymptomBinding {
        val item = ItemPlantHealthSymptomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        item.root.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (index > 0) marginStart = parent.resources.getDimensionPixelSize(R.dimen.aqua_size_6)
            bottomMargin = parent.resources.getDimensionPixelSize(R.dimen.aqua_size_6)
        }
        item.tvSymptom.setText(option.labelRes)
        item.root.contentDescription = parent.context.getString(option.labelRes)
        if (option.photoUrl == null) {
            item.ivSymptom.scaleType = ImageView.ScaleType.CENTER
            item.ivSymptom.setImageResource(R.drawable.ic_info)
        } else {
            item.ivSymptom.load(option.photoUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_health_plant_24)
                error(R.drawable.ic_health_plant_24)
            }
        }
        return item
    }

    fun render(selected: Set<String>, enabled: Boolean) {
        tiles.forEach { (key, item) ->
            item.root.isChecked = key in selected
            item.root.isEnabled = enabled
            item.root.strokeColor = ContextCompat.getColor(item.root.context,
                if (key in selected) R.color.aqua_accent_primary else R.color.aqua_card_outline
            )
        }
    }

    private companion object { const val COLUMNS = 3 }
}
