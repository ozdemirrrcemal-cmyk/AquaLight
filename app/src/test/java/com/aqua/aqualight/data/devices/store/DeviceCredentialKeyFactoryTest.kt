package com.aqua.aqualight.data.devices.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCredentialKeyFactoryTest {

    @Test
    fun sameOwnerAndDevice_producesStableKey() {
        val first = DeviceCredentialKeyFactory.key(
            ownerUid = "owner-a",
            deviceUid = "aql-device-001"
        )
        val second = DeviceCredentialKeyFactory.key(
            ownerUid = "owner-a",
            deviceUid = "AQL-DEVICE-001"
        )

        assertEquals(first, second)
    }

    @Test
    fun sameDeviceForDifferentOwners_producesDifferentKeys() {
        val ownerAKey = DeviceCredentialKeyFactory.key(
            ownerUid = "owner-a",
            deviceUid = "AQL-DEVICE-001"
        )
        val ownerBKey = DeviceCredentialKeyFactory.key(
            ownerUid = "owner-b",
            deviceUid = "AQL-DEVICE-001"
        )

        assertNotEquals(ownerAKey, ownerBKey)
    }

    @Test
    fun ownerPrefix_matchesOnlyThatOwnersCredentialKeys() {
        val ownerAPrefix = DeviceCredentialKeyFactory.ownerPrefix("owner-a")
        val ownerAKey = DeviceCredentialKeyFactory.key(
            ownerUid = "owner-a",
            deviceUid = "AQL-DEVICE-001"
        )
        val ownerBKey = DeviceCredentialKeyFactory.key(
            ownerUid = "owner-b",
            deviceUid = "AQL-DEVICE-001"
        )

        assertTrue(ownerAKey.startsWith(ownerAPrefix))
        assertTrue(!ownerBKey.startsWith(ownerAPrefix))
    }
}
