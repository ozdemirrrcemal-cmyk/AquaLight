package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCredentialKeyFactoryTest {

    @Test
    fun `same device has different key for different owners`() {
        val ownerAKey = DeviceCredentialKeyFactory.tokenKey(
            ownerUid = "owner-a",
            deviceUid = DeviceUid("device-1")
        )
        val ownerBKey = DeviceCredentialKeyFactory.tokenKey(
            ownerUid = "owner-b",
            deviceUid = DeviceUid("device-1")
        )

        assertNotEquals(ownerAKey, ownerBKey)
    }

    @Test
    fun `device uid normalization is deterministic`() {
        val lower = DeviceCredentialKeyFactory.tokenKey(
            ownerUid = "owner-a",
            deviceUid = DeviceUid("device-1")
        )
        val upper = DeviceCredentialKeyFactory.tokenKey(
            ownerUid = "owner-a",
            deviceUid = DeviceUid(" DEVICE-1 ")
        )

        assertEquals(lower, upper)
    }

    @Test
    fun `token key starts with target owner prefix`() {
        val prefix = DeviceCredentialKeyFactory.ownerPrefix("owner-a")
        val key = DeviceCredentialKeyFactory.tokenKey(
            ownerUid = "owner-a",
            deviceUid = DeviceUid("device-1")
        )

        assertTrue(key.startsWith(prefix))
    }

    @Test
    fun `pending and committed token keys are distinct and owner scoped`() {
        val deviceUid = DeviceUid("device-1")
        val committedKey = DeviceCredentialKeyFactory.tokenKey(
            ownerUid = "owner-a",
            deviceUid = deviceUid
        )
        val pendingKey = DeviceCredentialKeyFactory.pendingTokenKey(
            ownerUid = "owner-a",
            deviceUid = deviceUid
        )

        assertNotEquals(committedKey, pendingKey)
        assertTrue(
            pendingKey.startsWith(
                DeviceCredentialKeyFactory.pendingTokenPrefix("owner-a")
            )
        )
    }

    @Test
    fun `pending token key uses normalized device uid`() {
        val lower = DeviceCredentialKeyFactory.pendingTokenKey(
            ownerUid = "owner-a",
            deviceUid = DeviceUid("device-1")
        )
        val upper = DeviceCredentialKeyFactory.pendingTokenKey(
            ownerUid = "owner-a",
            deviceUid = DeviceUid(" DEVICE-1 ")
        )

        assertEquals(lower, upper)
    }

    @Test
    fun `key does not expose raw owner or device identity`() {
        val key = DeviceCredentialKeyFactory.tokenKey(
            ownerUid = "secret-owner",
            deviceUid = DeviceUid("secret-device")
        )

        assertFalse(key.contains("secret-owner"))
        assertFalse(key.contains("secret-device", ignoreCase = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank owner is rejected`() {
        DeviceCredentialKeyFactory.ownerPrefix("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank device is rejected`() {
        DeviceCredentialKeyFactory.tokenKey(
            ownerUid = "owner-a",
            deviceUid = DeviceUid("   ")
        )
    }
}
