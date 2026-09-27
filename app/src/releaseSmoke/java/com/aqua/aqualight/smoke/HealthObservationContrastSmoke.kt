package com.aqua.aqualight.smoke

import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.aqua.aqualight.R

/** Checks rendered choice colors against the actual health-screen surface in every visual profile. */
internal object HealthObservationContrastSmoke {
    fun verify(root: View) {
        val background = ContextCompat.getColor(root.context, R.color.background_color)
        visit(root, background)
    }

    private fun visit(view: View, background: Int) {
        if (!view.isShown) return
        if (view is CompoundButton && view.isEnabled) {
            check(ColorUtils.calculateContrast(view.currentTextColor, background) >= MIN_TEXT_CONTRAST) {
                "Health choice text has insufficient contrast: ${view.text}"
            }
            val tint = checkNotNull(view.buttonTintList) { "Health choice has no explicit tint" }
            val color = tint.getColorForState(view.drawableState, tint.defaultColor)
            check(ColorUtils.calculateContrast(color, background) >= MIN_CONTROL_CONTRAST) {
                "Health choice control has insufficient contrast: ${view.text}"
            }
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index), background)
    }

    private const val MIN_TEXT_CONTRAST = 4.5
    private const val MIN_CONTROL_CONTRAST = 3.0
}
