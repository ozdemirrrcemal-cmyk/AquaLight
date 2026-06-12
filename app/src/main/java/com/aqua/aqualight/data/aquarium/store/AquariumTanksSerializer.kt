package com.aqua.aqualight.data.aquarium.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object AquariumTanksSerializer : Serializer<AquariumTanksStore> {

    override val defaultValue: AquariumTanksStore =
        AquariumTanksStore.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): AquariumTanksStore {
        return try {
            AquariumTanksStore.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read aquarium tanks proto.", exception)
        }
    }

    override suspend fun writeTo(
        t: AquariumTanksStore,
        output: OutputStream
    ) {
        t.writeTo(output)
    }
}