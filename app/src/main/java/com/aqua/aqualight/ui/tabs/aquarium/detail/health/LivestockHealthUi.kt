package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import android.net.Uri
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.LivestockHealthSymptom
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.google.android.material.card.MaterialCardView

internal class LivestockHealthUi(context: Context) : LivestockHealthComponents(context) {

    fun speciesImage(
        livestock: AquariumLivestock, width: Int, height: Int, photoUri: String? = null
    ): ImageView =
        image(LivestockCategories.iconRes(livestock.category), width, height).apply {
            if (photoUri.isNullOrBlank()) {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(size(R.dimen.aqua_size_12), size(R.dimen.aqua_size_12),
                    size(R.dimen.aqua_size_12), size(R.dimen.aqua_size_12))
                setColorFilter(ContextCompat.getColor(context,
                    LivestockCategories.colorRes(livestock.category)))
            } else {
                scaleType = ImageView.ScaleType.CENTER_CROP
                load(Uri.parse(photoUri)) {
                    placeholder(LivestockCategories.iconRes(livestock.category))
                    error(LivestockCategories.iconRes(livestock.category))
                }
            }
            contentDescription = context.getString(
                R.string.livestock_health_accessibility_species_image,
                context.getString(LivestockCategories.labelRes(livestock.category))
            )
        }

    fun speciesChoice(
        livestock: AquariumLivestock,
        selected: Boolean,
        photoUri: String?,
        onClick: () -> Unit
    ): MaterialCardView {
        val content = row()
        content.addView(speciesImage(livestock, R.dimen.aqua_size_52,
            R.dimen.aqua_size_52, photoUri))
        val labels = column()
        labels.addView(text(livestock.name, bold = true))
        labels.addView(text(
            context.getString(LivestockCategories.labelRes(livestock.category)) +
                " · " + livestock.quantity,
            colorRes = R.color.aqua_card_text_secondary
        ))
        labels.layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply {
            marginStart = size(R.dimen.aqua_size_12)
        }
        content.addView(labels)
        return card(
            if (selected) R.color.aqua_accent_primary else R.color.aqua_card_outline,
            content
        ).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    fun symptomName(symptom: LivestockHealthSymptom): String = context.getString(
        when (symptom) {
            LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE -> R.string.livestock_health_symptom_surface
            LivestockHealthSymptom.APPETITE_CHANGE -> R.string.livestock_health_symptom_appetite
            LivestockHealthSymptom.SWIMMING_CHANGE -> R.string.livestock_health_symptom_swimming
            LivestockHealthSymptom.SKIN_OR_SPOTS -> R.string.livestock_health_symptom_skin
            LivestockHealthSymptom.FIN_CHANGE -> R.string.livestock_health_symptom_fins
            LivestockHealthSymptom.ACTIVITY_CHANGE -> R.string.livestock_health_symptom_activity
            LivestockHealthSymptom.COLOR_CHANGE -> R.string.livestock_health_symptom_color
            LivestockHealthSymptom.MOLTING_CHANGE -> R.string.livestock_health_symptom_molting
            LivestockHealthSymptom.SHELL_CHANGE -> R.string.livestock_health_symptom_shell
            LivestockHealthSymptom.POLYP_RETRACTION -> R.string.livestock_health_symptom_polyps
            LivestockHealthSymptom.OTHER -> R.string.livestock_health_symptom_other
        }
    )

    fun hero(isEmpty: Boolean, enabled: Boolean, onNewObservation: () -> Unit): MaterialCardView {
        val content = column()
        val frame = FrameLayout(context)
        frame.layoutParams = LinearLayout.LayoutParams(match, size(R.dimen.aqua_size_320))
        frame.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(if (isEmpty) R.drawable.livestock_health_empty_hero
                else R.drawable.livestock_health_hero)
            contentDescription = null
        }, FrameLayout.LayoutParams(match, match))
        frame.addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(ContextCompat.getColor(context, R.color.aqua_color_transparent),
                    ContextCompat.getColor(context, R.color.aqua_card_surface))
            )
        }, FrameLayout.LayoutParams(match, match))
        val captions = column().apply {
            setPadding(
                size(R.dimen.aqua_size_16), size(R.dimen.aqua_size_8),
                size(R.dimen.aqua_size_16), size(R.dimen.aqua_size_16)
            )
            addView(text(
                context.getString(if (isEmpty) R.string.livestock_health_empty_hero_title
                    else R.string.livestock_health_home_hero_title),
                R.dimen.aqua_text_size_title_small, bold = true
            ))
            addView(text(
                context.getString(if (isEmpty) R.string.livestock_health_empty_hero_subtitle
                    else R.string.livestock_health_home_hero_subtitle),
                colorRes = R.color.aqua_card_text_secondary
            ))
            addView(spacer(R.dimen.aqua_size_12))
            addView(button(R.string.livestock_health_home_new_observation, onNewObservation).apply {
                isEnabled = enabled
            })
        }
        frame.addView(captions, FrameLayout.LayoutParams(match, wrap, Gravity.BOTTOM))
        content.addView(frame)
        return card(content = content)
    }

    fun emptyState(): MaterialCardView {
        val content = column().apply {
            gravity = Gravity.CENTER
            minimumHeight = size(R.dimen.aqua_size_220)
            addView(image(R.drawable.ic_life_fish_24,
                R.dimen.aqua_size_72, R.dimen.aqua_size_72).apply {
                setColorFilter(ContextCompat.getColor(context, R.color.aqua_accent_primary))
            })
            addView(spacer())
            addView(text(context.getString(R.string.livestock_health_home_no_records),
                R.dimen.aqua_text_size_title_small, bold = true))
            addView(spacer(R.dimen.aqua_size_8))
            addView(text(context.getString(R.string.livestock_health_home_no_records_hint),
                colorRes = R.color.aqua_card_text_secondary))
        }
        content.background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, R.color.aqua_card_surface))
            cornerRadius = size(R.dimen.aqua_size_20).toFloat()
            setStroke(size(R.dimen.aqua_size_1),
                ContextCompat.getColor(context, R.color.aqua_card_outline),
                size(R.dimen.aqua_size_8).toFloat(),
                size(R.dimen.aqua_size_8).toFloat())
        }
        return card(content = content)
    }

}
