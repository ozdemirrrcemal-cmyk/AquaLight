package com.aqua.aqualight.data.devices.store

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KnownDevicesSerializerTest {

    @Test
    fun roundTrip_preservesOwnerScopedRecords() = runBlocking {
        val expected = KnownDevicesStore.newBuilder()
            .addDevices(
                OwnedKnownDeviceRecord.newBuilder()
                    .setOwnerUid("owner-a")
                    .setSnapshot(
                        StoredKnownDeviceSnapshot.newBuilder()
                            .setIdentity(
                                StoredDeviceIdentity.newBuilder()
                                    .setUid("AQL-DEVICE-001")
                            )
                    )
            )
            .addIgnoredDevices(
                IgnoredKnownDeviceRecord.newBuilder()
                    .setOwnerUid("owner-b")
                    .setDeviceUid("AQL-DEVICE-002")
            )
            .build()
        val output = ByteArrayOutputStream()

        KnownDevicesSerializer.writeTo(expected, output)
        val restored = KnownDevicesSerializer.readFrom(
            ByteArrayInputStream(output.toByteArray())
        )

        assertEquals(expected, restored)
    }

    @Test
    fun write_rejectsBlankOwnerRecords() {
        val invalidStore = KnownDevicesStore.newBuilder()
            .addDevices(
                OwnedKnownDeviceRecord.newBuilder()
                    .setOwnerUid("")
                    .setSnapshot(
                        StoredKnownDeviceSnapshot.newBuilder()
                            .setIdentity(
                                StoredDeviceIdentity.newBuilder()
                                    .setUid("AQL-DEVICE-001")
                            )
                    )
            )
            .build()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                KnownDevicesSerializer.writeTo(
                    invalidStore,
                    ByteArrayOutputStream()
                )
            }
        }
    }
}
