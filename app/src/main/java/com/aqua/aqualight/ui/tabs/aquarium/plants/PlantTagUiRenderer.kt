package com.aqua.aqualight.ui.tabs.aquarium.plants

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.google.android.material.card.MaterialCardView

object PlantTagUiRenderer {
    fun renderSelectedPlantList(
        container: LinearLayout,
        plants: List<TankPlantTag>,
        onRemoveAt: (Int) -> Unit
    ) {
        container.removeAllViews()

        plants.forEachIndexed { index, plant ->
            container.addView(
                createPlantCard(
                    context = container.context,
                    index = index,
                    plant = plant,
                    onRemoveAt = onRemoveAt
                )
            )
        }
    }

    fun renderMarkers(
        container: FrameLayout,
        plants: List<TankPlantTag>
    ) {
        container.removeAllViews()

        container.post {
            val width = container.width
            val height = container.height

            if (width <= 0 || height <= 0) {
                return@post
            }

            plants.forEachIndexed { index, plant ->
                val size = container.context.dp(30)
                val marker = TextView(container.context).apply {
                    text = "${index + 1}"
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    setTypeface(null, Typeface.BOLD)
                    setBackgroundResource(R.drawable.bg_plant_marker)
                    includeFontPadding = false
                }

                val params = FrameLayout.LayoutParams(size, size)
                marker.x = (plant.markerX * width) - size / 2f
                marker.y = (plant.markerY * height) - size / 2f

                container.addView(marker, params)
            }
        }
    }

    private fun createPlantCard(
        context: Context,
        index: Int,
        plant: TankPlantTag,
        onRemoveAt: (Int) -> Unit
    ): MaterialCardView {
        val card = MaterialCardView(context).apply {
            radius = context.dp(18).toFloat()
            strokeWidth = context.dp(1)
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#10233A"))
            cardElevation = 0f
            useCompatPadding = false

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = context.dp(12)
            layoutParams = params
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                context.dp(14),
                context.dp(12),
                context.dp(12),
                context.dp(12)
            )
        }

        val number = TextView(context).apply {
            text = "${index + 1}"
            gravity = Gravity.CENTER
            textSize = 13f
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_plant_number_circle)

            layoutParams = LinearLayout.LayoutParams(
                context.dp(38),
                context.dp(38)
            )
        }

        val textBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginStart = context.dp(14)
            layoutParams = params
        }

        val categoryText = TextView(context).apply {
            text = plant.category
            textSize = 12f
            includeFontPadding = false
            setTextColor(Color.parseColor("#8FA4BE"))
        }

        val nameText = TextView(context).apply {
            text = plant.plantName
            textSize = 14f
            includeFontPadding = false
            maxLines = 2
            setTextColor(Color.WHITE)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = context.dp(6)
            layoutParams = params
        }

        val delete = TextView(context).apply {
            text = context.getString(R.string.common_close)
            textSize = 26f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.parseColor("#8FA4BE"))
            contentDescription = context.getString(R.string.aquarium_remove_plant_tag)

            setOnClickListener {
                onRemoveAt(index)
            }

            layoutParams = LinearLayout.LayoutParams(
                context.dp(36),
                context.dp(36)
            )
        }

        textBox.addView(categoryText)
        textBox.addView(nameText)
        row.addView(number)
        row.addView(textBox)
        row.addView(delete)
        card.addView(row)

        return card
    }

    private fun Context.dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
