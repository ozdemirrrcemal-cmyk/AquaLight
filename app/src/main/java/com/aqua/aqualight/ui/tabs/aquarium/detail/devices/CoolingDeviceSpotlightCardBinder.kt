package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.content.Context
import android.content.res.ColorStateList
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardProgramSlot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardSummary
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import com.aqua.aqualight.databinding.ItemCoolingDeviceSpotlightCardBinding
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingFanIndicator
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

object CoolingDeviceSpotlightCardBinder {

    fun bind(
        binding: ItemCoolingDeviceSpotlightCardBinding,
        item: CoolingDeviceSpotlightCardUi
    ) {
        val context = binding.root.context
        val online = item.header.statusStyle == DeviceConnectionVisualState.ONLINE
        val displayName = item.header.displayName.trim().ifBlank {
            context.getString(R.string.device_menu_default_title)
        }
        bindHeader(binding, item.header, displayName)
        bindSummary(binding, item, online)
        binding.root.contentDescription = context.getString(
            R.string.cooling_device_card_accessibility,
            displayName,
            item.summary?.let { summary -> summary.accessibilitySummary(context) }
                ?: context.getString(
                    if (online) R.string.cooling_device_card_loading
                    else R.string.cooling_device_card_offline
                )
        )
    }

    private fun bindHeader(
        binding: ItemCoolingDeviceSpotlightCardBinding,
        header: CoolingDeviceSpotlightHeaderUi,
        displayName: String
    ) {
        val context = binding.root.context
        binding.tvDeviceName.text = displayName
        binding.ivDeviceIcon.setImageResource(header.iconRes)
        binding.ivDeviceIcon.imageTintList = null
        binding.ivDeviceIcon.clearColorFilter()
        binding.ivDeviceIcon.contentDescription = displayName
        binding.ivPresenceIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, header.statusStyle.tintColorRes)
        )
        binding.ivPresenceIcon.contentDescription =
            context.getString(header.statusStyle.accessibilityLabelRes)
        binding.ivPresenceIcon.isVisible = !header.isBusy
        binding.progressCardAction.isVisible = header.isBusy
        binding.root.isEnabled = !header.isBusy
    }

    private fun bindSummary(
        binding: ItemCoolingDeviceSpotlightCardBinding,
        item: CoolingDeviceSpotlightCardUi,
        online: Boolean
    ) {
        val context = binding.root.context
        val summary = item.summary
        binding.coolingDetails.isVisible = summary != null
        binding.tvCoolingUnavailable.isVisible = summary == null
        if (summary == null) {
            binding.tvCoolingUnavailable.text = context.getString(
                when {
                    !online -> R.string.cooling_device_card_offline_detail
                    item.contentState == CoolingDeviceSpotlightContentState.UNAVAILABLE ->
                        R.string.cooling_device_card_unavailable_detail
                    else -> R.string.cooling_device_card_loading_detail
                }
            )
            return
        }

        binding.tvCoolingMode.setText(summary.mode.labelRes())
        binding.tvCoolingActivity.text = summary.detailText(context)
        binding.waterMetric.metricLabel.setText(R.string.cooling_device_card_water)
        binding.waterMetric.metricValue.text = summary.waterTemperatureC.temperatureText(context)
        binding.fanMetric.metricLabel.setText(R.string.cooling_device_card_fan)
        binding.fanMetric.metricValue.text = summary.actualFanPercent.percentText(context)
        bindModeMetric(binding, summary, context)
        binding.coolingFan.setContent {
            AquaCoolingFanIndicator(
                appliedPercent = summary.actualFanPercent,
                motionActive = summary.fanMotionActive,
                colors = aquaCoolingDashboardColors(),
                modifier = Modifier.size(AquaCoolingDashboardGeometry.deviceCardFanSize)
            )
        }
    }

    private fun bindModeMetric(
        binding: ItemCoolingDeviceSpotlightCardBinding,
        summary: DeviceCoolingCardSummary,
        context: Context
    ) {
        when (summary.mode) {
            DeviceCoolingControlMode.AUTOMATIC -> {
                binding.thirdMetric.metricLabel.setText(R.string.cooling_device_card_range)
                binding.thirdMetric.metricValue.text = summary.automaticRange?.let { range ->
                    context.getString(
                        R.string.device_cooling_temperature_range_value_format,
                        range.startC,
                        range.fullSpeedC
                    )
                } ?: context.getString(R.string.device_cooling_value_unavailable)
            }
            DeviceCoolingControlMode.PROGRAM -> {
                binding.thirdMetric.metricLabel.setText(R.string.cooling_device_card_slot)
                val program = summary.program
                binding.thirdMetric.metricValue.text = if (
                    program?.activeSlotNumber != null && program.slotCount > 0
                ) {
                    context.getString(
                        R.string.cooling_device_card_slot_value,
                        program.activeSlotNumber,
                        program.slotCount
                    )
                } else {
                    context.getString(R.string.device_cooling_value_unavailable)
                }
            }
            DeviceCoolingControlMode.MANUAL -> {
                binding.thirdMetric.metricLabel.setText(R.string.cooling_device_card_target)
                binding.thirdMetric.metricValue.text = summary.targetFanPercent.percentText(context)
            }
        }
    }
}

