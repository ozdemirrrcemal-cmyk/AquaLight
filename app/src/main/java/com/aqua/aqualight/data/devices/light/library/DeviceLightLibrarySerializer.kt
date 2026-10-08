package com.aqua.aqualight.data.devices.light.library

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

internal object DeviceLightLibrarySerializer : Serializer<DeviceLightLibraryStoreData> {
    override val defaultValue: DeviceLightLibraryStoreData =
        DeviceLightLibraryStoreRules.defaultStore()

    override suspend fun readFrom(input: InputStream): DeviceLightLibraryStoreData {
        val parsed = try {
            DeviceLightLibraryStoreData.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read light-library proto.", exception)
        }
        return try {
            DeviceLightLibraryStoreRules.validateStore(parsed)
        } catch (exception: StoreInvariantViolation) {
            throw CorruptionException(
                "Light-library proto violates the commercial store contract.",
                exception
            )
        }
    }

    override suspend fun writeTo(
        t: DeviceLightLibraryStoreData,
        output: OutputStream
    ) {
        DeviceLightLibraryStoreRules.validateStore(t).writeTo(output)
    }
}
