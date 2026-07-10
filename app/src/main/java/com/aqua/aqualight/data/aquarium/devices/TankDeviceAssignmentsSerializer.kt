package com.aqua.aqualight.data.aquarium.devices

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object TankDeviceAssignmentsSerializer : Serializer<TankDeviceAssignmentsStore> {

    override val defaultValue: TankDeviceAssignmentsStore =
        TankDeviceAssignmentsStore.getDefaultInstance()

    override suspend fun readFrom(
        input: InputStream
    ): TankDeviceAssignmentsStore {
        return try {
            TankDeviceAssignmentsStore.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                "Cannot read tank-device assignments proto.",
                exception
            )
        }
    }

    override suspend fun writeTo(
        t: TankDeviceAssignmentsStore,
        output: OutputStream
    ) {
        t.writeTo(output)
    }
}
