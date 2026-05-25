package com.aqua.aqualight.ui.tabs.devices.detail.timer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aqua.aqualight.databinding.FragmentDeviceTimerBinding
import com.google.android.material.card.MaterialCardView

class TimerDashboardRenderer(
    private val binding: FragmentDeviceTimerBinding
) {

    fun render(
        data: TimerDeviceRepository.TimerDashboardData
    ) {
        binding.tvActiveOutletCount.text =
            "${data.activeOutletCount()} / ${data.outlets.size.coerceAtLeast(4)}"

        binding.tvNextTimerEvent.text =
            data.nextEventText()

        binding.tvUpcomingTimer.text =
            buildNextTimerDescription(
                data = data
            )

        val outlets = data.outlets.take(
            4
        )

        renderOutlet(
            holder = outlet1(),
            outlet = outlets.getOrNull(0),
            rule = outlets.getOrNull(0)?.let { outlet ->
                data.ruleForOutlet(
                    outlet = outlet
                )
            },
            fallbackName = "Outlet 1"
        )

        renderOutlet(
            holder = outlet2(),
            outlet = outlets.getOrNull(1),
            rule = outlets.getOrNull(1)?.let { outlet ->
                data.ruleForOutlet(
                    outlet = outlet
                )
            },
            fallbackName = "Outlet 2"
        )

        renderOutlet(
            holder = outlet3(),
            outlet = outlets.getOrNull(2),
            rule = outlets.getOrNull(2)?.let { outlet ->
                data.ruleForOutlet(
                    outlet = outlet
                )
            },
            fallbackName = "Outlet 3"
        )

        renderOutlet(
            holder = outlet4(),
            outlet = outlets.getOrNull(3),
            rule = outlets.getOrNull(3)?.let { outlet ->
                data.ruleForOutlet(
                    outlet = outlet
                )
            },
            fallbackName = "Outlet 4"
        )
    }

    fun clear() {
        binding.tvActiveOutletCount.text = "--"
        binding.tvNextTimerEvent.text = "--"
        binding.tvUpcomingTimer.text = "Timer data is not available."

        renderOutlet(
            holder = outlet1(),
            outlet = null,
            rule = null,
            fallbackName = "Outlet 1"
        )

        renderOutlet(
            holder = outlet2(),
            outlet = null,
            rule = null,
            fallbackName = "Outlet 2"
        )

        renderOutlet(
            holder = outlet3(),
            outlet = null,
            rule = null,
            fallbackName = "Outlet 3"
        )

        renderOutlet(
            holder = outlet4(),
            outlet = null,
            rule = null,
            fallbackName = "Outlet 4"
        )
    }

    private fun renderOutlet(
        holder: OutletViewHolder,
        outlet: TimerDeviceRepository.TimerOutletData?,
        rule: TimerDeviceRepository.TimerRuleData?,
        fallbackName: String
    ) {
        val context = holder.card.context

        if (outlet == null) {
            holder.name.text = fallbackName
            holder.mode.text = "Off"
            holder.schedule.text = "No data"

            applyOutletStyle(
                holder = holder,
                regime = TimerDeviceRepository.OutletRegime.OFF,
                currentlyOn = false,
                context = context
            )

            return
        }

        val currentlyOn = outlet.isCurrentlyOn()

        holder.name.text = outlet.name
        holder.mode.text = outlet.regime.displayName
        holder.schedule.text = buildScheduleText(
            outlet = outlet,
            rule = rule
        )

        applyOutletStyle(
            holder = holder,
            regime = outlet.regime,
            currentlyOn = currentlyOn,
            context = context
        )
    }

    private fun applyOutletStyle(
        holder: OutletViewHolder,
        regime: TimerDeviceRepository.OutletRegime,
        currentlyOn: Boolean,
        context: Context
    ) {
        val cardBackgroundColor = if (currentlyOn) {
            Color.parseColor("#151F3D")
        } else {
            Color.parseColor("#131C34")
        }

        val cardStrokeColor = when {
            currentlyOn && regime == TimerDeviceRepository.OutletRegime.ON -> {
                Color.parseColor("#3A9F72")
            }

            currentlyOn -> {
                Color.parseColor("#5F55C8")
            }

            else -> {
                Color.parseColor("#2D385C")
            }
        }

        val powerCircleColor = if (currentlyOn) {
            when (regime) {
                TimerDeviceRepository.OutletRegime.ON -> Color.parseColor("#2EAE74")
                TimerDeviceRepository.OutletRegime.AUTO -> Color.parseColor("#6E63E8")
                TimerDeviceRepository.OutletRegime.OFF -> Color.parseColor("#26314F")
            }
        } else {
            Color.parseColor("#26314F")
        }

        val powerIconColor = if (currentlyOn) {
            Color.WHITE
        } else {
            Color.parseColor("#9FAABB")
        }

        val modeBackgroundColor = when (regime) {
            TimerDeviceRepository.OutletRegime.AUTO -> Color.parseColor("#29264A")
            TimerDeviceRepository.OutletRegime.ON -> Color.parseColor("#173B2B")
            TimerDeviceRepository.OutletRegime.OFF -> Color.parseColor("#1D263F")
        }

        val modeTextColor = when (regime) {
            TimerDeviceRepository.OutletRegime.AUTO -> Color.parseColor("#CFC8FF")
            TimerDeviceRepository.OutletRegime.ON -> Color.parseColor("#B7F4D4")
            TimerDeviceRepository.OutletRegime.OFF -> Color.parseColor("#9FAABB")
        }

        holder.card.setCardBackgroundColor(
            cardBackgroundColor
        )

        holder.card.strokeColor =
            cardStrokeColor

        holder.powerCard.setCardBackgroundColor(
            powerCircleColor
        )

        holder.powerIcon.imageTintList =
            ColorStateList.valueOf(
                powerIconColor
            )

        holder.mode.background = roundedDrawable(
            color = modeBackgroundColor,
            radiusDp = 100,
            context = context
        )

        holder.mode.setTextColor(
            modeTextColor
        )

        holder.powerCard.foreground =
            selectableForeground(
                context = context
            )
    }

    private fun buildScheduleText(
        outlet: TimerDeviceRepository.TimerOutletData,
        rule: TimerDeviceRepository.TimerRuleData?
    ): String {
        return when (outlet.regime) {
            TimerDeviceRepository.OutletRegime.ON -> {
                "Manual control"
            }

            TimerDeviceRepository.OutletRegime.OFF -> {
                "Disabled"
            }

            TimerDeviceRepository.OutletRegime.AUTO -> {
                rule?.compactScheduleText() ?: "No schedule"
            }
        }
    }

    private fun buildNextTimerDescription(
        data: TimerDeviceRepository.TimerDashboardData
    ): String {
        val nextRule = data.nextRule()
            ?: return "No scheduled timer."

        val outlet = data.outlets.firstOrNull { item ->
            item.gpioPwm.trim().equals(
                nextRule.gpioPwm.trim(),
                ignoreCase = true
            )
        }

        val outletName = outlet?.name ?: nextRule.name
        val duration = nextRule.intervalOn.ifBlank {
            "--"
        }

        return "$outletName will run at ${nextRule.timeStart} for $duration."
    }

    private fun outlet1(): OutletViewHolder {
        return OutletViewHolder(
            card = binding.cardOutlet1,
            powerCard = binding.cardOutlet1Power,
            powerIcon = binding.ivOutlet1PowerIcon,
            name = binding.tvOutlet1Name,
            mode = binding.tvOutlet1Mode,
            schedule = binding.tvOutlet1Schedule
        )
    }

    private fun outlet2(): OutletViewHolder {
        return OutletViewHolder(
            card = binding.cardOutlet2,
            powerCard = binding.cardOutlet2Power,
            powerIcon = binding.ivOutlet2PowerIcon,
            name = binding.tvOutlet2Name,
            mode = binding.tvOutlet2Mode,
            schedule = binding.tvOutlet2Schedule
        )
    }

    private fun outlet3(): OutletViewHolder {
        return OutletViewHolder(
            card = binding.cardOutlet3,
            powerCard = binding.cardOutlet3Power,
            powerIcon = binding.ivOutlet3PowerIcon,
            name = binding.tvOutlet3Name,
            mode = binding.tvOutlet3Mode,
            schedule = binding.tvOutlet3Schedule
        )
    }

    private fun outlet4(): OutletViewHolder {
        return OutletViewHolder(
            card = binding.cardOutlet4,
            powerCard = binding.cardOutlet4Power,
            powerIcon = binding.ivOutlet4PowerIcon,
            name = binding.tvOutlet4Name,
            mode = binding.tvOutlet4Mode,
            schedule = binding.tvOutlet4Schedule
        )
    }

    private data class OutletViewHolder(
        val card: MaterialCardView,
        val powerCard: MaterialCardView,
        val powerIcon: ImageView,
        val name: TextView,
        val mode: TextView,
        val schedule: TextView
    )

    private fun roundedDrawable(
        color: Int,
        radiusDp: Int,
        context: Context
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(
                color
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