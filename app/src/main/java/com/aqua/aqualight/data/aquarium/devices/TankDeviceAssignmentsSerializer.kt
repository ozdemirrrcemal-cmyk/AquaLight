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
        val store = try {
            TankDeviceAssignmentsStore.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                "Cannot read tank device assignments proto.",
                exception
            )
        }

        try {
            TankDeviceAssignmentRules.validate(store)
        } catch (exception: TankDeviceAssignmentsValidationException) {
            throw CorruptionException(
                "Tank device assignments proto violates storage invariants.",
                exception
            )
        }

        return store
    }

    override suspend fun writeTo(
        t: TankDeviceAssignmentsStore,
        output: OutputStream
    ) {
        try {
            TankDeviceAssignmentRules.validate(t)
        } catch (exception: TankDeviceAssignmentsValidationException) {
            throw CorruptionException(
                "Refusing to write invalid tank device assignments.",
                exception
            )
        }

        t.writeTo(output)
    }
}
