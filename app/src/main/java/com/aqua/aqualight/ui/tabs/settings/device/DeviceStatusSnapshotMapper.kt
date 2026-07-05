package com.aqua.aqualight.ui.tabs.settings.device

import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DevicePresencePresentationMapper
import java.util.Locale
import kotlin.math.max

data class DeviceSettingsDeviceOverviewUi(
    val activeDeviceCountText: String = "No active devices",
    val hasOnlineDevices: Boolean = false
)

object DeviceStatusSnapshotMapper {

    fun overview(
        snapshots: List<DeviceSnapshot>
    ): DeviceSettingsDeviceOverviewUi {
        val onlineCount = snapshots.count { snapshot ->
            snapshot.connectionState.onlineState.isOnlineForSettings()
        }

        val text = when {
            snapshots.isEmpty() -> "No active devices"
            onlineCount == 1 -> "1 Online Device"
            else -> "$onlineCount Online Devices"
        }

        return DeviceSettingsDeviceOverviewUi(
            activeDeviceCountText = text,
            hasOnlineDevices = onlineCount > 0
        )
    }

    fun items(
        snapshots: List<DeviceSnapshot>,
        nowMillis: Long
    ): List<DeviceStatusItem> {
        return snapshots
            .sortedWith(
                compareBy<DeviceSnapshot> { snapshot -> snapshot.title.lowercase(Locale.US) }
                    .thenBy { snapshot -> snapshot.deviceUid.value }
            )
            .map { snapshot ->
                snapshot.toStatusItem(nowMillis)
            }
    }

    private fun DeviceSnapshot.toStatusItem(
        nowMillis: Long
    ): DeviceStatusItem {
        val online = connectionState.onlineState.isOnlineForSettings()

        return DeviceStatusItem(
            displayName = title.ifBlank { "Device" },
            supportingText = supportingText(),
            ip = endpoint.ip.ifBlank { "Unknown" },
            deviceCode = deviceCode(),
            productName = productName(),
            lastSeenText = lastSeenText(nowMillis),
            isOnline = online
        )
    }

    private fun DeviceSnapshot.supportingText(): String {
        return listOf(
            product.familyRaw.ifBlank { product.family.wireValue }.ifBlank { null },
            DevicePresencePresentationMapper.availabilityLabel(connectionState.onlineState)
        )
            .filterNotNull()
            .joinToString(separator = " • ")
            .ifBlank { "Device" }
    }

    private fun DeviceSnapshot.deviceCode(): String {
        return identity.shortId
            .ifBlank { identity.serialNumber }
            .ifBlank { identity.firmwareSerial }
            .ifBlank { identity.uid.value }
            .ifBlank { "Unknown" }
    }

    private fun DeviceSnapshot.productName(): String {
        return product.displayName
            .ifBlank { product.model }
            .ifBlank { product.productId }
            .ifBlank { "Unknown" }
    }

    private fun DeviceSnapshot.lastSeenText(
        nowMillis: Long
    ): String {
        val lastSeen = latestSeenMillis()
        if (lastSeen <= 0L) {
            return "-"
        }

        val diffMillis = max(0L, nowMillis - lastSeen)
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

    private fun DeviceSnapshot.latestSeenMillis(): Long {
        return listOfNotNull(
            connectionState.lastAuthenticatedAtMillis,
            connectionState.lastWsConnectedAtMillis,
            connectionState.lastUdpSeenAtMillis,
            lastSeenAtMillis.takeIf { value -> value > 0L }
        ).maxOrNull() ?: 0L
    }

    private fun DeviceOnlineState.isOnlineForSettings(): Boolean =
        DevicePresencePresentationMapper.isReachable(this)
}
