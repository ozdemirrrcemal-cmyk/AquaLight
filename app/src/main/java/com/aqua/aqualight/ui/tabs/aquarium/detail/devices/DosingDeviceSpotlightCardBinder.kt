package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardNextDose
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardReservoirState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardReservoirSummary
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardSummary
import com.aqua.aqualight.databinding.ItemDosingDeviceSpotlightCardBinding
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactStatusStyle
import java.util.Locale

object DosingDeviceSpotlightCardBinder {

    fun bind(
        binding: ItemDosingDeviceSpotlightCardBinding,
        item: DosingDeviceSpotlightCardUi
    ) {
        val context = binding.root.context
        val header = item.header
        val online = header.statusStyle == DeviceCompactStatusStyle.ONLINE
        val displayName = header.displayName.trim().ifBlank {
            context.getString(R.string.device_menu_default_title)
        }

        bindHeader(binding, header, online, displayName)
        bindSummary(binding, item.summary, online)
        bindSpotlight(binding, item, online)
        bindAccessibility(binding, item.summary, displayName, item.selectedChannel?.title.orEmpty())
    }

    private fun bindHeader(
        binding: ItemDosingDeviceSpotlightCardBinding,
        header: DosingDeviceSpotlightHeaderUi,
        online: Boolean,
        displayName: String
    ) {
        val context = binding.root.context
        binding.tvDeviceName.text = displayName
        binding.ivDeviceIcon.setImageResource(header.iconRes)
        binding.ivDeviceIcon.imageTintList = null
        binding.ivDeviceIcon.clearColorFilter()
        binding.ivDeviceIcon.contentDescription = displayName
        binding.ivPresenceIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                if (online) R.color.aqua_accent_positive
                else R.color.aqua_device_compact_card_binder_color
            )
        )
        binding.ivPresenceIcon.contentDescription = context.getString(
            if (online) R.string.device_online else R.string.device_offline
        )
        binding.ivPresenceIcon.isVisible = !header.isBusy
        binding.progressCardAction.isVisible = header.isBusy
        binding.root.isEnabled = !header.isBusy
    }

    private fun bindSummary(
        binding: ItemDosingDeviceSpotlightCardBinding,
        summary: DeviceDosingCardSummary?,
        online: Boolean
    ) {
        val context = binding.root.context
        binding.tvDosingChannelSummary.text = summary?.let { value ->
            context.getString(
                R.string.dosing_device_card_channel_summary,
                value.channelCount,
                value.activeChannelCount
            )
        } ?: context.getString(
            if (online) R.string.dosing_device_card_loading
            else R.string.dosing_device_card_offline
        )
    }

    private fun bindSpotlight(
        binding: ItemDosingDeviceSpotlightCardBinding,
        item: DosingDeviceSpotlightCardUi,
        online: Boolean
    ) {
        val context = binding.root.context
        val channel = item.selectedChannel
        binding.spotlightDetails.isVisible = channel != null
        binding.tvDosingUnavailable.isVisible = channel == null
        if (channel == null) {
            binding.tvDosingUnavailable.text = context.getString(
                if (online) R.string.dosing_device_card_loading_detail
                else R.string.dosing_device_card_offline_detail
            )
            binding.channelIndicator.removeAllViews()
            return
        }

        val locale = binding.root.resources.configuration.locales[0]
        binding.tvChannelBadge.text = channel.channelNumber.toString()
        binding.tvSpotlightChannelName.text = channel.title
        bindRuntimeState(binding, channel.runtimeEnabled)
        bindDailyDose(binding, channel.dailyDoseMicroliters, locale)
        bindNextDose(binding, channel.nextDose, locale)
        bindReservoir(binding, channel.reservoir, locale)
        bindIndicators(binding.channelIndicator, item.pageCount, item.selectedIndex)
    }

    private fun bindRuntimeState(
        binding: ItemDosingDeviceSpotlightCardBinding,
        runtimeEnabled: Boolean
    ) {
        val context = binding.root.context
        binding.tvSpotlightState.text = context.getString(
            if (runtimeEnabled) R.string.dosing_device_card_active
            else R.string.dosing_device_card_inactive
        )
        binding.tvSpotlightState.setTextColor(
            ContextCompat.getColor(
                context,
                if (runtimeEnabled) R.color.aqua_accent_positive
                else R.color.aqua_content_muted
            )
        )
    }

    private fun bindDailyDose(
        binding: ItemDosingDeviceSpotlightCardBinding,
        dailyDoseMicroliters: Long?,
        locale: Locale
    ) {
        val context = binding.root.context
        binding.tvDailyDoseValue.text = dailyDoseMicroliters?.let { microliters ->
            context.getString(
                R.string.dosing_device_card_ml_value,
                DosingDeviceCardFormatter.milliliters(microliters, locale, fractionDigits = 2)
            )
        } ?: context.getString(R.string.dosing_device_card_metric_empty)
        binding.tvDailyDoseSub.text = context.getString(
            if (dailyDoseMicroliters == null) R.string.dosing_device_card_program_off
            else R.string.dosing_device_card_daily_plan
        )
    }

    private fun bindNextDose(
        binding: ItemDosingDeviceSpotlightCardBinding,
        nextDose: DeviceDosingCardNextDose?,
        locale: Locale
    ) {
        val context = binding.root.context
        binding.tvNextDoseValue.text = nextDose?.let { dose ->
            DosingDeviceCardFormatter.time(dose.timeMillis, locale)
        } ?: context.getString(R.string.dosing_device_card_metric_empty)
        binding.tvNextDoseSub.text = nextDose?.let { dose ->
            context.getString(
                R.string.dosing_device_card_ml_value,
                DosingDeviceCardFormatter.milliliters(
                    dose.amountMicroliters,
                    locale,
                    fractionDigits = 2
                )
            )
        } ?: context.getString(R.string.dosing_device_card_next_unavailable)
    }

    private fun bindReservoir(
        binding: ItemDosingDeviceSpotlightCardBinding,
        reservoir: DeviceDosingCardReservoirSummary?,
        locale: Locale
    ) {
        val context = binding.root.context
        binding.tvReservoirValue.text = reservoir?.let { value ->
            context.getString(
                R.string.dosing_device_card_ml_value,
                DosingDeviceCardFormatter.milliliters(
                    value.remainingMicroliters,
                    locale,
                    fractionDigits = 1
                )
            )
        } ?: context.getString(R.string.dosing_device_card_metric_empty)
        binding.tvReservoirSub.text = when (reservoir?.state) {
            null -> context.getString(R.string.dosing_device_card_reservoir_tracking_off)
            DeviceDosingCardReservoirState.LOW ->
                context.getString(R.string.dosing_device_card_reservoir_low)
            DeviceDosingCardReservoirState.ESTIMATED ->
                context.getString(
                    R.string.dosing_device_card_remaining_days,
                    requireNotNull(reservoir.estimatedRemainingDays)
                )
            DeviceDosingCardReservoirState.UNCERTAIN ->
                context.getString(R.string.dosing_device_card_reservoir_uncertain)
            DeviceDosingCardReservoirState.ESTIMATE_UNAVAILABLE ->
                context.getString(R.string.dosing_device_card_reservoir_estimate_unavailable)
        }
        binding.tvReservoirSub.setTextColor(
            ContextCompat.getColor(
                context,
                if (reservoir?.state == DeviceDosingCardReservoirState.LOW) {
                    R.color.aqua_content_warning
                } else {
                    R.color.aqua_content_muted
                }
            )
        )
    }

    private fun bindIndicators(
        container: LinearLayout,
        pageCount: Int,
        selectedIndex: Int
    ) {
        val context = container.context
        container.removeAllViews()
        if (pageCount <= 1) return

        val dotSize = context.resources.getDimensionPixelSize(R.dimen.aqua_size_6)
        val dotSpacing = context.resources.getDimensionPixelSize(R.dimen.aqua_size_4)
        val selectedColor = ContextCompat.getColor(context, R.color.aqua_accent_positive)
        val idleColor = ContextCompat.getColor(context, R.color.aqua_content_muted)
        repeat(pageCount) { index ->
            container.addView(
                View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (index == selectedIndex) selectedColor else idleColor)
                    }
                    layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                        marginStart = if (index == 0) 0 else dotSpacing
                    }
                }
            )
        }
    }

    private fun bindAccessibility(
        binding: ItemDosingDeviceSpotlightCardBinding,
        summary: DeviceDosingCardSummary?,
        displayName: String,
        channelTitle: String
    ) {
        val context = binding.root.context
        val summaryText = summary?.let { value ->
            context.getString(
                R.string.dosing_device_card_channel_summary,
                value.channelCount,
                value.activeChannelCount
            )
        } ?: binding.tvDosingChannelSummary.text.toString()
        binding.root.contentDescription = context.getString(
            R.string.dosing_device_card_accessibility,
            displayName,
            summaryText,
            channelTitle
        )
    }
}
