package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.google.android.material.card.MaterialCardView

/** The approved review cards; content remains tied to recorded evidence. */
internal class LivestockHealthAssessmentUi(context: Context) : LivestockHealthComponents(context) {

    fun highlight(heading: String, detail: String, onOpen: () -> Unit): MaterialCardView {
        val line = row()
        line.addView(image(R.drawable.ic_health_surface,
            R.dimen.aqua_size_56, R.dimen.aqua_size_56).apply {
            setColorFilter(ContextCompat.getColor(context, R.color.aqua_content_warning))
            contentDescription = null
        })
        line.addView(column().apply {
            addView(text(heading, R.dimen.aqua_text_size_title_small,
                R.color.aqua_content_warning, bold = true))
            addView(spacer(R.dimen.aqua_size_4))
            addView(text(detail, colorRes = R.color.aqua_card_text_secondary))
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply {
                marginStart = size(R.dimen.aqua_size_12)
            }
        })
        return card(R.color.aqua_content_warning, column().apply { addView(line) }).apply {
            setCardBackgroundColor(ContextCompat.getColor(context,
                R.color.aqua_bg_maintenance_profile_percent_warning_fill))
            isClickable = true
            isFocusable = true
            setOnClickListener { onOpen() }
        }
    }

    fun evidence(icon: Int, title: String, detail: String): MaterialCardView {
        val line = row()
        line.addView(image(icon, R.dimen.aqua_size_36, R.dimen.aqua_size_36).apply {
            setColorFilter(ContextCompat.getColor(context, R.color.aqua_accent_primary))
            contentDescription = null
        })
        line.addView(column().apply {
            addView(text(title, R.dimen.aqua_text_size_body_large, bold = true))
            addView(text(detail, colorRes = R.color.aqua_card_text_secondary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = size(R.dimen.aqua_size_12) }
        })
        return card(content = column().apply { addView(line) })
    }

    fun step(number: Int, icon: Int, heading: String, detail: String,
        onOpen: () -> Unit): MaterialCardView {
        val line = row()
        line.addView(text(number.toString(), R.dimen.aqua_text_size_body_large,
            R.color.aqua_accent_primary, bold = true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size(R.dimen.aqua_size_32),
                size(R.dimen.aqua_size_40))
        })
        line.addView(image(icon, R.dimen.aqua_size_32, R.dimen.aqua_size_32).apply {
            setColorFilter(ContextCompat.getColor(context, R.color.aqua_accent_primary))
            contentDescription = null
        })
        line.addView(column().apply {
            addView(text(heading, R.dimen.aqua_text_size_body_large, bold = true))
            addView(text(detail, colorRes = R.color.aqua_card_text_secondary))
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply {
                marginStart = size(R.dimen.aqua_size_12)
            }
        })
        return card(content = column().apply { addView(line) }).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { onOpen() }
        }
    }
}
