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
        onClick: () -> Unit
    ): MaterialCardView {
        val content = row()
        content.addView(speciesImage(livestock, R.dimen.aqua_size_40,
            R.dimen.aqua_size_40))
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
        content.addView(View(context).apply {
            val diameter = size(R.dimen.aqua_size_20)
            layoutParams = LinearLayout.LayoutParams(diameter, diameter)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context,
                    if (selected) R.color.aqua_accent_primary else R.color.aqua_card_surface))
                setStroke(size(R.dimen.aqua_size_2), ContextCompat.getColor(context,
                    if (selected) R.color.aqua_accent_primary else R.color.aqua_card_text_secondary))
            }
            isSelected = selected
        })
        return card(
            if (selected) R.color.aqua_accent_primary else R.color.aqua_card_outline,
            content
        ).apply {
            content.setPadding(size(R.dimen.aqua_size_10), size(R.dimen.aqua_size_8),
                size(R.dimen.aqua_size_10), size(R.dimen.aqua_size_8))
            minimumHeight = size(R.dimen.aqua_size_56)
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

    fun symptomChoice(symptom: LivestockHealthSymptom, selected: Boolean,
        onClick: () -> Unit): MaterialCardView {
        val content = column().apply {
            gravity = Gravity.CENTER
            minimumHeight = size(R.dimen.aqua_size_52)
            addView(image(symptomIcon(symptom), R.dimen.aqua_size_24,
                R.dimen.aqua_size_24).apply {
                setColorFilter(ContextCompat.getColor(context,
                    if (selected) R.color.aqua_accent_primary else R.color.aqua_card_text_secondary))
                layoutParams = LinearLayout.LayoutParams(size(R.dimen.aqua_size_24),
                    size(R.dimen.aqua_size_24)).apply { gravity = Gravity.CENTER_HORIZONTAL }
                contentDescription = null
            })
            addView(spacer(R.dimen.aqua_size_4))
            addView(text(symptomName(symptom), R.dimen.aqua_text_size_body_small).apply {
                gravity = Gravity.CENTER
                maxLines = 3
            })
        }
        return card(if (selected) R.color.aqua_accent_primary else R.color.aqua_card_outline,
            content).apply {
            content.setPadding(size(R.dimen.aqua_size_4), size(R.dimen.aqua_size_4),
                size(R.dimen.aqua_size_4), size(R.dimen.aqua_size_4))
            minimumHeight = size(R.dimen.aqua_size_60)
            isClickable = true
            isFocusable = true
            isSelected = selected
            setOnClickListener { onClick() }
        }
    }

    private fun symptomIcon(symptom: LivestockHealthSymptom): Int = when (symptom) {
        LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE -> R.drawable.ic_health_surface
        LivestockHealthSymptom.APPETITE_CHANGE -> R.drawable.ic_health_appetite
        LivestockHealthSymptom.SWIMMING_CHANGE -> R.drawable.ic_health_swimming
        LivestockHealthSymptom.SKIN_OR_SPOTS -> R.drawable.ic_health_spots
        LivestockHealthSymptom.FIN_CHANGE -> R.drawable.ic_health_fin
        LivestockHealthSymptom.OTHER -> R.drawable.ic_life_other_24
        else -> R.drawable.ic_life_fish_24
    }

    fun hero(isEmpty: Boolean, enabled: Boolean, onNewObservation: () -> Unit): MaterialCardView {
        val frame = FrameLayout(context)
        frame.layoutParams = LinearLayout.LayoutParams(match, size(R.dimen.aqua_size_270))
        frame.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(if (isEmpty) R.drawable.livestock_health_empty_hero
                else R.drawable.livestock_health_hero)
            contentDescription = null
        }, FrameLayout.LayoutParams(match, size(R.dimen.aqua_size_180), Gravity.TOP))
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
        return MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(match, wrap)
            radius = size(R.dimen.aqua_size_20).toFloat()
            strokeWidth = size(R.dimen.aqua_size_1)
            strokeColor = ContextCompat.getColor(context, R.color.aqua_card_outline)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.aqua_card_surface))
            setContentPadding(size(R.dimen.aqua_size_10), size(R.dimen.aqua_size_10),
                size(R.dimen.aqua_size_10), size(R.dimen.aqua_size_10))
            addView(frame)
        }
    }

    fun emptyState(): MaterialCardView {
        val content = column().apply {
            gravity = Gravity.CENTER
            minimumHeight = size(R.dimen.aqua_size_220)
            addView(image(R.drawable.ic_health_empty_fish,
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

    fun tankContext(summary: String, onOpen: () -> Unit): MaterialCardView {
        val layout = row()
        layout.addView(image(R.drawable.ic_care_water_test_24,
            R.dimen.aqua_size_48, R.dimen.aqua_size_48).apply {
            setPadding(size(R.dimen.aqua_size_10), size(R.dimen.aqua_size_10),
                size(R.dimen.aqua_size_10), size(R.dimen.aqua_size_10))
            setColorFilter(ContextCompat.getColor(context, R.color.aqua_accent_primary))
            contentDescription = null
        })
        layout.addView(column().apply {
            addView(text(context.getString(R.string.livestock_health_home_tank_summary),
                R.dimen.aqua_text_size_body_large, bold = true))
            addView(text(summary, colorRes = R.color.aqua_card_text_secondary))
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply {
                marginStart = size(R.dimen.aqua_size_12)
            }
        })
        layout.addView(text(context.getString(R.string.livestock_health_home_tank_open),
            R.dimen.aqua_text_size_body_small, R.color.aqua_accent_primary))
        return card(content = column().apply { addView(layout) }).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { onOpen() }
        }
    }

    fun statusBadge(label: String, active: Boolean): android.widget.TextView = text(
        label, R.dimen.aqua_text_size_body_small,
        if (active) R.color.aqua_content_warning else R.color.aqua_card_text_secondary
    ).apply {
        setPadding(size(R.dimen.aqua_size_10), size(R.dimen.aqua_size_6),
            size(R.dimen.aqua_size_10), size(R.dimen.aqua_size_6))
        background = GradientDrawable().apply {
            cornerRadius = size(R.dimen.aqua_size_20).toFloat()
            setColor(ContextCompat.getColor(context, if (active)
                R.color.aqua_bg_maintenance_profile_percent_warning_fill
            else R.color.aqua_bg_maintenance_tab_unselected_fill))
        }
    }

    fun compactChoice(label: String, selected: Boolean, onClick: () -> Unit): MaterialCardView {
        val content = column().apply {
            gravity = Gravity.CENTER
            minimumHeight = size(R.dimen.aqua_size_40)
            setPadding(size(R.dimen.aqua_size_6), size(R.dimen.aqua_size_6),
                size(R.dimen.aqua_size_6), size(R.dimen.aqua_size_6))
            addView(text(label, R.dimen.aqua_text_size_body_small, bold = selected).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            })
        }
        return card(if (selected) R.color.aqua_accent_primary else R.color.aqua_card_outline,
            content).apply {
            if (selected) setCardBackgroundColor(ContextCompat.getColor(context,
                R.color.aqua_bg_maintenance_tab_selected_fill))
            isClickable = true
            isFocusable = true
            isSelected = selected
            setOnClickListener { onClick() }
        }
    }

    fun countStepper(count: Int, maximum: Int, onDecrease: () -> Unit,
        onIncrease: () -> Unit): MaterialCardView {
        val content = row()
        content.addView(text(context.getString(R.string.common_minus),
            R.dimen.aqua_text_size_title_large, R.color.aqua_accent_primary).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, size(R.dimen.aqua_size_48), 1f)
            contentDescription = context.getString(R.string.livestock_health_count_decrease)
            isClickable = true
            isFocusable = true
            setOnClickListener { onDecrease() }
        })
        content.addView(text(context.getString(R.string.livestock_health_counter,
            count, maximum), R.dimen.aqua_text_size_body_large, bold = true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, size(R.dimen.aqua_size_48), 2f)
        })
        content.addView(text(context.getString(R.string.common_plus),
            R.dimen.aqua_text_size_title_large, R.color.aqua_accent_primary).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, size(R.dimen.aqua_size_48), 1f)
            contentDescription = context.getString(R.string.livestock_health_count_increase)
            isClickable = true
            isFocusable = true
            setOnClickListener { onIncrease() }
        })
        return card(content = column().apply { addView(content) })
    }

}
