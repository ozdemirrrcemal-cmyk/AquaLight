package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import android.net.Uri
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.LivestockHealthSymptom
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

internal class LivestockHealthUi(private val context: Context) {
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    fun size(resource: Int): Int = context.resources.getDimensionPixelSize(resource)

    fun column(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(match, wrap)
    }

    fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(match, wrap)
    }

    fun spacer(height: Int = R.dimen.aqua_size_16): View = Space(context).apply {
        layoutParams = LinearLayout.LayoutParams(match, size(height))
    }

    fun text(
        value: CharSequence,
        sizeRes: Int = R.dimen.aqua_text_size_body,
        @ColorRes colorRes: Int = R.color.aqua_card_text_primary,
        bold: Boolean = false
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size(sizeRes).toFloat())
        setTextColor(ContextCompat.getColor(context, colorRes))
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    fun heading(@StringRes res: Int): TextView = text(
        context.getString(res), R.dimen.aqua_text_size_title_small,
        bold = true
    )

    fun card(
        @ColorRes outline: Int = R.color.aqua_card_outline,
        content: LinearLayout = column()
    ): MaterialCardView = MaterialCardView(context).apply {
        layoutParams = LinearLayout.LayoutParams(match, wrap)
        radius = size(R.dimen.aqua_size_20).toFloat()
        strokeWidth = size(R.dimen.aqua_size_1)
        strokeColor = ContextCompat.getColor(context, outline)
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.aqua_card_surface))
        cardElevation = size(R.dimen.aqua_size_0).toFloat()
        content.setPadding(
            size(R.dimen.aqua_size_16), size(R.dimen.aqua_size_16),
            size(R.dimen.aqua_size_16), size(R.dimen.aqua_size_16)
        )
        addView(content)
    }

    fun image(@DrawableRes imageRes: Int, width: Int, height: Int): ImageView =
        ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(size(width), size(height))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(imageRes)
        }

    fun speciesImage(livestock: AquariumLivestock, width: Int, height: Int): ImageView =
        image(LivestockCategories.iconRes(livestock.category), width, height).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(size(R.dimen.aqua_size_12), size(R.dimen.aqua_size_12),
                size(R.dimen.aqua_size_12), size(R.dimen.aqua_size_12))
            setColorFilter(ContextCompat.getColor(context,
                LivestockCategories.colorRes(livestock.category)))
            contentDescription = context.getString(
                R.string.livestock_health_accessibility_species_image,
                context.getString(LivestockCategories.labelRes(livestock.category))
            )
        }

    fun button(@StringRes label: Int, onClick: () -> Unit): MaterialButton =
        MaterialButton(context).apply {
            text = context.getString(label)
            setTextColor(ContextCompat.getColor(context, R.color.aqua_content_on_dark))
            backgroundTintList = ContextCompat.getColorStateList(
                context, R.color.aqua_accent_primary
            )
            cornerRadius = size(R.dimen.aqua_size_14)
            minHeight = size(R.dimen.aqua_size_52)
            setOnClickListener { onClick() }
        }

    fun choice(
        label: CharSequence,
        selected: Boolean,
        onClick: () -> Unit
    ): MaterialCardView {
        val content = column()
        content.addView(text(label, R.dimen.aqua_text_size_body, bold = selected))
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

    fun hero(tankPhotoUri: String?): MaterialCardView {
        val content = column()
        val frame = FrameLayout(context)
        frame.layoutParams = LinearLayout.LayoutParams(match, size(R.dimen.aqua_size_240))
        frame.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (tankPhotoUri.isNullOrBlank()) {
                setImageResource(R.drawable.nature_aquarium)
            } else {
                load(Uri.parse(tankPhotoUri)) {
                    placeholder(R.drawable.nature_aquarium)
                    error(R.drawable.nature_aquarium)
                }
            }
            contentDescription = null
        }, FrameLayout.LayoutParams(match, match))
        val captions = column().apply {
            setPadding(
                size(R.dimen.aqua_size_16), size(R.dimen.aqua_size_8),
                size(R.dimen.aqua_size_16), size(R.dimen.aqua_size_16)
            )
            addView(text(
                context.getString(R.string.livestock_health_home_hero_title),
                R.dimen.aqua_text_size_title_small, bold = true
            ))
            addView(text(
                context.getString(R.string.livestock_health_home_hero_subtitle),
                colorRes = R.color.aqua_card_text_secondary
            ))
        }
        frame.addView(captions, FrameLayout.LayoutParams(match, wrap, Gravity.BOTTOM))
        content.addView(frame)
        return card(content = content)
    }

}
