package com.aqua.aqualight.data.aquarium.health

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object AquariumHealthCommercialSerializer : Serializer<AquariumHealthStore> {

    override val defaultValue: AquariumHealthStore =
        AquariumHealthStoreRules.defaultStore()

    override suspend fun readFrom(input: InputStream): AquariumHealthStore {
        val parsed = try {
            AquariumHealthStore.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                "Cannot read aquarium health proto.",
                exception
            )
        }

        return try {
            AquariumHealthStoreRules.validateStore(parsed)
        } catch (exception: StoreInvariantViolation) {
            throw CorruptionException(
                "Aquarium health proto violates the commercial store contract.",
                exception
            )
        }
    }

    override suspend fun writeTo(
        t: AquariumHealthStore,
        output: OutputStream
    ) {
        AquariumHealthStoreRules.validateStore(t).writeTo(output)
    }
}
