package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRegistryStoreDiscoveryTest {

    @Test
    fun `clean registry ignores LAN discovery`() {
        val store = DeviceRegistryStore()

        store.updateExistingAll(
            listOf(snapshot(uid = UNKNOWN_UID, ip = "192.168.1.55"))
        )

        assertTrue(store.currentDevices().isEmpty())
    }

    @Test
    fun `registered device receives discovery endpoint update without losing metadata`() {
        val store = DeviceRegistryStore()
        val registeredUid = DeviceUid(REGISTERED_UID)
        store.upsert(
            snapshot(
                uid = REGISTERED_UID,
                ip = "192.168.1.10",
                customName = "Salon Akvaryumu",
                productName = "WRGB Pro Elite 120",
                lastSeenAtMillis = 10L
            )
        )

        store.updateExistingAll(
            listOf(
                snapshot(
                    uid = REGISTERED_UID,
                    ip = "192.168.1.99",
                    lastSeenAtMillis = 20L
                ),
                snapshot(uid = UNKNOWN_UID, ip = "192.168.1.77")
            )
        )

        val updated = store.currentDevice(registeredUid)
        requireNotNull(updated)
        assertEquals("192.168.1.99", updated.endpoint.ip)
        assertEquals("Salon Akvaryumu", updated.identity.customName)
        assertEquals("WRGB Pro Elite 120", updated.product.displayName)
        assertEquals(20L, updated.lastSeenAtMillis)
        assertEquals(1, store.currentDevices().size)
        assertNull(store.currentDevice(DeviceUid(UNKNOWN_UID)))
    }

    @Test
    fun `late discovery cannot resurrect a removed device`() {
        val store = DeviceRegistryStore()
        val registeredUid = DeviceUid(REGISTERED_UID)
        store.upsert(snapshot(uid = REGISTERED_UID, ip = "192.168.1.10"))
        store.remove(registeredUid)

        store.updateExistingAll(
            listOf(snapshot(uid = REGISTERED_UID, ip = "192.168.1.99"))
        )

        assertNull(store.currentDevice(registeredUid))
        assertTrue(store.currentDevices().isEmpty())
    }

    private fun snapshot(
        uid: String,
        ip: String,
        customName: String = "",
        productName: String = "",
        lastSeenAtMillis: Long = 0L
    ): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(
                uid = DeviceUid(uid),
                customName = customName
            ),
            product = DeviceProduct(displayName = productName),
            endpoint = DeviceRuntimeEndpoint(
                ip = ip,
                wsPort = 81
            ),
            lastSeenAtMillis = lastSeenAtMillis
        )
    }

    private companion object {
        const val REGISTERED_UID = "AQL-DEVICE-001"
        const val UNKNOWN_UID = "AQL-UNKNOWN-999"
    }
}
