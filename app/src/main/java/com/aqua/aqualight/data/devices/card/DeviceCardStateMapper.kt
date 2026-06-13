package com.aqua.aqualight.data.devices.card

import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.DeviceSerialFormatter
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Central mapper for common device card metadata.
 *
 * Keep all screen-independent card rules here. Screens may still map this
 * state into their own existing UI classes to preserve layout and card type.
 */
class DeviceCardStateMapper {

    fun map(
        device: DevicesDataStoreManager.DeviceInfo,
        statuses: Map<Long, DeviceStatusState>,
        tanks: List<SavedAquariumTank> = emptyList(),
        nowMillis: Long = System.currentTimeMillis(),
        unassignedTankText: String = "",
        unknownTankText: String = "Unknown aquarium",
        includeLastSeenText: Boolean = false
    ): DeviceCardUiState {
        val statusState =
            statuses[device.id]

        val definition =
            AquaDeviceCatalog.findDefinition(
                productId = device.productId,
                productKey = device.productKey,
                category = device.category
            )

        val title =
            device.customName.ifBlank {
                device.displayName.ifBlank {
                    definition?.displayName
                        ?: device.name.ifBlank {
                            device.productModel.ifBlank {
                                "Device"
                            }
                        }
                }
            }

        val familyName =
            device.productFamily.ifBlank {
                definition?.productFamily
                    ?: device.aquaName.ifBlank {
                        "Unknown"
                    }
            }

        val productLine =
            device.productLine.ifBlank {
                definition?.productLine.orEmpty()
            }

        val productModel =
            device.productModel.ifBlank {
                definition?.productModel ?: device.name
            }

        val productKey =
            definition?.productKey ?: device.productKey

        val category =
            definition?.category ?: device.category

        val setupCode =
            device.setupCode.ifBlank {
                definition?.setupCode ?: productKey.setupCode.takeUnless { code ->
                    productKey.name == "UNKNOWN"
                }.orEmpty()
            }

        val productId =
            device.productId.ifBlank {
                definition?.productId ?: productKey.productId.takeUnless { id ->
                    productKey.name == "UNKNOWN"
                }.orEmpty()
            }

        val commercialIdentifier =
            DeviceSerialFormatter.buildCommercialIdentifier(
                setupCode = setupCode,
                serialNumber = device.serialNumber,
                shortId = device.shortId,
                deviceUid = device.deviceUid,
                macAddress = device.macAddress,
                firmwareSerial = device.firmwareSerial,
                fallbackNumericId = device.id
            )

        val serial =
            DeviceSerialFormatter.displaySerial(
                serial = device.serial.ifBlank {
                    commercialIdentifier
                }
            )

        val isOnline =
            statusState?.isOnline == true

        val lastSeenMillis =
            statusState?.lastSeenMillis ?: device.lastSeenMillis

        val ip =
            statusState?.ip ?: device.ip

        val lastSeenText =
            if (includeLastSeenText) {
                formatLastSeen(
                    isOnline = isOnline,
                    lastSeenMillis = lastSeenMillis,
                    nowMillis = nowMillis
                )
            } else {
                ""
            }

        val statusText =
            if (isOnline) {
                "Online"
            } else {
                "Offline"
            }

        return DeviceCardUiState(
            deviceId = device.id,
            title = title,
            familyName = familyName,
            tankName = tankNameForDevice(
                device = device,
                tanks = tanks,
                unassignedTankText = unassignedTankText,
                unknownTankText = unknownTankText
            ),
            ip = ip,
            serial = serial,
            firmwareBuild = device.firmwareBuild,
            productId = productId,
            productKey = productKey,
            category = category,
            productLine = productLine,
            productModel = productModel,
            skuCode = device.skuCode,
            setupCode = setupCode,
            deviceUid = device.deviceUid,
            macAddress = device.macAddress,
            serialNumber = device.serialNumber,
            shortId = device.shortId,
            hardwareRevision = device.hardwareRevision,
            firmwareVersion = device.firmwareVersion,
            protocolVersion = device.protocolVersion,
            productMetaText = buildProductMetaText(
                familyName = familyName,
                productLine = productLine,
                category = category
            ),
            identityText = buildIdentityText(
                serial = serial,
                skuCode = device.skuCode,
                shortId = device.shortId,
                deviceUid = device.deviceUid
            ),
            networkText = buildNetworkText(
                statusText = statusText,
                ip = ip,
                lastSeenText = lastSeenText
            ),
            statusText = statusText,
            isOnline = isOnline,
            lastSeenMillis = lastSeenMillis,
            lastCheckedMillis = statusState?.lastCheckedMillis,
            missedChecks = statusState?.missedChecks ?: 0,
            lastSeenText = lastSeenText
        )
    }

