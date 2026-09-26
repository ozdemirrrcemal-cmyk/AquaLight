package com.aqua.aqualight.data.aquarium.health

import androidx.datastore.core.CorruptionException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WaterAnalysesSerializerTest {

    @Test
    fun legacySchemaIsReadWithItsEntriesIntact() = runBlocking {
        val legacy = WaterAnalysesStore.newBuilder()
            .setSchemaVersion(1)
            .build()
        val bytes = ByteArrayOutputStream().also { output -> legacy.writeTo(output) }
            .toByteArray()

        val migrated = WaterAnalysesSerializer.readFrom(ByteArrayInputStream(bytes))

        assertEquals(2, migrated.schemaVersion)
        assertEquals(legacy.analysesList, migrated.analysesList)
    }

    @Test
    fun unsupportedSchemaReturnsErrorInsteadOfAnEmptyStore() {
        val bytes = WaterAnalysesStore.newBuilder()
            .setSchemaVersion(99)
            .build()
            .toByteArray()

        assertThrows(CorruptionException::class.java) {
            runBlocking { WaterAnalysesSerializer.readFrom(ByteArrayInputStream(bytes)) }
        }
    }
}
