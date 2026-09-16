package com.aqua.aqualight.data.aquarium.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TankDeviceAssignmentRulesTest {

    @Test
    fun `assign stores one owner scoped relationship`() {
        val mutation = TankDeviceAssignmentRules.assign(
            store = emptyStore(),
            ownerUid = OWNER_A,
            tankId = TANK_A,
            deviceUid = DEVICE_A,
            assignedAtMillis = 100L
        )

        assertTrue(mutation.decision is TankDeviceStoreAssignDecision.Assigned)
        assertEquals(1, mutation.store.assignmentsCount)
        assertEquals(OWNER_A, mutation.store.getAssignments(0).ownerUid)
        assertEquals(TANK_A, mutation.store.getAssignments(0).tankId)
        assertEquals(DEVICE_A, mutation.store.getAssignments(0).deviceUid)
    }

    @Test
    fun `assign is idempotent for same owner device and tank`() {
        val first = assignedStore(
            ownerUid = OWNER_A,
            tankId = TANK_A,
            deviceUid = DEVICE_A
        )

        val mutation = TankDeviceAssignmentRules.assign(
            store = first,
            ownerUid = OWNER_A,
            tankId = TANK_A,
            deviceUid = DEVICE_A,
            assignedAtMillis = 200L
        )

        assertTrue(mutation.decision is TankDeviceStoreAssignDecision.AlreadyAssigned)
        assertSame(first, mutation.store)
        assertEquals(1, mutation.store.assignmentsCount)
        assertEquals(100L, mutation.store.getAssignments(0).assignedAtMillis)
    }

    @Test
    fun `assign rejects second tank for same owner device`() {
        val first = assignedStore(
            ownerUid = OWNER_A,
            tankId = TANK_A,
            deviceUid = DEVICE_A
        )

        val mutation = TankDeviceAssignmentRules.assign(
            store = first,
            ownerUid = OWNER_A,
            tankId = TANK_B,
            deviceUid = DEVICE_A,
            assignedAtMillis = 200L
        )

        val conflict = mutation.decision as TankDeviceStoreAssignDecision.Conflict
        assertSame(first, mutation.store)
        assertEquals(TANK_A, conflict.existingAssignment.tankId)
        assertEquals(1, mutation.store.assignmentsCount)
    }

    @Test
    fun `same device uid can be assigned independently by different owners`() {
        val ownerAStore = TankDeviceAssignmentRules.assign(
            store = emptyStore(),
            ownerUid = OWNER_A,
            tankId = TANK_A,
            deviceUid = DEVICE_A,
            assignedAtMillis = 100L
        ).store

        val mutation = TankDeviceAssignmentRules.assign(
            store = ownerAStore,
            ownerUid = OWNER_B,
            tankId = TANK_B,
            deviceUid = DEVICE_A,
            assignedAtMillis = 200L
        )

        assertTrue(mutation.decision is TankDeviceStoreAssignDecision.Assigned)
        assertEquals(2, mutation.store.assignmentsCount)
    }

    @Test
    fun `repair removes only stale records for target owner`() {
        val store = TankDeviceAssignmentsStore.newBuilder()
            .addAssignments(stored(OWNER_A, TANK_A, DEVICE_A, 100L))
            .addAssignments(stored(OWNER_A, TANK_B, DEVICE_B, 200L))
            .addAssignments(stored(OWNER_B, TANK_B, DEVICE_B, 300L))
            .build()

        val mutation = TankDeviceAssignmentRules.repairOwner(
            store = store,
            ownerUid = OWNER_A,
            validTankIds = setOf(TANK_A),
            validDeviceUids = setOf(DEVICE_A)
        )

        assertEquals(1, mutation.removedAssignments.size)
        assertEquals(2, mutation.store.assignmentsCount)
        assertTrue(
            mutation.store.getAssignmentsList().any { assignment ->
                assignment.ownerUid == OWNER_B
            }
        )
    }

    @Test(expected = TankDeviceAssignmentsValidationException::class)
    fun `validation rejects duplicate device assignment for one owner`() {
        val store = TankDeviceAssignmentsStore.newBuilder()
            .addAssignments(stored(OWNER_A, TANK_A, DEVICE_A, 100L))
            .addAssignments(stored(OWNER_A, TANK_B, DEVICE_A, 200L))
            .build()

        TankDeviceAssignmentRules.validate(store)
    }

    private fun emptyStore(): TankDeviceAssignmentsStore {
        return TankDeviceAssignmentsStore.getDefaultInstance()
    }

    private fun assignedStore(
        ownerUid: String,
        tankId: Long,
        deviceUid: String
    ): TankDeviceAssignmentsStore {
        return TankDeviceAssignmentsStore.newBuilder()
            .addAssignments(stored(ownerUid, tankId, deviceUid, 100L))
            .build()
    }

    private fun stored(
        ownerUid: String,
        tankId: Long,
        deviceUid: String,
        assignedAtMillis: Long
    ): StoredTankDeviceAssignment {
        return StoredTankDeviceAssignment.newBuilder()
            .setOwnerUid(ownerUid)
            .setTankId(tankId)
            .setDeviceUid(deviceUid)
            .setAssignedAtMillis(assignedAtMillis)
            .build()
    }

    private companion object {
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
        const val DEVICE_A = "device-a"
        const val DEVICE_B = "device-b"
        const val TANK_A = 10L
        const val TANK_B = 20L
    }
}
