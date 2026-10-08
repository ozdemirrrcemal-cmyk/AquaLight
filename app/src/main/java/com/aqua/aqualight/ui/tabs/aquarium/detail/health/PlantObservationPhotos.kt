package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.PlantObservationRules
import com.aqua.aqualight.databinding.ItemPlantObservationPhotoBinding
import com.aqua.aqualight.ui.common.media.bindRecordPhoto

/** The same photo presentation is used by the editable draft and the immutable saved record. */
internal class PlantObservationPhotos(
    private val container: LinearLayout,
    private val onEdit: ((Int) -> Unit)? = null,
    private val onOpen: ((Int) -> Unit)? = null
) {
    private val slots = List(PlantObservationRules.MAX_PHOTOS) { index ->
        ItemPlantObservationPhotoBinding.inflate(LayoutInflater.from(container.context), container, false)
            .also { item ->
                item.root.layoutParams = LinearLayout.LayoutParams(
                    0, container.resources.getDimensionPixelSize(R.dimen.aqua_size_96), 1f
                ).apply {
                    if (index > 0) marginStart = container.resources.getDimensionPixelSize(R.dimen.aqua_size_8)
                }
                item.root.setOnClickListener {
                    if (onEdit != null) onEdit.invoke(index) else onOpen?.invoke(index)
                }
                container.addView(item.root)
            }
    }

    fun render(photos: List<String>, enabled: Boolean = true) {
        slots.forEachIndexed { index, item ->
            val uri = photos.getOrNull(index)?.takeIf(String::isNotBlank)
            item.root.isVisible = onEdit != null || uri != null
            item.root.isEnabled = enabled
            item.root.isClickable = (onEdit != null || onOpen != null) && enabled
            item.root.isFocusable = onEdit != null || onOpen != null
            item.root.contentDescription = container.context.getString(
                if (uri == null) R.string.plant_health_photo_add_slot else R.string.plant_health_photo_slot,
                index + 1
            )
            item.imgPhoto.bindRecordPhoto(uri)
        }
        container.isVisible = onEdit != null || photos.any(String::isNotBlank)
    }
}
