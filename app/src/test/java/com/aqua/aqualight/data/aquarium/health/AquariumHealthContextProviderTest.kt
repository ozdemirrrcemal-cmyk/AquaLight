package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.LivestockCatalogItem
import com.aqua.aqualight.application.aquarium.LivestockCatalogOperations
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogFailure
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogOperations
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogResult
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContextResult
import com.aqua.aqualight.application.aquarium.health.context.HealthContextFailure
import com.aqua.aqualight.application.aquarium.health.context.HealthContextTimeBasis
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import com.aqua.aqualight.data.auth.OwnerSessionMutationBarrier
import com.aqua.aqualight.data.auth.OwnerSessionStateMachine
import com.aqua.aqualight.data.auth.OwnerSessionExpiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumHealthContextProviderTest {
    @Test
    fun `one tank read retains local identities and explicitly unavailable catalog facts`() = runBlocking {
        val fixture = Fixture()
        val result = fixture.provider().capture(7) as AquariumHealthContextResult.Available
        val context = result.context
        assertEquals(1, fixture.reads)
        assertEquals(54.0, context.geometricVolumeLitres)
        assertNull(context.setupDateEpochDay)
        assertEquals(HealthContextTimeBasis.CURRENT_AT_ENTRY, context.timeBasis)
        assertEquals(HealthEntityResolution.CATALOG_UNAVAILABLE, context.plants.single().resolution)
        assertEquals(11L, context.plants.single().plantId)
        assertEquals("plant:missing", context.plants.single().catalogId)
        assertEquals(HealthEntityResolution.CATALOG_UNAVAILABLE, context.livestock.single().resolution)
        assertEquals(3, context.livestock.single().quantity)
        assertTrue(runCatching { (context.plants as MutableList).clear() }.isFailure)
    }

    @Test
    fun `unknown taxonomy and missing dimensions do not become freshwater or net volume`() = runBlocking {
        val fixture = Fixture()
        fixture.tank = fixture.tank.copy(tankType = "Unknown", widthCm = 0)
        val context = (fixture.provider().capture(7) as AquariumHealthContextResult.Available).context
        assertNull(context.waterEnvironment)
        assertNull(context.geometricVolumeLitres)
    }

    @Test
    fun `content revision ignores capture time and changes with actual tank facts`() = runBlocking {
        val fixture = Fixture()
        val provider = fixture.provider()
        val first = (provider.capture(7) as AquariumHealthContextResult.Available).context
        fixture.now += 1000
        val second = (provider.capture(7) as AquariumHealthContextResult.Available).context
        assertEquals(first.revision, second.revision)
        fixture.tank = fixture.tank.copy(tankType = "Marine Fish")
        val third = (provider.capture(7) as AquariumHealthContextResult.Available).context
        assertTrue(first.revision != third.revision)
    }

    @Test
    fun `owner switch during capture is cancellation and never returns stale context`() = runBlocking {
        val fixture = Fixture()
        val provider = fixture.provider { fixture.state.close() }
        assertTrue(runCatching { provider.capture(7) }.exceptionOrNull() is OwnerSessionExpiredException)
        val missing = Fixture()
        assertTrue(runCatching { missing.provider { missing.state.close() }.capture(99) }
            .exceptionOrNull() is OwnerSessionExpiredException)
    }

    @Test
    fun `duplicate local identities and missing tank remain typed`() = runBlocking {
        val fixture = Fixture()
        assertEquals(AquariumHealthContextResult.TankMissing, fixture.provider().capture(99))
        fixture.tank = fixture.tank.copy(plants = fixture.tank.plants + fixture.tank.plants)
        assertEquals(AquariumHealthContextResult.Unavailable(HealthContextFailure.INVALID_TANK),
            fixture.provider().capture(7))
        val cancelled = fixture.provider { throw CancellationException() }
        assertTrue(runCatching { cancelled.capture(7) }.exceptionOrNull() is CancellationException)
    }

    private class Fixture {
        val state = OwnerSessionStateMachine()
        private val transition = state.begin("context-owner").also { check(state.commit(it)) }
        private val lease = OwnerSessionMutationBarrier(state).bind("context-owner", transition.generation)
        var now = 1_800_000_000_000L
        var reads = 0
        var tank = AquariumTankSnapshot(7, "Tank", "", null, null, 60, 30, 30, "cm", "L", "Freshwater Fish",
            "", now, false, false, listOf(AquariumPlantTag(11, "plant:missing", "Plant", "")), emptyList(),
            listOf(AquariumLivestock(12, "Animal", quantity = 3, catalogEntryId = "missing")))

        fun provider(onRead: () -> Unit = {}) = DefaultAquariumHealthContextProvider(
            loadTank = { id -> reads++; onRead(); tank.takeIf { it.id == id } },
            plants = PlantCareCatalogOperations {
                PlantCareCatalogResult.Unavailable(PlantCareCatalogFailure.UNREADABLE)
            },
            livestock = object : LivestockCatalogOperations {
                override fun entries(): List<LivestockCatalogItem> = emptyList()
                override fun findById(entryId: String): LivestockCatalogItem? = null
            },
            session = lease,
            clock = { now }
        )
    }
}
