package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentPlantHealthDetailBinding

internal enum class PlantHealthDetailTab { OVERVIEW, OBSERVATIONS, CARE, NOTES }

internal fun FragmentPlantHealthDetailBinding.bindPlantHealthTabs(select: (PlantHealthDetailTab) -> Unit) {
    tabOverview.setOnClickListener { select(PlantHealthDetailTab.OVERVIEW) }
    tabObservations.setOnClickListener { select(PlantHealthDetailTab.OBSERVATIONS) }
    tabCare.setOnClickListener { select(PlantHealthDetailTab.CARE) }
    tabNotes.setOnClickListener { select(PlantHealthDetailTab.NOTES) }
}

internal fun TextView.setPlantHealthTabSelected(selected: Boolean) {
    setTextColor(ContextCompat.getColor(context,
        if (selected) R.color.aqua_accent_primary else R.color.aqua_content_secondary
    ))
    setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
}
