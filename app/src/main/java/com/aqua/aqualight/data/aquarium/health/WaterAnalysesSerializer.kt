package com.aqua.aqualight.data.aquarium.health

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

internal object WaterAnalysesSerializer : Serializer<WaterAnalysesStore> {

    override val defaultValue: WaterAnalysesStore = WaterAnalysisStoreRules.defaultStore()

    override suspend fun readFrom(input: InputStream): WaterAnalysesStore =
        WaterAnalysisLegacyReader.readFrom(input)

    override suspend fun writeTo(t: WaterAnalysesStore, output: OutputStream) {
        WaterAnalysisStoreRules.validateStore(t).writeTo(output)
    }
}
