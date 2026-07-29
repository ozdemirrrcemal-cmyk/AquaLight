package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.application.devices.OwnerDeviceStatusSnapshot
import com.aqua.aqualight.application.devices.TankDeviceListItem
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

internal fun DeviceSnapshot.toOwnerDeviceListItem(
    assignedTankName: String = ""
): OwnerDeviceListItem {
    return OwnerDeviceListItem(
        deviceUid = deviceUid.value,
        displayName = title.ifBlank { deviceUid.value },
        serialText = serialText(),
        family = product.family.toOwnerDeviceFamily(),
        availability = connectionState.onlineState.toOwnerDeviceAvailability(),
        assignedTankName = assignedTankName.trim()
    )
}

internal fun DeviceSnapshot.toOwnerDeviceStatusSnapshot(): OwnerDeviceStatusSnapshot {
    return OwnerDeviceStatusSnapshot(
        deviceUid = deviceUid.value,
        displayName = title.ifBlank { deviceUid.value },
        serialText = serialText(),
        family = product.family.toOwnerDeviceFamily(),
        availability = connectionState.onlineState.toOwnerDeviceAvailability(),
        ipAddress = endpoint.ip.trim(),
        lastSeenAtMillis = latestSeenAtMillis()
    )
}

internal fun DeviceSnapshot.toTankDeviceListItem(): TankDeviceListItem {
    return TankDeviceListItem(
        deviceUid = deviceUid.value,
        displayName = title.ifBlank { deviceUid.value },
        serialText = serialText(),
        family = product.family.toOwnerDeviceFamily(),
        availability = connectionState.onlineState.toOwnerDeviceAvailability()
    )
}

internal fun DeviceFamily.toOwnerDeviceFamily(): OwnerDeviceFamily {
    return when (this) {
        DeviceFamily.LIGHT -> OwnerDeviceFamily.LIGHT
        DeviceFamily.TIMER -> OwnerDeviceFamily.TIMER
        DeviceFamily.DOSING -> OwnerDeviceFamily.DOSING
        DeviceFamily.COOLING -> OwnerDeviceFamily.COOLING
        DeviceFamily.UNKNOWN -> OwnerDeviceFamily.UNKNOWN
    }
}

internal fun DeviceOnlineState.toOwnerDeviceAvailability(): OwnerDeviceAvailability {
    return when (this) {
        DeviceOnlineState.AUTHENTICATED,
        DeviceOnlineState.PROVISIONING,
        DeviceOnlineState.OTA_UPDATING -> OwnerDeviceAvailability.REACHABLE

        DeviceOnlineState.ONLINE_LAN,
        DeviceOnlineState.CONNECTING_WS,
        DeviceOnlineState.UNKNOWN,
        DeviceOnlineState.DISCOVERING,
        DeviceOnlineState.STALE,
        DeviceOnlineState.OFFLINE,
        DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
        DeviceOnlineState.AUTH_REQUIRED,
        DeviceOnlineState.ERROR -> OwnerDeviceAvailability.UNREACHABLE
    }
}

private fun DeviceSnapshot.serialText(): String {
    return identity.serialNumber
        .ifBlank { identity.firmwareSerial }
        .ifBlank { identity.shortId }
        .ifBlank { deviceUid.value }
}

private fun DeviceSnapshot.latestSeenAtMillis(): Long {
    return listOfNotNull(
        connectionState.lastControlProofAtMillis,
        connectionState.lastRuntimeMessageAtMillis,
        connectionState.lastAuthenticatedAtMillis,
        connectionState.lastWsConnectedAtMillis,
        connectionState.lastUdpSeenAtMillis,
        lastSeenAtMillis.takeIf { value -> value > 0L }
    ).maxOrNull() ?: 0L
}
