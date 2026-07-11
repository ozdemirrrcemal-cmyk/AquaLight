package com.aqua.aqualight.data.devices.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object KnownDevicesSerializer : Serializer<KnownDevicesStore> {

    override val defaultValue: KnownDevicesStore =
        KnownDevicesStore.getDefaultInstance()

    override suspend fun readFrom(
        input: InputStream
    ): KnownDevicesStore {
        val store = try {
            KnownDevicesStore.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                "Cannot read known devices proto.",
                exception
            )
        }

        try {
            KnownDevicesStoreReducer.validate(store)
        } catch (exception: KnownDevicesValidationException) {
            throw CorruptionException(
                "Known devices proto violates storage invariants.",
                exception
            )
        }

        return store
    }

    override suspend fun writeTo(
        t: KnownDevicesStore,
        output: OutputStream
    ) {
        try {
            KnownDevicesStoreReducer.validate(t)
        } catch (exception: KnownDevicesValidationException) {
            throw CorruptionException(
                "Refusing to write invalid known devices.",
                exception
            )
        }

        t.writeTo(output)
    }
}
