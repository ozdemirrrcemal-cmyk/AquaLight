package com.aqua.aqualight.ui.tabs.settings.device

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.OwnerDeviceStatusSnapshot
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactVisualKind
import com.aqua.aqualight.ui.common.devicecard.DeviceFamilyIconMapper
import com.aqua.aqualight.ui.common.text.AquaUiText
import java.util.Locale
import kotlin.math.max

data class DeviceSettingsDeviceOverviewUi(
    val activeDeviceCountText: AquaUiText = AquaUiText.Resource(
        R.string.settings_no_active_devices_summary
    ),
    val hasOnlineDevices: Boolean = false
)

object DeviceStatusSnapshotMapper {

    fun overview(
        statuses: List<OwnerDeviceStatusSnapshot>
    ): DeviceSettingsDeviceOverviewUi {
        val onlineCount = statuses.count { status ->
            status.availability == OwnerDeviceAvailability.REACHABLE
        }

        val text = when {
            statuses.isEmpty() -> AquaUiText.Resource(
                R.string.settings_no_active_devices_summary
            )
            else -> AquaUiText.Plural(
                R.plurals.settings_online_devices_count,
                onlineCount
            )
        }

        return DeviceSettingsDeviceOverviewUi(
            activeDeviceCountText = text,
            hasOnlineDevices = onlineCount > 0
        )
    }

    fun items(
        statuses: List<OwnerDeviceStatusSnapshot>,
        nowMillis: Long
    ): List<DeviceStatusItem> {
        return statuses
            .sortedWith(
                compareBy<OwnerDeviceStatusSnapshot> { status ->
                    status.displayName.lowercase(Locale.US)
                }.thenBy { status -> status.deviceUid }
            )
            .map { status -> status.toStatusItem(nowMillis) }
    }

    private fun OwnerDeviceStatusSnapshot.toStatusItem(
        nowMillis: Long
    ): DeviceStatusItem {
        return DeviceStatusItem(
            displayName = displayName,
            iconRes = DeviceFamilyIconMapper.iconFor(family),
            visualKind = if (family == OwnerDeviceFamily.DOSING) {
                DeviceCompactVisualKind.DOSING_IDENTITY
            } else {
                DeviceCompactVisualKind.ICON
            },
            ip = ipAddress,
            serialText = serialText.ifBlank { deviceUid },
            lastSeenText = lastSeenText(nowMillis),
            isOnline = availability == OwnerDeviceAvailability.REACHABLE
        )
    }

    private fun OwnerDeviceStatusSnapshot.lastSeenText(
        nowMillis: Long
    ): AquaUiText {
        if (lastSeenAtMillis <= 0L) {
            return AquaUiText.Resource(R.string.common_not_available_symbol)
        }

        val diffMillis = max(0L, nowMillis - lastSeenAtMillis)
        val seconds = diffMillis / 1_000L
        val minutes = seconds / 60L
        val hours = minutes / 60L
        val days = hours / 24L

        return when {
            seconds < 15L -> AquaUiText.Resource(R.string.device_status_last_seen_just_now)
            seconds < 60L -> AquaUiText.Plural(
                R.plurals.device_status_last_seen_seconds_ago,
                seconds.toInt()
            )
            minutes < 60L -> AquaUiText.Plural(
                R.plurals.device_status_last_seen_minutes_ago,
                minutes.toInt()
            )
            hours < 24L -> AquaUiText.Plural(
                R.plurals.device_status_last_seen_hours_ago,
                hours.toInt()
            )
            else -> AquaUiText.Plural(
                R.plurals.device_status_last_seen_days_ago,
                days.toInt()
            )
        }
    }
}
