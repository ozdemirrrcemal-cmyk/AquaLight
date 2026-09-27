package com.aqua.aqualight.data.aquarium.catalog.plant

import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogFailure
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogResult
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareLookup
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantCareCatalogTest {
    @Test
    fun `packaged facts retain identities readiness and independent content revision`() {
        val snapshot = PlantCatalogParser.parse(asset())
        assertEquals(271, snapshot.records.size)
        assertEquals(182, snapshot.records.count { it.care.healthAnalysisReady })
        assertEquals(AquariumPlantLightCatalog.catalogIds, snapshot.records.map { it.id }.toSet())
        assertEquals("plant-care-2026-09-27.1", snapshot.revision)
        assertEquals(PlantCatalogParser.CONTENT_SHA256, snapshot.contentSha256)
        assertTrue(snapshot.find("plant:anubias_barteri_var_nana") is PlantCareLookup.Found)
        assertTrue(snapshot.find("plant:removed") is PlantCareLookup.Missing)
        assertTrue(runCatching { PlantCatalogParser.parse(asset().replace("Acmella repens", "Changed")) }.isFailure)
    }

    @Test
    fun `concurrent and repeated callers share one immutable snapshot off caller thread`() = runBlocking {
        val reads = AtomicInteger()
        val caller = Thread.currentThread()
        val operations = DefaultPlantCareCatalogOperations(readAsset = {
            assertFalse(caller === Thread.currentThread())
            reads.incrementAndGet()
            asset()
        })
        val snapshots = List(24) {
            async(Dispatchers.Default) { (operations.snapshot() as PlantCareCatalogResult.Available).snapshot }
        }.awaitAll()
        val first = snapshots.first()
        snapshots.forEach { assertSame(first, it) }
        assertSame(first, (operations.snapshot() as PlantCareCatalogResult.Available).snapshot)
        assertEquals(1, reads.get())
        assertTrue(runCatching { (first.records as MutableList).clear() }.isFailure)
        assertTrue(runCatching { (first.records.first().care.verifiedCareFields as MutableSet).clear() }.isFailure)
    }

    @Test
    fun `read and parse failures stay typed and retryable`() = runBlocking {
        var attempt = 0
        val operations = DefaultPlantCareCatalogOperations(readAsset = {
            when (++attempt) {
                1 -> throw IOException("Unavailable asset")
                2 -> "{broken"
                else -> asset()
            }
        })
        assertEquals(
            PlantCareCatalogResult.Unavailable(PlantCareCatalogFailure.UNREADABLE), operations.snapshot()
        )
        assertEquals(
            PlantCareCatalogResult.Unavailable(PlantCareCatalogFailure.INVALID_CONTENT), operations.snapshot()
        )
        assertTrue(operations.snapshot() is PlantCareCatalogResult.Available)
        assertEquals(3, attempt)
    }

    @Test
    fun `cancellation is never converted to unavailable`() = runBlocking {
        val operations = DefaultPlantCareCatalogOperations(readAsset = { throw CancellationException() })
        assertTrue(runCatching { operations.snapshot() }.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `invalid ranges verified fields and readiness are rejected before promotion`() {
        val original = JsonParser.parseString(asset()).asJsonObject.getAsJsonArray("records")[0].asJsonObject
        listOf<(com.google.gson.JsonObject) -> Unit>(
            { it.addProperty("temperatureMinC", 100.0) },
            { it.addProperty("temperatureMaxC", Double.POSITIVE_INFINITY) },
            { it.getAsJsonArray("verifiedCareFields").add("unknownField") },
            { it.addProperty("lightRequirement", "UNKNOWN") },
            { it.addProperty("healthAnalysisReady", true) },
            { it.addProperty("recordId", "") }
        ).forEach { mutate ->
            val row = original.deepCopy()
            mutate(row)
            assertTrue(runCatching { PlantCatalogValidation.validate(row) }.isFailure)
        }
        PlantCatalogValidation.validate(original)
    }

    private fun asset(): String {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() || File(it, "settings.gradle").exists() }
        return File(root, "app/src/main/assets/aqualight_plant_catalog.json").readText()
    }
}
