package com.aqua.aqualight.data.aquarium.devices

import androidx.datastore.core.CorruptionException
import com.aqua.aqualight.data.store.CommercialStoreSchema
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TankDeviceAssignmentsSerializerTest {

    @Test
    fun `serializer round trips valid assignments`() {
        runBlocking {
            val expected = TankDeviceAssignmentsStore.newBuilder()
                .setSchemaVersion(CommercialStoreSchema.TANK_DEVICE_ASSIGNMENTS_VERSION)
                .addAssignments(
                    StoredTankDeviceAssignment.newBuilder()
                        .setOwnerUid("owner-a")
                        .setTankId(10L)
                        .setDeviceUid("device-a")
                        .setAssignedAtMillis(100L)
                        .setLightInstallation(validInstallation())
                        .build()
                )
                .build()

            val output = ByteArrayOutputStream()
            TankDeviceAssignmentsSerializer.writeTo(expected, output)

            val actual = TankDeviceAssignmentsSerializer.readFrom(
                ByteArrayInputStream(output.toByteArray())
            )

            assertEquals(expected, actual)
        }
    }

    @Test(expected = CorruptionException::class)
    fun `serializer rejects duplicate device assignment for one owner`() {
        runBlocking {
            val invalid = TankDeviceAssignmentsStore.newBuilder()
                .setSchemaVersion(CommercialStoreSchema.TANK_DEVICE_ASSIGNMENTS_VERSION)
                .addAssignments(stored(tankId = 10L))
                .addAssignments(stored(tankId = 20L))
                .build()

            TankDeviceAssignmentsSerializer.readFrom(
                ByteArrayInputStream(invalid.toByteArray())
            )
        }
    }

    @Test(expected = CorruptionException::class)
    fun `serializer rejects malformed protobuf bytes`() {
        runBlocking {
            TankDeviceAssignmentsSerializer.readFrom(
                ByteArrayInputStream(byteArrayOf(0x0A, 0x7F, 0x01))
            )
        }
    }

    private fun stored(
        tankId: Long
    ): StoredTankDeviceAssignment {
        return StoredTankDeviceAssignment.newBuilder()
            .setOwnerUid("owner-a")
            .setTankId(tankId)
            .setDeviceUid("device-a")
            .setAssignedAtMillis(100L)
            .setLightInstallation(validInstallation())
            .build()
    }

    private fun validInstallation(): StoredLightInstallationProfile =
        StoredLightInstallationProfile.newBuilder()
            .setContractRevision(TankLightInstallationProfile.CONTRACT_REVISION)
            .build()
}
