package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownDevicesStoreReducerTest {

    @Test
    fun `save snapshots scopes records to owner`() {
        val ownerAStore = KnownDevicesStoreReducer.saveSnapshots(
            store = emptyStore(),
            ownerUid = OWNER_A,
            snapshots = listOf(snapshot(DEVICE_A, "A"))
        )

        val result = KnownDevicesStoreReducer.saveSnapshots(
            store = ownerAStore,
            ownerUid = OWNER_B,
            snapshots = listOf(snapshot(DEVICE_A, "B"))
        )

        assertEquals(2, result.devicesCount)
        assertTrue(result.getDevicesList().any { it.ownerUid == OWNER_A })
        assertTrue(result.getDevicesList().any { it.ownerUid == OWNER_B })
    }

    @Test
    fun `latest incoming snapshot replaces same owner device`() {
        val result = KnownDevicesStoreReducer.saveSnapshots(
            store = emptyStore(),
            ownerUid = OWNER_A,
            snapshots = listOf(
                snapshot(DEVICE_A, "Old"),
                snapshot(DEVICE_A, "New")
            )
        )

        assertEquals(1, result.devicesCount)
        assertEquals("New", result.getDevices(0).identity.customName)
    }

    @Test
    fun `forget atomically removes known record and adds ignored record`() {
        val known = KnownDevicesStoreReducer.saveSnapshots(
            store = emptyStore(),
            ownerUid = OWNER_A,
            snapshots = listOf(snapshot(DEVICE_A, "Device"))
        )

        val result = KnownDevicesStoreReducer.forgetDevice(
            store = known,
            ownerUid = OWNER_A,
            deviceUid = DeviceUid(DEVICE_A)
        )

        assertEquals(0, result.devicesCount)
        assertEquals(1, result.ignoredDevicesCount)
        assertEquals(OWNER_A, result.getIgnoredDevices(0).ownerUid)
        assertEquals(DEVICE_A, result.getIgnoredDevices(0).deviceUid)
    }

    @Test
    fun `saving forgotten device allows it in the same mutation`() {
        val ignored = KnownDevicesStoreReducer.forgetDevice(
            store = emptyStore(),
            ownerUid = OWNER_A,
            deviceUid = DeviceUid(DEVICE_A)
        )

        val result = KnownDevicesStoreReducer.saveSnapshots(
            store = ignored,
            ownerUid = OWNER_A,
            snapshots = listOf(snapshot(DEVICE_A, "Restored"))
        )

        assertEquals(1, result.devicesCount)
        assertEquals(0, result.ignoredDevicesCount)
    }

    @Test
    fun `clear owner preserves other owner records`() {
        val twoOwners = KnownDevicesStoreReducer.saveSnapshots(
            store = KnownDevicesStoreReducer.saveSnapshots(
                store = emptyStore(),
                ownerUid = OWNER_A,
                snapshots = listOf(snapshot(DEVICE_A, "A"))
            ),
            ownerUid = OWNER_B,
            snapshots = listOf(snapshot(DEVICE_B, "B"))
        )

        val result = KnownDevicesStoreReducer.clearOwner(
            store = twoOwners,
            ownerUid = OWNER_A
        )

        assertEquals(1, result.devicesCount)
        assertFalse(result.getDevicesList().any { it.ownerUid == OWNER_A })
        assertTrue(result.getDevicesList().any { it.ownerUid == OWNER_B })
    }

    @Test
    fun `mapper preserves commercial device metadata`() {
        val expected = snapshot(DEVICE_A, "Aqua Prime")
        val stored = KnownDeviceProtoMapper.toStored(OWNER_A, expected)
        val actual = KnownDeviceProtoMapper.toSnapshot(stored)

        assertEquals(expected.identity, actual.identity)
        assertEquals(expected.product, actual.product)
        assertEquals(expected.endpoint, actual.endpoint)
        assertEquals(expected.capabilities, actual.capabilities)
        assertEquals(expected.limits, actual.limits)
        assertEquals(expected.supportedFeatures.sorted(), actual.supportedFeatures)
        assertEquals(expected.supportedScreens.sorted(), actual.supportedScreens)
        assertEquals(expected.modules.sorted(), actual.modules)
    }

    @Test(expected = KnownDevicesValidationException::class)
    fun `validation rejects duplicate owner device records`() {
        val stored = KnownDeviceProtoMapper.toStored(
            ownerUid = OWNER_A,
            snapshot = snapshot(DEVICE_A, "Device")
        )
        val invalid = KnownDevicesStore.newBuilder()
            .addDevices(stored)
            .addDevices(stored)
            .build()

        KnownDevicesStoreReducer.validate(invalid)
    }

    @Test(expected = KnownDevicesValidationException::class)
    fun `validation rejects known ignored overlap`() {
        val stored = KnownDeviceProtoMapper.toStored(
            ownerUid = OWNER_A,
            snapshot = snapshot(DEVICE_A, "Device")
        )
        val invalid = KnownDevicesStore.newBuilder()
            .addDevices(stored)
            .addIgnoredDevices(
                StoredIgnoredDevice.newBuilder()
                    .setOwnerUid(OWNER_A)
                    .setDeviceUid(DEVICE_A)
                    .build()
            )
            .build()

        KnownDevicesStoreReducer.validate(invalid)
    }

    private fun snapshot(
        uid: String,
        customName: String
    ): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(
                uid = DeviceUid(uid),
                shortId = "short-$uid",
                chipId = "chip-$uid",
                espChipId = "esp-$uid",
                efuseMac = "AA:BB:CC:DD:EE:FF",
                macAddress = "11:22:33:44:55:66",
                serialNumber = "serial-$uid",
                firmwareSerial = "firmware-$uid",
                displayName = "Display $uid",
                customName = customName,
                setupCode = "123456",
                setupSsid = "Aqua-$uid"
            ),
            product = DeviceProduct(
                brand = "AquaLight",
                productId = "product-$uid",
                productKey = "key-$uid",
                family = DeviceFamily.LIGHT,
                familyRaw = "light",
                line = "Prime",
                model = "L1",
                displayName = "Prime Light",
                skuId = "sku-$uid",
                skuCode = "code-$uid",
                setupCode = "setup-$uid",
                hardwareRevision = "rev-a"
            ),
            firmwareVersion = "1.2.3",
            firmwareBuild = "42",
            apiVersion = "2",
            protocolVersion = "3",
            endpoint = DeviceRuntimeEndpoint(
                ip = "192.168.1.20",
                wifiMode = "sta",
                wifiConnected = true,
                setupApActive = false,
                runtimeTransport = "ws",
                wsPort = 81,
                wsPath = "/ws",
                wsProtocol = "aqualight",
                wsProtocolVersion = 2,
                discoveryPort = 4210
            ),
            capabilities = DeviceCapabilities(
                light = true,
                manualLight = true,
                lightProgram = true,
                lightPresets = true,
                lightSimulation = true,
                fan = true,
                cooling = true,
                temperature = true,
                standaloneTimer = true,
                dosing = false,
                timeSync = true,
                ota = true
            ),
            limits = DeviceLimits(
                lightChannelCount = 6,
                fanOutputCount = 2,
                temperatureSensorCount = 1,
                timerChannelCount = 4,
                dosingChannelCount = 0
            ),
            supportedFeatures = listOf("manual", "program"),
            supportedScreens = listOf("light", "settings"),
            modules = listOf("light", "fan"),
            lastSeenAtMillis = 1234L
        )
    }

    private fun emptyStore(): KnownDevicesStore {
        return KnownDevicesStore.getDefaultInstance()
    }

    private companion object {
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
        const val DEVICE_A = "device-a"
        const val DEVICE_B = "device-b"
    }
}
