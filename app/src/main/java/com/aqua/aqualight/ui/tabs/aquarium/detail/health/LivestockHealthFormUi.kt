package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.google.android.material.card.MaterialCardView

/** Progress and media controls in the approved observation form. */
internal class LivestockHealthFormUi(context: Context) : LivestockHealthComponents(context) {
    fun progress(): LinearLayout = column().apply {
        gravity = Gravity.END
        addView(text(context.getString(R.string.livestock_health_form_progress),
            R.dimen.aqua_text_size_caption, R.color.aqua_card_text_secondary).apply {
            gravity = Gravity.END
        })
        addView(View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = size(R.dimen.aqua_size_3).toFloat()
                setColor(ContextCompat.getColor(context, R.color.aqua_accent_primary))
            }
            layoutParams = LinearLayout.LayoutParams(size(R.dimen.aqua_size_80),
                size(R.dimen.aqua_size_3)).apply {
                gravity = Gravity.END
                topMargin = size(R.dimen.aqua_size_4)
            }
        })
    }

    fun photoPicker(livestock: AquariumLivestock, photoUri: String?,
        onClick: () -> Unit): MaterialCardView {
        val line = row()
        val icon = if (photoUri.isNullOrBlank()) {
            image(R.drawable.ic_camera_24, R.dimen.aqua_size_44, R.dimen.aqua_size_44)
        } else {
            LivestockHealthUi(context).speciesImage(livestock,
                R.dimen.aqua_size_44, R.dimen.aqua_size_44, photoUri)
        }
        line.addView(icon.apply { contentDescription = null })
        line.addView(column().apply {
            addView(text(context.getString(if (photoUri.isNullOrBlank())
                R.string.livestock_health_form_photo_add
            else R.string.livestock_health_form_photo_selected), bold = true))
            addView(text(context.getString(R.string.livestock_health_form_photo_formats),
                R.dimen.aqua_text_size_caption, R.color.aqua_card_text_secondary))
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply {
                marginStart = size(R.dimen.aqua_size_12)
            }
        })
        line.addView(text(context.getString(R.string.common_plus),
            R.dimen.aqua_text_size_title_large, R.color.aqua_accent_primary))
        val frame = column().apply {
            addView(line)
            setPadding(size(R.dimen.aqua_size_12), size(R.dimen.aqua_size_12),
                size(R.dimen.aqua_size_12), size(R.dimen.aqua_size_12))
            background = GradientDrawable().apply {
                cornerRadius = size(R.dimen.aqua_size_14).toFloat()
                setColor(ContextCompat.getColor(context, R.color.aqua_card_surface))
                setStroke(size(R.dimen.aqua_size_1),
                    ContextCompat.getColor(context, R.color.aqua_card_outline),
                    size(R.dimen.aqua_size_6).toFloat(),
                    size(R.dimen.aqua_size_4).toFloat())
            }
        }
        return MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(match, wrap)
            radius = size(R.dimen.aqua_size_14).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.aqua_card_surface))
            addView(frame)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }
}
