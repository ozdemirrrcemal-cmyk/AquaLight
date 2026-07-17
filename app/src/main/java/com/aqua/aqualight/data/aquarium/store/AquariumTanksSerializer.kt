package com.aqua.aqualight.data.aquarium.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object AquariumTanksSerializer : Serializer<AquariumTanksStore> {

    override val defaultValue: AquariumTanksStore = TankStoreRules.defaultStore()

    override suspend fun readFrom(input: InputStream): AquariumTanksStore {
        val parsed = try {
            AquariumTanksStore.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                "Cannot read aquarium tanks proto.",
                exception
            )
        }

        return try {
            TankStoreRules.validateStore(parsed)
        } catch (exception: StoreInvariantViolation) {
            throw CorruptionException(
                "Aquarium tanks proto violates the commercial store contract.",
                exception
            )
        }
    }

    override suspend fun writeTo(
        t: AquariumTanksStore,
        output: OutputStream
    ) {
        TankStoreRules.validateStore(t).writeTo(output)
    }
}
