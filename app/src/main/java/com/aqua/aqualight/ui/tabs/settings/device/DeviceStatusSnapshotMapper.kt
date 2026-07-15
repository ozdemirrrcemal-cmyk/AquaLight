package com.aqua.aqualight.ui.tabs.settings.device

import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceStatusSnapshot
import com.aqua.aqualight.ui.common.devicecard.DeviceFamilyIconMapper
import java.util.Locale
import kotlin.math.max

data class DeviceSettingsDeviceOverviewUi(
    val activeDeviceCountText: String = "No active devices",
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
            statuses.isEmpty() -> "No active devices"
            onlineCount == 1 -> "1 Online Device"
            else -> "$onlineCount Online Devices"
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
            displayName = displayName.ifBlank { "Device" },
            iconRes = DeviceFamilyIconMapper.iconFor(family),
            ip = ipAddress.ifBlank { "Unknown" },
            serialText = serialText.ifBlank { deviceUid.ifBlank { "Unknown" } },
            lastSeenText = lastSeenText(nowMillis),
            isOnline = availability == OwnerDeviceAvailability.REACHABLE
        )
    }

    private fun OwnerDeviceStatusSnapshot.lastSeenText(
        nowMillis: Long
    ): String {
        if (lastSeenAtMillis <= 0L) {
            return "-"
        }

        val diffMillis = max(0L, nowMillis - lastSeenAtMillis)
        val seconds = diffMillis / 1_000L
        val minutes = seconds / 60L
        val hours = minutes / 60L
        val days = hours / 24L

        return when {
            seconds < 15L -> "Just now"
            seconds < 60L -> "${seconds}s ago"
            minutes < 60L -> "${minutes}m ago"
            hours < 24L -> "${hours}h ago"
            else -> "${days}d ago"
        }
    }
}
