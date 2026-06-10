package com.aqua.aqualight.data.devices

import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import java.util.Locale

/**
 * Centralized physical-device identity rules.
 *
 * IP is intentionally not part of the identity check. IP is only a mutable
 * network address. A physical device is matched by its stable firmware identity:
 * numeric chip id, DeviceUid, MAC address, or firmware serial.
 */
object DeviceIdentityMatcher {

    fun samePhysicalDevice(
        savedDevice: DevicesDataStoreManager.DeviceInfo,
        discoveredDevice: DiscoveredAquaDevice
    ): Boolean {
        if (savedDevice.id > 0L && savedDevice.id == discoveredDevice.id) {
            return true
        }

        return identifiersMatch(savedDevice.deviceUid, discoveredDevice.deviceUid) ||
            identifiersMatch(savedDevice.macAddress, discoveredDevice.macAddress) ||
            identifiersMatch(savedDevice.firmwareSerial, discoveredDevice.firmwareSerial)
    }

    fun samePhysicalDevice(
        savedDevice: DevicesDataStoreManager.DeviceInfo,
        update: DevicesDataStoreManager.DeviceLastSeenUpdate
    ): Boolean {
        if (savedDevice.id > 0L && savedDevice.id == update.id) {
            return true
        }

        return identifiersMatch(savedDevice.deviceUid, update.deviceUid) ||
            identifiersMatch(savedDevice.macAddress, update.macAddress) ||
            identifiersMatch(savedDevice.firmwareSerial, update.firmwareSerial)
    }

    fun matchesStoredIdentity(
        savedDevice: DevicesDataStoreManager.DeviceInfo,
        id: Long,
        deviceUid: String?,
        macAddress: String?,
        firmwareSerial: String?
    ): Boolean {
        if (savedDevice.id > 0L && savedDevice.id == id) {
            return true
        }

        return identifiersMatch(savedDevice.deviceUid, deviceUid) ||
            identifiersMatch(savedDevice.macAddress, macAddress) ||
            identifiersMatch(savedDevice.firmwareSerial, firmwareSerial)
    }

    fun matchesSetupShortId(
        savedDevice: DevicesDataStoreManager.DeviceInfo,
        setupShortId: String
    ): Boolean {
        return matchesSetupShortId(
            setupShortId = setupShortId,
            identityValues = listOf(
                savedDevice.id.toString(),
                savedDevice.deviceUid,
                savedDevice.macAddress,
                savedDevice.firmwareSerial,
                savedDevice.serial
            )
        )
    }

    fun matchesSetupShortId(
        discoveredDevice: DiscoveredAquaDevice,
        setupShortId: String
    ): Boolean {
        return matchesSetupShortId(
            setupShortId = setupShortId,
            identityValues = listOf(
                discoveredDevice.id.toString(),
                discoveredDevice.deviceUid.orEmpty(),
                discoveredDevice.macAddress.orEmpty(),
                discoveredDevice.firmwareSerial.orEmpty()
            )
        )
    }

    private fun matchesSetupShortId(
        setupShortId: String,
        identityValues: List<String>
    ): Boolean {
        val normalizedShortId = normalizeIdentity(setupShortId)
            .trimStart('0')

        if (normalizedShortId.isBlank()) {
            return false
        }

        return identityValues.any { value ->
            val normalizedValue = normalizeIdentity(value)

            normalizedValue.isNotBlank() &&
                (
                    normalizedValue.endsWith(normalizedShortId) ||
                        normalizedValue.trimStart('0').endsWith(normalizedShortId)
                    )
        }
    }

    private fun identifiersMatch(
        first: String?,
        second: String?
    ): Boolean {
        val left = normalizeIdentity(first.orEmpty())
        val right = normalizeIdentity(second.orEmpty())

        return left.isNotBlank() &&
            right.isNotBlank() &&
            left == right
    }

    private fun normalizeIdentity(
        value: String
    ): String {
        return value
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]"), "")
    }
}
