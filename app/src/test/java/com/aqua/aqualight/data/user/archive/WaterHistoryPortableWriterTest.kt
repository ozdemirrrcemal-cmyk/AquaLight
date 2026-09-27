package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.health.StoredWaterAnalysis
import com.aqua.aqualight.data.aquarium.health.StoredWaterMeasurement
import com.google.gson.JsonParser
import java.io.File
import java.io.StringWriter
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterHistoryPortableWriterTest {
    @Test
    fun `portable JSON exposes raw zero absent temperature source semantics and event timestamps`() {
        val file = File(Files.createTempDirectory("portable-water").toFile(), "history.bin")
        val record = StoredWaterAnalysis.newBuilder().setOwnerUid("owner").setId(9).setTankId(7)
            .setMeasuredAtMillis(TIME).setCreatedAtMillis(TIME + 10)
            .addMeasurements(StoredWaterMeasurement.newBuilder().setParameter("NITRATE").setValue(0.0)
                .setMethod("TEST_KIT").setTestKitId("other").setBasis("NO3_N").setUnit("MG_L")).build()
        val history = WaterHistoryArchive.write(sequenceOf(record), 1, file)
        val writer = StringWriter()
        WaterHistoryPortableWriter.write(export(), writer, history to file)
        val document = JsonParser.parseString(writer.toString()).asJsonObject
        val event = document.getAsJsonArray("waterAnalyses").single().asJsonObject
        assertEquals(9L, event.get("analysisId").asLong)
        assertEquals(TIME, event.get("observedAtMillis").asLong)
        assertEquals(TIME + 10, event.get("createdAtMillis").asLong)
        assertTrue(event.get("temperatureCelsius").isJsonNull)
        assertTrue(event.get("evaluation").isJsonNull)
        val measurement = event.getAsJsonArray("measurements").single().asJsonObject
        assertEquals(0.0, measurement.get("value").asDouble, 0.0)
        assertEquals("NO3_N", measurement.get("basis").asString)
        assertEquals("TEST_KIT", measurement.get("method").asString)
        assertEquals(3, document.get("schemaVersion").asInt)
    }

    private fun export(): PortableUserDataExport {
        val backup = RestoreFixture.backup().manifest
        return PortableUserDataExport(USER_DATA_EXPORT_FORMAT, USER_DATA_EXPORT_SCHEMA_VERSION, TIME, "test",
            PortableAccountData("owner", "", "", "", "", "", "", "", "", "", "", false),
            PortableAppPreferences("system", "tr", true, true, false),
            PortableUsageData(0, 0, 0, 0, 0, ""),
            PortableAquariumData(backup.aquariums, backup.careTasks, backup.deviceAssignments, 0))
    }

    private companion object { const val TIME = 1_800_000_000_000L }
}
