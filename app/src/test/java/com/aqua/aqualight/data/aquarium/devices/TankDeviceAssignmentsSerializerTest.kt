package com.aqua.aqualight.data.aquarium.devices

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TankDeviceAssignmentsSerializerTest {

    @Test
    fun roundTrip_preservesOwnerScopedAssignmentMetadata() = runBlocking {
        val expectedAssignment = StoredTankDeviceAssignment.newBuilder()
            .setOwnerUid("owner-123")
            .setTankId(42L)
            .setDeviceUid("AQL-DEVICE-001")
            .setAssignedAtMillis(1_000L)
            .setUpdatedAtMillis(2_000L)
            .build()
        val expectedStore = TankDeviceAssignmentsStore.newBuilder()
            .addAssignments(expectedAssignment)
            .build()
        val output = ByteArrayOutputStream()

        TankDeviceAssignmentsSerializer.writeTo(
            t = expectedStore,
            output = output
        )

        val restored = TankDeviceAssignmentsSerializer.readFrom(
            ByteArrayInputStream(output.toByteArray())
        )

        assertEquals(1, restored.assignmentsCount)
        assertEquals(expectedAssignment, restored.getAssignments(0))
    }
}
