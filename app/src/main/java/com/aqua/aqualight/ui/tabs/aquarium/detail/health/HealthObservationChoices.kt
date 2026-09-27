package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.google.android.material.checkbox.MaterialCheckBox

/** Localized, explicit unknown choices; single-valued fields never silently select the first option. */
internal fun <T : Enum<T>> LinearLayout.healthChoices(values: List<T>, selected: Set<String>,
    single: Boolean = false, changed: (List<String>) -> Unit) {
    removeAllViews()
    var updating = false
    val choices = values.map { value ->
        MaterialCheckBox(context).apply {
            setText(HealthObservationLabels.label(value))
            setTextColor(ContextCompat.getColor(context, R.color.aqua_card_text_primary))
            buttonTintList = ContextCompat.getColorStateList(context, R.color.aqua_choice_tint)
            minHeight = resources.getDimensionPixelSize(R.dimen.aqua_size_48)
            isChecked = value.name in selected
            tag = value.name
            this@healthChoices.addView(this)
        }
    }
    choices.forEach { choice ->
        choice.setOnCheckedChangeListener { _, checked ->
            if (!updating) {
                updating = true
                if (checked) choices.filter { it !== choice }.forEach { other ->
                    if (single || choice.tag == "UNKNOWN" || other.tag == "UNKNOWN") other.isChecked = false
                }
                changed(choices.filter { it.isChecked }.map { it.tag as String })
                updating = false
            }
        }
    }
}
