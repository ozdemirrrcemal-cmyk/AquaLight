package com.aqua.aqualight.data.devices

import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import java.util.Locale

/**
 * Centralized physical-device identity rules.
 *
 * IP is intentionally not part of the identity check. IP is only a mutable
 * network address. A physical device is matched by its stable commercial
 * identity: DeviceUid, MAC address, SerialNumber/FirmwareSerial, ShortId, or
 * stable numeric device id as a final fallback.
 */
object DeviceIdentityMatcher {

    fun samePhysicalDevice(
        savedDevice: DevicesDataStoreManager.DeviceInfo,
        discoveredDevice: DiscoveredAquaDevice
    ): Boolean {
        if (legacyMigrationIdMatches(savedDevice, discoveredDevice)) {
            return true
        }

        if (productMismatch(savedDevice.productId, discoveredDevice.productId)) {
            return false
        }

        return identifiersMatch(savedDevice.deviceUid, discoveredDevice.deviceUid) ||
            identifiersMatch(savedDevice.macAddress, discoveredDevice.macAddress) ||
            identifiersMatch(savedDevice.serialNumber, discoveredDevice.serialNumber) ||
            identifiersMatch(savedDevice.firmwareSerial, discoveredDevice.firmwareSerial) ||
            identifiersMatch(savedDevice.shortId, discoveredDevice.shortId) ||
            stableNumericIdMatches(savedDevice.id, discoveredDevice.id)
    }

    fun samePhysicalDevice(
        savedDevice: DevicesDataStoreManager.DeviceInfo,
        update: DevicesDataStoreManager.DeviceLastSeenUpdate
    ): Boolean {
        if (legacyMigrationIdMatches(savedDevice, update)) {
            return true
        }

        if (productMismatch(savedDevice.productId, update.productId)) {
            return false
        }

        return identifiersMatch(savedDevice.deviceUid, update.deviceUid) ||
            identifiersMatch(savedDevice.macAddress, update.macAddress) ||
            identifiersMatch(savedDevice.serialNumber, update.serialNumber) ||
            identifiersMatch(savedDevice.firmwareSerial, update.firmwareSerial) ||
            identifiersMatch(savedDevice.shortId, update.shortId) ||
            stableNumericIdMatches(savedDevice.id, update.id)
    }

    fun matchesStoredIdentity(
        savedDevice: DevicesDataStoreManager.DeviceInfo,
        id: Long,
        deviceUid: String?,
        macAddress: String?,
        firmwareSerial: String?,
        serialNumber: String? = null,
        shortId: String? = null,
        productId: String? = null
    ): Boolean {
        if (
            stableNumericIdMatches(savedDevice.id, id) &&
            (
                savedDevice.isLegacyCompatDevice() ||
                    isLegacyCompatIdentity(deviceUid = deviceUid)
                )
        ) {
            return true
        }

        if (productMismatch(savedDevice.productId, productId)) {
            return false
        }

        return identifiersMatch(savedDevice.deviceUid, deviceUid) ||
            identifiersMatch(savedDevice.macAddress, macAddress) ||
            identifiersMatch(savedDevice.serialNumber, serialNumber) ||
            identifiersMatch(savedDevice.firmwareSerial, firmwareSerial) ||
            identifiersMatch(savedDevice.shortId, shortId) ||
            stableNumericIdMatches(savedDevice.id, id)
    }

    fun matchesSetupShortId(
        savedDevice: DevicesDataStoreManager.DeviceInfo,
        setupShortId: String
    ): Boolean {
        return matchesSetupShortId(
            setupShortId = setupShortId,
            identityValues = listOf(
                savedDevice.shortId,
                savedDevice.serialNumber,
                savedDevice.deviceUid,
                savedDevice.macAddress,
                savedDevice.firmwareSerial,
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
                discoveredDevice.shortId.orEmpty(),
                discoveredDevice.serialNumber.orEmpty(),
                discoveredDevice.deviceUid.orEmpty(),
                discoveredDevice.macAddress.orEmpty(),
                discoveredDevice.firmwareSerial.orEmpty()
            )
        )
    }


    private fun legacyMigrationIdMatches(
        savedDevice: DevicesDataStoreManager.DeviceInfo,
        discoveredDevice: DiscoveredAquaDevice
    ): Boolean {
        return stableNumericIdMatches(savedDevice.id, discoveredDevice.id) &&
            (
                savedDevice.isLegacyCompatDevice() ||
                    discoveredDevice.isLegacyCompatDevice()
                )
    }

    private fun legacyMigrationIdMatches(
        savedDevice: DevicesDataStoreManager.DeviceInfo,
        update: DevicesDataStoreManager.DeviceLastSeenUpdate
    ): Boolean {
        return stableNumericIdMatches(savedDevice.id, update.id) &&
            (
                savedDevice.isLegacyCompatDevice() ||
                    update.isLegacyCompatDevice()
                )
    }

    private fun DevicesDataStoreManager.DeviceInfo.isLegacyCompatDevice(): Boolean {
        return isLegacyCompatIdentity(deviceUid = deviceUid) ||
            (protocolVersion ?: 0) <= 0
    }

    private fun DiscoveredAquaDevice.isLegacyCompatDevice(): Boolean {
        return isLegacyCompatIdentity(deviceUid = deviceUid) ||
            (protocolVersion ?: 0) <= 0
    }

    private fun DevicesDataStoreManager.DeviceLastSeenUpdate.isLegacyCompatDevice(): Boolean {
        return isLegacyCompatIdentity(deviceUid = deviceUid) ||
            (protocolVersion ?: 0) <= 0
    }

    private fun isLegacyCompatIdentity(
        deviceUid: String?
    ): Boolean {
        return normalizeIdentity(deviceUid.orEmpty())
            .startsWith("aqllegacy")
    }

    private fun stableNumericIdMatches(
        first: Long,
        second: Long
    ): Boolean {
        return first > 0L && second > 0L && first == second
    }

    private fun productMismatch(
        firstProductId: String?,
        secondProductId: String?
    ): Boolean {
        val first = normalizeProductId(firstProductId.orEmpty())
        val second = normalizeProductId(secondProductId.orEmpty())

        return first.isNotBlank() &&
            second.isNotBlank() &&
            first != second
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

    private fun normalizeProductId(
        value: String
    ): String {
        return value
            .trim()
            .lowercase(Locale.US)
    }
}
