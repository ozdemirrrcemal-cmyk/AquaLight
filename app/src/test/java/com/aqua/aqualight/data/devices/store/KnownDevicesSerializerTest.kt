package com.aqua.aqualight.data.devices.store

import androidx.datastore.core.CorruptionException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class KnownDevicesSerializerTest {

    @Test
    fun `serializer round trips valid store`() {
        runBlocking {
            val expected = KnownDevicesStore.newBuilder()
                .addIgnoredDevices(
                    StoredIgnoredDevice.newBuilder()
                        .setOwnerUid("owner-a")
                        .setDeviceUid("device-a")
                        .build()
                )
                .build()

            val output = ByteArrayOutputStream()
            KnownDevicesSerializer.writeTo(expected, output)

            val actual = KnownDevicesSerializer.readFrom(
                ByteArrayInputStream(output.toByteArray())
            )

            assertEquals(expected, actual)
        }
    }

    @Test(expected = CorruptionException::class)
    fun `serializer rejects duplicate ignored records`() {
        runBlocking {
            val ignored = StoredIgnoredDevice.newBuilder()
                .setOwnerUid("owner-a")
                .setDeviceUid("device-a")
                .build()
            val invalid = KnownDevicesStore.newBuilder()
                .addIgnoredDevices(ignored)
                .addIgnoredDevices(ignored)
                .build()

            KnownDevicesSerializer.readFrom(
                ByteArrayInputStream(invalid.toByteArray())
            )
        }
    }

    @Test(expected = CorruptionException::class)
    fun `serializer rejects malformed protobuf bytes`() {
        runBlocking {
            KnownDevicesSerializer.readFrom(
                ByteArrayInputStream(byteArrayOf(0x0A, 0x7F, 0x01))
            )
        }
    }
}