private fun DeviceCoolingControlMode.labelRes(): Int = when (this) {
    DeviceCoolingControlMode.AUTOMATIC -> R.string.device_cooling_mode_automatic
    DeviceCoolingControlMode.MANUAL -> R.string.device_cooling_mode_manual
    DeviceCoolingControlMode.PROGRAM -> R.string.device_cooling_mode_program
}

private fun DeviceCoolingCardSummary.detailText(context: Context): String {
    val activity = context.getString(
        when {
            mode != DeviceCoolingControlMode.MANUAL && fanMotionActive ->
                R.string.device_cooling_system_status_operating_cooling
            else -> when (operatingState) {
            DeviceCoolingOperatingState.IDLE -> R.string.device_cooling_system_status_operating_idle
            DeviceCoolingOperatingState.COOLING ->
                R.string.device_cooling_system_status_operating_cooling
            DeviceCoolingOperatingState.MANUAL ->
                R.string.device_cooling_system_status_operating_manual
            DeviceCoolingOperatingState.PROGRAM ->
                R.string.device_cooling_system_status_operating_program
            DeviceCoolingOperatingState.FAULT ->
                R.string.device_cooling_system_status_operating_fault
            }
        }
    )
    val interval = if (mode == DeviceCoolingControlMode.PROGRAM) {
        program?.activeSlot?.timeText(context)
    } else {
        null
    }
    return interval?.let { value ->
        context.getString(R.string.cooling_device_card_detail_with_program, activity, value)
    } ?: activity
}

private fun DeviceCoolingCardProgramSlot.timeText(context: Context): String = context.getString(
    R.string.cooling_device_card_program_interval,
    startMinutes / MINUTES_PER_HOUR,
    startMinutes % MINUTES_PER_HOUR,
    endMinutes / MINUTES_PER_HOUR,
    endMinutes % MINUTES_PER_HOUR
)

private fun Double?.temperatureText(context: Context): String = this?.let { value ->
    context.getString(R.string.device_cooling_temperature_value_format, value)
} ?: context.getString(R.string.device_cooling_value_unavailable)

private fun Double?.percentText(context: Context): String = this?.let { value ->
    context.getString(R.string.cooling_device_card_percent_value, value)
} ?: context.getString(R.string.device_cooling_value_unavailable)

private fun DeviceCoolingCardSummary.accessibilitySummary(context: Context): String =
    context.getString(
        R.string.cooling_device_card_accessibility_summary,
        context.getString(mode.labelRes()),
        detailText(context),
        waterTemperatureC.temperatureText(context),
        actualFanPercent.percentText(context)
    )

private const val MINUTES_PER_HOUR = 60
