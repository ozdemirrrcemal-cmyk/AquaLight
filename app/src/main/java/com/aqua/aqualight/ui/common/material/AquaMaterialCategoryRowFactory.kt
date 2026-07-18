package com.aqua.aqualight.ui.common.material

import com.aqua.aqualight.ui.common.text.setTextSizeResource
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import java.util.Locale

object AquaMaterialCategoryRowFactory {

    fun create(
        context: Context,
        title: String,
        summary: String,
        iconText: String = title.take(2).uppercase(Locale.getDefault()),
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(
                context,
                R.drawable.bg_aqua_selection_row
            )

            setPadding(
                context.dp(14),
                context.dp(12),
                context.dp(12),
                context.dp(12)
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = context.dp(10)
            }
        }

        val iconBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(
                ContextCompat.getColor(
                    context,
                    R.color.aqua_card_icon_accent_surface
                )
            )
            cornerRadius = context.dp(12).toFloat()
        }

        val iconBox = TextView(context).apply {
            text = iconText
            gravity = Gravity.CENTER
            setTextSizeResource(R.dimen.aqua_text_size_status_micro)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = iconBackground
            includeFontPadding = false

            layoutParams = LinearLayout.LayoutParams(
                context.dp(42),
                context.dp(42)
            )
        }

        val textBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = context.dp(14)
            }
        }

        val titleText = TextView(context).apply {
            text = title
            setTextSizeResource(R.dimen.aqua_text_size_body)
            setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.aqua_card_text_primary
                )
            )
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val summaryText = TextView(context).apply {
            text = summary
            setTextSizeResource(R.dimen.aqua_text_size_caption)
            setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.aqua_card_text_secondary
                )
            )

            setLineSpacing(
                context.dp(2).toFloat(),
                1.0f
            )

            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = context.dp(6)
            }
        }

        val arrow = ImageView(context).apply {
            setImageResource(R.drawable.ic_arrow_right)
            setColorFilter(
                ContextCompat.getColor(
                    context,
                    R.color.aqua_card_text_secondary
                )
            )
            scaleType = ImageView.ScaleType.CENTER

            layoutParams = LinearLayout.LayoutParams(
                context.dp(22),
                context.dp(22)
            )
        }

        textBox.addView(titleText)
        textBox.addView(summaryText)

        row.addView(iconBox)
        row.addView(textBox)
        row.addView(arrow)

        row.setOnClickListener {
            onClick()
        }

        return row
    }

    private fun Context.dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
