package com.aqua.aqualight.ui.common.pager

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R

/** Shared compact page indicator for swipeable Aqua content. */
class AquaPageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun render(pageCount: Int, selectedIndex: Int) {
        require(pageCount > 0)
        require(selectedIndex in 0 until pageCount)

        if (childCount != pageCount) {
            rebuildDots(pageCount)
        }
        repeat(pageCount) { index ->
            getChildAt(index).background = createDotDrawable(index == selectedIndex)
        }
        visibility = if (pageCount > 1) View.VISIBLE else View.GONE
    }

    private fun rebuildDots(pageCount: Int) {
        removeAllViews()
        val dotSize = resources.getDimensionPixelSize(R.dimen.aqua_size_6)
        val spacing = resources.getDimensionPixelSize(R.dimen.aqua_size_4)
        repeat(pageCount) {
            addView(
                View(context),
                LayoutParams(dotSize, dotSize).apply {
                    marginStart = spacing
                    marginEnd = spacing
                }
            )
        }
    }

    private fun createDotDrawable(selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                ContextCompat.getColor(
                    context,
                    if (selected) {
                        R.color.aqua_button_blue
                    } else {
                        R.color.aqua_card_outline
                    }
                )
            )
        }
}
