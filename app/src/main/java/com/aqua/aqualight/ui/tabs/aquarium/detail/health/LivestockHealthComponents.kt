package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** Reusable feature components backed by the central Aqua design tokens. */
internal open class LivestockHealthComponents(protected val context: Context) {
    protected val match = ViewGroup.LayoutParams.MATCH_PARENT
    protected val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
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

}
