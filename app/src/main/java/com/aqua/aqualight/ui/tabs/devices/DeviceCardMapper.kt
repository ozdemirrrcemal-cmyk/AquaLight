package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.ui.common.devicecard.DeviceFamilyIconMapper
import java.util.Locale
import java.util.concurrent.TimeUnit

object DeviceCardMapper {

    fun map(
        snapshot: DeviceSnapshot,
        nowMillis: Long = System.currentTimeMillis()
    ): DeviceCardUi {
        val onlineState = snapshot.connectionState.onlineState
        val status = statusFor(onlineState)

        return DeviceCardUi(
            deviceUid = snapshot.deviceUid.value,
            title = snapshot.title.ifBlank { snapshot.deviceUid.value },
            subtitle = buildSubtitle(snapshot),
            statusLabel = status.label,
            statusStyle = status.style,
            ipText = snapshot.endpoint.ip.ifBlank { "Unknown" },
            serialText = snapshot.identity.serialNumber
                .ifBlank { snapshot.identity.firmwareSerial }
                .ifBlank { snapshot.identity.shortId }
                .ifBlank { snapshot.deviceUid.value },
            firmwareText = snapshot.firmwareVersion.ifBlank { "Unknown" },
            lastSeenText = lastSeenText(snapshot.lastSeenAtMillis, nowMillis),
            productText = snapshot.product.displayName
                .ifBlank { snapshot.product.model }
                .ifBlank { snapshot.product.productKey }
                .ifBlank { snapshot.product.family.wireValue },
            onlineState = onlineState,
            iconRes = DeviceFamilyIconMapper.iconFor(snapshot.product.family)
        )
    }

    private fun buildSubtitle(snapshot: DeviceSnapshot): String {
        val family = familyLabel(snapshot.product.family)
        val capabilities = capabilitySummary(snapshot.capabilities, snapshot)
        return listOf(family, capabilities)
            .filter { it.isNotBlank() }
            .joinToString(separator = " • ")
            .ifBlank { snapshot.product.displayName.ifBlank { "AquaLight device" } }
    }

    private fun familyLabel(family: DeviceFamily): String = when (family) {
        DeviceFamily.LIGHT -> "Light"
        DeviceFamily.TIMER -> "Timer"
        DeviceFamily.DOSING -> "Dosing"
        DeviceFamily.COOLING -> "Cooling"
        DeviceFamily.UNKNOWN -> "Device"
    }

    private fun capabilitySummary(
        capabilities: DeviceCapabilities,
        snapshot: DeviceSnapshot
    ): String {
        val parts = mutableListOf<String>()

        if (capabilities.light) {
            val channelText = if (snapshot.limits.lightChannelCount > 0) {
                "${snapshot.limits.lightChannelCount}CH"
            } else {
                "Light"
            }
            parts += channelText
        }
        if (capabilities.dosing) {
            val dosingText = if (snapshot.limits.dosingChannelCount > 0) {
                "${snapshot.limits.dosingChannelCount} dosing"
            } else {
                "Dosing"
            }
            parts += dosingText
        }
        if (capabilities.standaloneTimer) {
            val timerText = if (snapshot.limits.timerChannelCount > 0) {
                "${snapshot.limits.timerChannelCount} timer"
            } else {
                "Timer"
            }
            parts += timerText
        }
        if (capabilities.cooling || capabilities.fan) {
            val fanText = if (snapshot.limits.fanOutputCount > 0) {
                "${snapshot.limits.fanOutputCount} fan"
            } else {
                "Cooling"
            }
            parts += fanText
        }
        if (capabilities.temperature) parts += "Temp"
        if (capabilities.ota) parts += "OTA"

        return parts.distinct().take(4).joinToString(separator = " / ")
    }

    private fun statusFor(onlineState: DeviceOnlineState): StatusPresentation = when (onlineState) {
        DeviceOnlineState.AUTHENTICATED -> StatusPresentation("ONLINE", DeviceCardUi.StatusStyle.ONLINE)
        DeviceOnlineState.ONLINE_LAN -> StatusPresentation("ONLINE", DeviceCardUi.StatusStyle.ONLINE)
        DeviceOnlineState.CONNECTING_WS -> StatusPresentation("CONNECTING", DeviceCardUi.StatusStyle.CONNECTING)
        DeviceOnlineState.DISCOVERING -> StatusPresentation("DISCOVERING", DeviceCardUi.StatusStyle.CONNECTING)
        DeviceOnlineState.STALE -> StatusPresentation("STALE", DeviceCardUi.StatusStyle.WARNING)
        DeviceOnlineState.AUTH_REQUIRED -> StatusPresentation("AUTH", DeviceCardUi.StatusStyle.WARNING)
        DeviceOnlineState.PROVISIONING -> StatusPresentation("SETUP", DeviceCardUi.StatusStyle.WARNING)
        DeviceOnlineState.OTA_UPDATING -> StatusPresentation("UPDATING", DeviceCardUi.StatusStyle.WARNING)
        DeviceOnlineState.LOCAL_NETWORK_OFFLINE -> StatusPresentation("NO LAN", DeviceCardUi.StatusStyle.OFFLINE)
        DeviceOnlineState.OFFLINE -> StatusPresentation("OFFLINE", DeviceCardUi.StatusStyle.OFFLINE)
        DeviceOnlineState.ERROR -> StatusPresentation("ERROR", DeviceCardUi.StatusStyle.OFFLINE)
        DeviceOnlineState.UNKNOWN -> StatusPresentation("UNKNOWN", DeviceCardUi.StatusStyle.WARNING)
    }

    private fun lastSeenText(lastSeenAtMillis: Long, nowMillis: Long): String {
        if (lastSeenAtMillis <= 0L) return "Never"

        val ageMillis = (nowMillis - lastSeenAtMillis).coerceAtLeast(0L)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ageMillis)
        if (seconds < 5L) return "Just now"
        if (seconds < 60L) return "${seconds}s ago"

        val minutes = TimeUnit.MILLISECONDS.toMinutes(ageMillis)
        if (minutes < 60L) return "${minutes}m ago"

        val hours = TimeUnit.MILLISECONDS.toHours(ageMillis)
        if (hours < 24L) return "${hours}h ago"

        val days = TimeUnit.MILLISECONDS.toDays(ageMillis)
        return String.format(Locale.US, "%dd ago", days)
    }

    private data class StatusPresentation(
        val label: String,
        val style: DeviceCardUi.StatusStyle
    )
}
