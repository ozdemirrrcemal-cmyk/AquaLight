package com.aqua.aqualight.data.devices.provisioning.repository

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AqlProvisioningTransactionRegistryTest {

    @Test
    fun `concurrent duplicate registration has exactly one winner`() = runBlocking {
        val registry = registry()
        val startGate = CompletableDeferred<Unit>()
        val registrations = (1..32).map { marker ->
            async(Dispatchers.Default) {
                startGate.await()
                val transaction = Transaction(
                    ownerUid = "owner-a",
                    deviceUid = DeviceUid("device-1"),
                    marker = marker
                )
                transaction to registry.registerIfAbsent(transaction)
            }
        }
        startGate.complete(Unit)

        val completedRegistrations = registrations.awaitAll()

        val winners = completedRegistrations.filter { (_, registered) -> registered }

        assertEquals(1, winners.size)
        assertSame(
            winners.single().first,
            registry.find("owner-a", DeviceUid("device-1"))
        )
    }

    @Test
    fun `device uid matching is case and whitespace insensitive`() = runBlocking {
        val registry = registry()
        val transaction = Transaction(
            ownerUid = "owner-a",
            deviceUid = DeviceUid("device-1"),
            marker = 1
        )

        assertTrue(registry.registerIfAbsent(transaction))
        assertFalse(
            registry.registerIfAbsent(
                transaction.copy(
                    deviceUid = DeviceUid(" DEVICE-1 "),
                    marker = 2
                )
            )
        )
        assertSame(
            transaction,
            registry.find("owner-a", DeviceUid("DEVICE-1"))
        )
    }

    @Test
    fun `same device uid is independent between owners`() = runBlocking {
        val registry = registry()
        val ownerA = Transaction("owner-a", DeviceUid("device-1"), 1)
        val ownerB = Transaction("owner-b", DeviceUid("device-1"), 2)

        assertTrue(registry.registerIfAbsent(ownerA))
        assertTrue(registry.registerIfAbsent(ownerB))
        assertSame(ownerA, registry.find("owner-a", ownerA.deviceUid))
        assertSame(ownerB, registry.find("owner-b", ownerB.deviceUid))
    }

    @Test
    fun `stale transaction cannot remove current transaction`() = runBlocking {
        val registry = registry()
        val current = Transaction("owner-a", DeviceUid("device-1"), 1)
        val staleButEqual = current.copy()

        assertTrue(registry.registerIfAbsent(current))
        assertFalse(registry.remove(staleButEqual))
        assertSame(current, registry.find("owner-a", current.deviceUid))
        assertTrue(registry.remove(current))
        assertNull(registry.find("owner-a", current.deviceUid))
    }

    @Test
    fun `active transaction remains reserved until explicitly removed`() = runBlocking {
        val registry = registry()
        val transaction = Transaction("owner-a", DeviceUid("device-1"), 1)

        assertTrue(registry.registerIfAbsent(transaction))
        assertFalse(registry.registerIfAbsent(transaction.copy(marker = 2)))

        assertSame(
            transaction,
            registry.find("owner-a", transaction.deviceUid)
        )
        assertEquals(
            listOf(transaction.deviceUid),
            registry.deviceUidsForOwner("owner-a")
        )
        assertTrue(registry.remove(transaction))
    }

    private fun registry(): AqlProvisioningTransactionRegistry<Transaction> {
        return AqlProvisioningTransactionRegistry(
            ownerUidOf = Transaction::ownerUid,
            deviceUidOf = Transaction::deviceUid
        )
    }

    private data class Transaction(
        val ownerUid: String,
        val deviceUid: DeviceUid,
        val marker: Int
    )
}
