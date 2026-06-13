package com.aqua.aqualight.data.devices.card

import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
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
            AquaDeviceCatalog.findByProductId(
                productId = device.productId
            ) ?: AquaDeviceCatalog.findByProductKey(
                productKey = device.productKey
            ) ?: AquaDeviceCatalog.findByType(
                type = device.deviceType
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

        val isOnline =
            statusState?.isOnline == true

        val lastSeenMillis =
            statusState?.lastSeenMillis ?: device.lastSeenMillis

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
            ip = statusState?.ip ?: device.ip,
            serial = device.serial,
            firmwareBuild = device.firmwareBuild,
            productId = device.productId,
            productKey = device.productKey,
            category = device.category,
            deviceType = device.deviceType,
            isOnline = isOnline,
            lastSeenMillis = lastSeenMillis,
            lastCheckedMillis = statusState?.lastCheckedMillis,
            missedChecks = statusState?.missedChecks ?: 0,
            statusText = if (isOnline) {
                "Online"
            } else {
                "Offline"
            },
            lastSeenText = if (includeLastSeenText) {
                formatLastSeen(
                    isOnline = isOnline,
                    lastSeenMillis = lastSeenMillis,
                    nowMillis = nowMillis
                )
            } else {
                ""
            }
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
