package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

internal object PlantHealthSerializer : Serializer<PlantHealthStore> {
    override val defaultValue: PlantHealthStore = PlantHealthStore.newBuilder()
        .setSchemaVersion(CommercialStoreSchema.PLANT_HEALTH_VERSION).build()

    override suspend fun readFrom(input: InputStream): PlantHealthStore = try {
        validatePlantHealthStore(PlantHealthStore.parseFrom(input))
    } catch (error: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read plant observations.", error)
    } catch (error: IllegalArgumentException) {
        throw CorruptionException("Invalid plant observations.", error)
    }

    override suspend fun writeTo(t: PlantHealthStore, output: OutputStream) {
        validatePlantHealthStore(t).writeTo(output)
    }
}

internal val Context.plantHealthDataStore by dataStore(
    fileName = "plant_health.pb",
    serializer = PlantHealthSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(LocalDataRecoveryTracker.Area.PLANT_HEALTH)
        PlantHealthSerializer.defaultValue
    }
)
