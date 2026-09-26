package com.aqua.aqualight.data.aquarium.health

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

internal object WaterAnalysesSerializer : Serializer<WaterAnalysesStore> {

    override val defaultValue: WaterAnalysesStore = WaterAnalysisStoreRules.defaultStore()

    override suspend fun readFrom(input: InputStream): WaterAnalysesStore {
        val parsed = try {
            WaterAnalysesStore.parseFrom(input)
        } catch (error: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read water analyses proto.", error)
        }

        return try {
            WaterAnalysisStoreRules.validateStore(parsed)
        } catch (error: StoreInvariantViolation) {
            throw CorruptionException(
                "Water analyses proto violates the commercial store contract.",
                error
            )
        }
    }

    override suspend fun writeTo(t: WaterAnalysesStore, output: OutputStream) {
        WaterAnalysisStoreRules.validateStore(t).writeTo(output)
    }
}
