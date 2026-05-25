package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aqua.aqualight.ui.tabs.devices.detail.DeviceVisualSpec
import com.google.android.material.card.MaterialCardView
import java.util.Locale
import kotlin.math.roundToInt

class CoolingManagementRenderer(
    private val container: LinearLayout,
    private val visualSpec: DeviceVisualSpec,
    private val onFanCardClick: (
        fan: CoolingDeviceRepository.FanChannelData,
        rule: CoolingDeviceRepository.CoolRuleData?
    ) -> Unit
) {

    fun render(
        data: CoolingDeviceRepository.CoolingDashboardData
    ) {
        container.removeAllViews()

        if (data.fanChannels.isEmpty()) {
            container.addView(
                createEmptyView(
                    text = "No fan channel found"
                )
            )
            return
        }

        data.fanChannels.forEachIndexed { index, fan ->
            val rule = data.ruleForFan(
                fan = fan
            )

            val usedSensors = data.usedSensorsFor(
                rule = rule
            )

            container.addView(
                createFanCard(
                    index = index,
                    fan = fan,
                    rule = rule,
                    usedSensors = usedSensors
                )
            )
        }
    }

    fun clear() {
        container.removeAllViews()

        container.addView(
            createEmptyView(
                text = "No cooling data"
            )
        )
    }

    private fun createFanCard(
        index: Int,
        fan: CoolingDeviceRepository.FanChannelData,
        rule: CoolingDeviceRepository.CoolRuleData?,
        usedSensors: List<CoolingDeviceRepository.TemperatureSensorData>
    ): MaterialCardView {
        val context = container.context

        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    topMargin = 12.dp(context)
                }
            }

            setCardBackgroundColor(
                Color.parseColor("#101F33")
            )

            strokeColor = Color.parseColor("#10243A")
            strokeWidth = 1.dp(context)

            radius = 18.dp(context).toFloat()
            cardElevation = 0f

            isClickable = true
            isFocusable = true
            foreground = selectableForeground(
                context = context
            )

            setOnClickListener {
                onFanCardClick(
                    fan,
                    rule
                )
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                14.dp(context),
                14.dp(context),
                14.dp(context),
                14.dp(context)
            )
        }

        content.addView(
            createCardHeader(
                fanName = fan.name
            )
        )

        content.addView(
            createRow(
                label = "Mode",
                value = fan.regime.displayName
            )
        )

        content.addView(
            createRow(
                label = "Output",
                value = formatFanOutput(
                    value = fan.vNow
                )
            )
        )

        content.addView(
            createRow(
                label = "Automation Status",
                value = resolveAutomationStatus(
                    rule = rule,
                    fan = fan,
                    usedSensorCount = usedSensors.size
                )
            )
        )

        content.addView(
            createRow(
                label = "Start Cooling",
                value = rule?.let {
                    formatTemperature(
                        value = it.tMin
                    )
                } ?: "--"
            )
        )

        content.addView(
            createRow(
                label = "Full Power",
                value = rule?.let {
                    formatTemperature(
                        value = it.tMax
                    )
                } ?: "--"
            )
        )

        content.addView(
            createRow(
                label = "Power Range",
                value = "${formatPercent(fan.vMin)} - ${formatPercent(fan.vMax)}"
            )
        )

        content.addView(
            createRow(
                label = "Sensors",
                value = if (usedSensors.isEmpty()) {
                    "No sensor selected"
                } else {
                    usedSensors.joinToString(
                        separator = ", "
                    ) { sensor ->
                        sensor.name
                    }
                },
                allowMultiLine = true
            )
        )

        card.addView(
            content
        )

        return card
    }

    private fun createCardHeader(
    fanName: String
): View {
    val context = container.context

    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(
            0,
            0,
            0,
            10.dp(context)
        )

        val titleView = TextView(context).apply {
            text = "Cooling"
            setTextColor(
                Color.parseColor("#E6EDF7")
            )
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val fanNameView = TextView(context).apply {
            text = fanName
            setTextColor(
                visualSpec.accentColor
            )
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.END
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        addView(
            titleView,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        addView(
            fanNameView,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
    }
}

    private fun createRow(
        label: String,
        value: String,
        allowMultiLine: Boolean = false
    ): View {
        val context = container.context

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(
                0,
                8.dp(context),
                0,
                0
            )

            val labelView = TextView(context).apply {
                text = label
                setTextColor(
                    Color.parseColor("#9FAABB")
                )
                textSize = 14f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val valueView = TextView(context).apply {
                text = value
                setTextColor(
                    Color.parseColor("#E6EDF7")
                )
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.END

                if (allowMultiLine) {
                    maxLines = 2
                } else {
                    maxLines = 1
                }

                ellipsize = TextUtils.TruncateAt.END
            }

            addView(
                labelView,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            addView(
                valueView,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }
    }

    private fun createEmptyView(
        text: String
    ): TextView {
        val context = container.context

        return TextView(context).apply {
            this.text = text
            setTextColor(
                Color.parseColor("#9FAABB")
            )
            textSize = 14f
            setPadding(
                0,
                8.dp(context),
                0,
                0
            )
        }
    }

    private fun CoolingDeviceRepository.CoolingDashboardData.ruleForFan(
        fan: CoolingDeviceRepository.FanChannelData
    ): CoolingDeviceRepository.CoolRuleData? {
        val fanGpio = fan.gpioPwm.trim()

        if (fanGpio.isBlank() || fanGpio == "-") {
            return null
        }

        return coolRules.firstOrNull { rule ->
            rule.gpioPwm.trim().equals(
                fanGpio,
                ignoreCase = true
            )
        }
    }

    private fun resolveAutomationStatus(
        rule: CoolingDeviceRepository.CoolRuleData?,
        fan: CoolingDeviceRepository.FanChannelData,
        usedSensorCount: Int
    ): String {
        if (rule == null) {
            return "No rule"
        }

        return when (fan.regime) {
            CoolingDeviceRepository.FanRegime.OFF -> {
                "Off"
            }

            CoolingDeviceRepository.FanRegime.ON -> {
                "Manual On"
            }

            CoolingDeviceRepository.FanRegime.AUTO -> {
                when {
                    !rule.enabled -> "Disabled"
                    usedSensorCount <= 0 -> "No sensor"
                    else -> "Active"
                }
            }
        }
    }

    private fun formatFanOutput(
        value: Float?
    ): String {
        if (value == null || value < 0f) {
            return "--"
        }

        return "${normalizePercent(value).roundToInt()}%"
    }

    private fun formatPercent(
        value: Float
    ): String {
        return "${normalizePercent(value).roundToInt()}%"
    }

    private fun normalizePercent(
        value: Float
    ): Float {
        return if (value <= 1f) {
            value.coerceIn(
                0f,
                1f
            ) * 100f
        } else {
            value.coerceIn(
                0f,
                100f
            )
        }
    }

    private fun formatTemperature(
        value: Float
    ): String {
        return String.format(
            Locale.US,
            "%.1f °C",
            value
        )
    }

    private fun roundedStrokeDrawable(
        fillColor: Int,
        strokeColor: Int,
        radiusDp: Int,
        context: Context
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE

            setColor(
                fillColor
            )

            setStroke(
                1.dp(context),
                strokeColor
            )

            cornerRadius = radiusDp.dp(
                context = context
            ).toFloat()
        }
    }

    private fun selectableForeground(
        context: Context
    ): Drawable? {
        val typedValue = TypedValue()

        context.theme.resolveAttribute(
            android.R.attr.selectableItemBackground,
            typedValue,
            true
        )

        return ContextCompat.getDrawable(
            context,
            typedValue.resourceId
        )
    }

    private fun Int.dp(
        context: Context
    ): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}