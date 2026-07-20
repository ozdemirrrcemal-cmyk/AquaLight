package com.aqua.aqualight.data.care

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityWriteGuard
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/** Strict serializer for the first commercial Care Tasks store schema. */
object CareTasksCommercialSerializer : Serializer<CareTasksStore> {

    override val defaultValue: CareTasksStore = CareTaskStoreRules.defaultStore()

    override suspend fun readFrom(input: InputStream): CareTasksStore {
        val parsed = try {
            CareTasksStore.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                "Cannot read care tasks proto.",
                exception
            )
        }

        return try {
            CareTaskStoreRules.validateStore(parsed)
        } catch (exception: StoreInvariantViolation) {
            throw CorruptionException(
                "Care tasks proto violates the commercial store contract.",
                exception
            )
        }
    }

    override suspend fun writeTo(
        t: CareTasksStore,
        output: OutputStream
    ) {
        val validated = CareTaskStoreRules.validateStore(t)
        TankCareIntegrityWriteGuard.requireValidWrite(validated.tasksList)
        validated.writeTo(output)
    }
}
