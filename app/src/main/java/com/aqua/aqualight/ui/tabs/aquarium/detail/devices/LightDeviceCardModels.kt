package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.card.DeviceLightCardState
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

data class LightDeviceSpotlightHeaderUi(
    val displayName: String,
    @DrawableRes val iconRes: Int,
    val statusStyle: DeviceConnectionVisualState,
    val isBusy: Boolean
)

enum class LightDeviceSpotlightContentState {
    PREPARING,
    READY,
    UNAVAILABLE
}

data class LightDeviceSpotlightCardUi(
    val header: LightDeviceSpotlightHeaderUi,
    val contentState: LightDeviceSpotlightContentState,
    val snapshot: DeviceLightControlSnapshot?,
    val sunriseTimeMs: Long?,
    val sunsetTimeMs: Long?
)

internal fun DeviceCompactCardUi.toLightSpotlightCardUi(
    state: DeviceLightCardState?
): LightDeviceSpotlightCardUi {
    val snapshot = (state as? DeviceLightCardState.Ready)?.snapshot
    val window = snapshot?.plan?.outerActiveWindow(snapshot.hero.mode)
    val contentState = when (state) {
        is DeviceLightCardState.Ready -> LightDeviceSpotlightContentState.READY
        is DeviceLightCardState.Unavailable -> LightDeviceSpotlightContentState.UNAVAILABLE
        DeviceLightCardState.Preparing -> LightDeviceSpotlightContentState.PREPARING
        null -> if (statusStyle == DeviceConnectionVisualState.ONLINE) {
            LightDeviceSpotlightContentState.PREPARING
        } else {
            LightDeviceSpotlightContentState.UNAVAILABLE
        }
    }
    return LightDeviceSpotlightCardUi(
        header = LightDeviceSpotlightHeaderUi(
            displayName = displayName,
            iconRes = iconRes,
            statusStyle = statusStyle,
            isBusy = isBusy
        ),
        contentState = contentState,
        snapshot = snapshot,
        sunriseTimeMs = window?.startTimeMs,
        sunsetTimeMs = window?.endTimeMs
    )
}

internal fun lightDeviceSpotlightAccessibilityDescription(
    context: Context,
    item: LightDeviceSpotlightCardUi
): String {
    val status = context.getString(item.header.statusStyle.accessibilityLabelRes)
    val mode = context.getString(item.snapshot?.hero?.mode.lightCardModeLabelRes())
    val sunrise = item.sunriseTimeMs.lightCardTimeText(context)
    val sunset = item.sunsetTimeMs.lightCardTimeText(context)
    val channels = item.snapshot?.channels
        ?.joinToString { channel ->
            val label = channel.lightCardLabelRes()
                ?.let(context::getString)
                ?: channel.displayName
            label + " " + channel.effectivePercent + "%"
        }
        ?.takeIf(String::isNotBlank)
        ?: context.getString(R.string.device_light_card_unavailable)
    return context.getString(
        R.string.device_light_card_accessibility,
        item.header.displayName,
        status,
        mode,
        sunrise,
        sunset,
        channels
    )
}

@StringRes
internal fun DeviceLightControlMode?.lightCardModeLabelRes(): Int = when (this) {
    DeviceLightControlMode.MANUAL -> R.string.device_light_mode_selector_manual
    DeviceLightControlMode.AUTOMATIC -> R.string.device_light_mode_selector_automatic
    DeviceLightControlMode.CUSTOM -> R.string.device_light_mode_selector_custom
    null -> R.string.device_light_hero_mode_unavailable
}

internal fun DeviceLightControlMode?.lightCardModeGlyph(): String = when (this) {
    DeviceLightControlMode.MANUAL -> "M"
    DeviceLightControlMode.AUTOMATIC -> "A"
    DeviceLightControlMode.CUSTOM -> "C"
    null -> "—"
}

@StringRes
internal fun DeviceLightChannelOutputSnapshot.lightCardLabelRes(): Int? = when (key) {
    "red" -> R.string.device_light_live_output_red
    "green" -> R.string.device_light_live_output_green
    "blue" -> R.string.device_light_live_output_blue
    "white" -> R.string.device_light_live_output_white
    else -> null
}

private data class LightDeviceScheduleWindow(
    val startTimeMs: Long,
    val endTimeMs: Long
)

private fun DeviceLightPlanSnapshot.outerActiveWindow(
    mode: DeviceLightControlMode?
): LightDeviceScheduleWindow? {
    val eligible = mode != null &&
        mode != DeviceLightControlMode.MANUAL &&
        available &&
        reason == DeviceLightPlanReason.OK &&
        hasScheduleToday
    val activeIndices = if (eligible) {
        points.indices.filter { index ->
            points[index].channelLevels.any { level -> level > 0 }
        }
    } else {
        emptyList()
    }
    return activeIndices.firstOrNull()?.let { firstActive ->
        val lastActive = activeIndices.last()
        val startIndex = (firstActive - 1).coerceAtLeast(0)
        val endIndex = (lastActive + 1).coerceAtMost(points.lastIndex)
        LightDeviceScheduleWindow(
            startTimeMs = points[startIndex].timeMs,
            endTimeMs = points[endIndex].timeMs
        )
    }
}

private fun Long?.lightCardTimeText(context: Context): String =
    this?.let { value ->
        val totalMinutes = value / MILLIS_PER_MINUTE
        context.getString(
            R.string.device_light_auto_time_format,
            (totalMinutes / MINUTES_PER_HOUR).toInt(),
            (totalMinutes % MINUTES_PER_HOUR).toInt()
        )
    } ?: context.getString(R.string.device_light_auto_editor_time_placeholder)

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
