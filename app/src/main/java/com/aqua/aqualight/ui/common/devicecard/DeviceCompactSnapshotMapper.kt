package com.aqua.aqualight.ui.common.devicecard

import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DevicePresencePresentationMapper
import java.util.Locale

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
        return DevicePresencePresentationMapper
            .availabilityLabel(onlineState)
            .uppercase(Locale.US)
    }

    private fun statusStyle(
        onlineState: DeviceOnlineState
    ): DeviceCompactStatusStyle {
        return if (DevicePresencePresentationMapper.isReachable(onlineState)) {
            DeviceCompactStatusStyle.ONLINE
        } else {
            DeviceCompactStatusStyle.OFFLINE
        }
    }
}
