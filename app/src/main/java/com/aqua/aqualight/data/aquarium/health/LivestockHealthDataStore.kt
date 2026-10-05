package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

private object LivestockHealthSerializer : Serializer<LivestockHealthStore> {
    override val defaultValue: LivestockHealthStore = LivestockHealthStore.newBuilder()
        .setSchemaVersion(CommercialStoreSchema.LIVESTOCK_HEALTH_VERSION)
        .build()

    override suspend fun readFrom(input: InputStream): LivestockHealthStore = try {
        validateLivestockHealthStore(LivestockHealthStore.parseFrom(input))
    } catch (error: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read livestock health records.", error)
    } catch (error: StoreInvariantViolation) {
        throw CorruptionException("Invalid livestock health records.", error)
    } catch (error: IllegalArgumentException) {
        throw CorruptionException("Invalid livestock health fields.", error)
    }

    override suspend fun writeTo(t: LivestockHealthStore, output: OutputStream) {
        validateLivestockHealthStore(t).writeTo(output)
    }
}

internal val Context.livestockHealthDataStore: DataStore<LivestockHealthStore> by dataStore(
    fileName = "livestock_health.pb",
    serializer = LivestockHealthSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(LocalDataRecoveryTracker.Area.LIVESTOCK_HEALTH)
        LivestockHealthSerializer.defaultValue
    }
)
