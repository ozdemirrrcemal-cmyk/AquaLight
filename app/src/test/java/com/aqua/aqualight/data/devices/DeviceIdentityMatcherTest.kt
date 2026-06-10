package com.aqua.aqualight.data.devices

import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityMatcherTest {

    @Test
    fun samePhysicalDevice_matchesByNormalizedMacAddress() {
        val savedDevice = savedDevice(
            id = 1001L,
            macAddress = "AA:BB:CC:11:22:33"
        )

        val discoveredDevice = discoveredDevice(
            id = 2002L,
            ip = "192.168.1.25",
            macAddress = "aa-bb-cc-11-22-33"
        )

        assertTrue(
            DeviceIdentityMatcher.samePhysicalDevice(
                savedDevice = savedDevice,
                discoveredDevice = discoveredDevice
            )
        )
    }

    @Test
    fun samePhysicalDevice_matchesByDeviceUidWhenNumericIdChanges() {
        val savedDevice = savedDevice(
            id = 1001L,
            deviceUid = "AquaLight-00112233"
        )

        val discoveredDevice = discoveredDevice(
            id = 2002L,
            ip = "192.168.1.26",
            deviceUid = "aqualight_00112233"
        )

        assertTrue(
            DeviceIdentityMatcher.samePhysicalDevice(
                savedDevice = savedDevice,
                discoveredDevice = discoveredDevice
            )
        )
    }

    @Test
    fun samePhysicalDevice_doesNotMatchOnlyBecauseIpIsSame() {
        val savedDevice = savedDevice(
            id = 1001L,
            deviceUid = "device-a",
            macAddress = "AA:AA:AA:AA:AA:AA"
        )

        val discoveredDevice = discoveredDevice(
            id = 2002L,
            ip = "192.168.1.25",
            deviceUid = "device-b",
            macAddress = "BB:BB:BB:BB:BB:BB"
        )

        assertFalse(
            DeviceIdentityMatcher.samePhysicalDevice(
                savedDevice = savedDevice,
                discoveredDevice = discoveredDevice
            )
        )
    }

    @Test
    fun matchesSetupShortId_matchesIdentifierSuffix() {
        val savedDevice = savedDevice(
            id = 1001L,
            deviceUid = "AQL-0000-00AB-CD12"
        )

        assertTrue(
            DeviceIdentityMatcher.matchesSetupShortId(
                savedDevice = savedDevice,
                setupShortId = "ABCD12"
            )
        )
    }

    private fun savedDevice(
        id: Long,
        deviceUid: String = "",
        macAddress: String = "",
        firmwareSerial: String = ""
    ): DevicesDataStoreManager.DeviceInfo {
        return DevicesDataStoreManager.DeviceInfo(
            id = id,
            aquaName = "AquaLight",
            name = "WRGB Pro2",
            ip = "192.168.1.25",
            serial = "AQL-$id",
            deviceUid = deviceUid,
            macAddress = macAddress,
            firmwareSerial = firmwareSerial,
            firmwareBuild = "test",
            lastSeenMillis = 0L,
            deviceType = AquaDeviceType.AQUA_LIGHT_001
        )
    }

    private fun discoveredDevice(
        id: Long,
        ip: String,
        deviceUid: String? = null,
        macAddress: String? = null,
        firmwareSerial: String? = null
    ): DiscoveredAquaDevice {
        return DiscoveredAquaDevice(
            id = id,
            ip = ip,
            aquaName = "AquaLight",
            name = "WRGB Pro2",
            deviceUid = deviceUid,
            macAddress = macAddress,
            firmwareSerial = firmwareSerial,
            firmwareBuild = "test",
            udpVersion = 20240813,
            tabLight = true,
            tabTimer = false,
            tabTemperature = false,
            deviceType = AquaDeviceType.AQUA_LIGHT_001
        )
    }
}