    fun mapAll(
        devices: List<DevicesDataStoreManager.DeviceInfo>,
        statuses: Map<Long, DeviceStatusState>,
        tanks: List<SavedAquariumTank> = emptyList(),
        nowMillis: Long = System.currentTimeMillis(),
        unassignedTankText: String = "",
        unknownTankText: String = "Unknown aquarium",
        includeLastSeenText: Boolean = false
    ): List<DeviceCardUiState> {
        return devices.map { device ->
            map(
                device = device,
                statuses = statuses,
                tanks = tanks,
                nowMillis = nowMillis,
                unassignedTankText = unassignedTankText,
                unknownTankText = unknownTankText,
                includeLastSeenText = includeLastSeenText
            )
        }
    }

    private fun tankNameForDevice(
        device: DevicesDataStoreManager.DeviceInfo,
        tanks: List<SavedAquariumTank>,
        unassignedTankText: String,
        unknownTankText: String
    ): String {
        val connectedTankId =
            device.tankId ?: return unassignedTankText

        return tanks.firstOrNull { tank ->
            tank.id == connectedTankId
        }?.name ?: unknownTankText
    }

    private fun buildProductMetaText(
        familyName: String,
        productLine: String,
        category: AquaDeviceCategory
    ): String {
        return listOf(
            familyName,
            productLine,
            categoryDisplayName(
                category = category
            )
        ).filter { value ->
            value.isNotBlank()
        }.distinct().joinToString(
            separator = " • "
        )
    }

    private fun buildIdentityText(
        serial: String,
        skuCode: String,
        shortId: String,
        deviceUid: String
    ): String {
        val identity = DeviceSerialFormatter.displaySerial(
            serial = serial
        )

        val primary = if (identity.isNotBlank()) {
            identity
        } else if (shortId.isNotBlank()) {
            "Device code: $shortId"
        } else {
            deviceUid
        }

        return listOf(
            primary,
            skuCode
        ).filter { value ->
            value.isNotBlank()
        }.distinct().joinToString(
            separator = " • "
        )
    }

    private fun buildNetworkText(
        statusText: String,
        ip: String,
        lastSeenText: String
    ): String {
        return listOf(
            statusText,
            ip.ifBlank {
                "No IP"
            },
            lastSeenText
        ).filter { value ->
            value.isNotBlank()
        }.distinct().joinToString(
            separator = " • "
        )
    }

    private fun categoryDisplayName(
        category: AquaDeviceCategory
    ): String {
        return when (category) {
            AquaDeviceCategory.LIGHT -> "Light"
            AquaDeviceCategory.TIMER -> "Timer"
            AquaDeviceCategory.COOLING -> "Cooling"
            AquaDeviceCategory.DOSING -> "Dosing"
            AquaDeviceCategory.CONTROLLER -> "Controller"
            AquaDeviceCategory.UNKNOWN -> ""
        }
    }

    private fun formatLastSeen(
        isOnline: Boolean,
        lastSeenMillis: Long,
        nowMillis: Long
    ): String {
        if (isOnline) {
            return "Online now"
        }

        if (lastSeenMillis <= 0L) {
            return "Never seen"
        }

        val deltaMs =
            (nowMillis - lastSeenMillis).coerceAtLeast(
                0L
            )

        val minutes =
            TimeUnit.MILLISECONDS.toMinutes(
                deltaMs
            )

        return when {
            minutes < 1L -> "Just now"
            minutes < 60L -> "$minutes min ago"
            minutes < 1440L -> "${minutes / 60L} h ago"
            else -> "${minutes / 1440L} d ago"
        }
    }
}
