package com.aqua.aqualight.ui.common.devicecard

import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

object DeviceCompactSnapshotMapper {

    fun map(
        snapshot: DeviceSnapshot,
        supportingText: String = buildDefaultSupportingText(snapshot),
        showAction: Boolean = false,
        actionText: String = ""
    ): DeviceCompactCardUi {
        return DeviceCompactCardUi(
            deviceUid = snapshot.deviceUid.value,
            displayName = snapshot.title.ifBlank { snapshot.deviceUid.value },
            serialText = serialText(snapshot),
            supportingText = supportingText,
            iconRes = DeviceFamilyIconMapper.iconFor(snapshot.product.family),
            statusText = statusText(snapshot.connectionState.onlineState),
            statusStyle = statusStyle(snapshot.connectionState.onlineState),
            actionText = actionText,
            showAction = showAction
        )
    }

    fun familyLabel(
        family: DeviceFamily
    ): String {
        return when (family) {
            DeviceFamily.LIGHT -> "Light"
            DeviceFamily.TIMER -> "Timer"
            DeviceFamily.DOSING -> "Dosing"
            DeviceFamily.COOLING -> "Cooling"
            DeviceFamily.UNKNOWN -> "Device"
        }
    }

    private fun serialText(
        snapshot: DeviceSnapshot
    ): String {
        return snapshot.identity.serialNumber
            .ifBlank { snapshot.identity.firmwareSerial }
            .ifBlank { snapshot.identity.shortId }
            .ifBlank { snapshot.deviceUid.value }
    }

    private fun buildDefaultSupportingText(
        snapshot: DeviceSnapshot
    ): String {
        val family = familyLabel(snapshot.product.family)

        val product = snapshot.product.displayName
            .ifBlank { snapshot.product.model }
            .ifBlank { snapshot.product.productId }

        return listOf(
            family,
            product
        )
            .filter { value -> value.isNotBlank() }
            .distinct()
            .joinToString(separator = " • ")
            .ifBlank { "AquaLight device" }
    }

    private fun statusText(
        onlineState: DeviceOnlineState
    ): String {
        return when (onlineState) {
            DeviceOnlineState.AUTHENTICATED,
            DeviceOnlineState.ONLINE_LAN -> "ONLINE"

            DeviceOnlineState.CONNECTING_WS -> "CONNECTING"
            DeviceOnlineState.DISCOVERING -> "DISCOVERING"
            DeviceOnlineState.STALE -> "STALE"
            DeviceOnlineState.AUTH_REQUIRED -> "AUTH"
            DeviceOnlineState.PROVISIONING -> "SETUP"
            DeviceOnlineState.OTA_UPDATING -> "UPDATING"
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE -> "NO LAN"
            DeviceOnlineState.OFFLINE -> "OFFLINE"
            DeviceOnlineState.ERROR -> "ERROR"
            DeviceOnlineState.UNKNOWN -> "UNKNOWN"
        }
    }

    private fun statusStyle(
        onlineState: DeviceOnlineState
    ): DeviceCompactStatusStyle {
        return when (onlineState) {
            DeviceOnlineState.AUTHENTICATED,
            DeviceOnlineState.ONLINE_LAN -> DeviceCompactStatusStyle.ONLINE

            DeviceOnlineState.CONNECTING_WS,
            DeviceOnlineState.DISCOVERING -> DeviceCompactStatusStyle.CONNECTING

            DeviceOnlineState.STALE,
            DeviceOnlineState.AUTH_REQUIRED,
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING,
            DeviceOnlineState.UNKNOWN -> DeviceCompactStatusStyle.WARNING

            DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.ERROR -> DeviceCompactStatusStyle.OFFLINE
        }
    }
}
