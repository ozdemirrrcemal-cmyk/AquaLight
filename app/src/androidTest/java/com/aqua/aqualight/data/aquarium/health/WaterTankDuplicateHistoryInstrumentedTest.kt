package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementInput
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSelection
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationKind
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationQuery
import com.aqua.aqualight.composition.WaterContextCatalogs
import com.aqua.aqualight.composition.createHealthObservationOperations
import com.aqua.aqualight.composition.createWaterAnalysisOperations
import com.aqua.aqualight.data.aquarium.catalog.livestock.DefaultLivestockCatalogOperations
import com.aqua.aqualight.data.aquarium.catalog.plant.DefaultPlantCareCatalogOperations
import com.aqua.aqualight.data.aquarium.health.observation.HealthRoomFixture
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.OwnerSessionTestFixture
import com.aqua.aqualight.data.user.UserDataScope
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterTankDuplicateHistoryInstrumentedTest {
    @Test
    fun duplicateKeepsBothHistoriesOnlyOnOriginalTankEvenWhenPlantIdentityIsCopied() = runBlocking {
        withTimeout(30_000L) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val owner = "duplicate-history-${UUID.randomUUID()}"
            val session = OwnerSessionTestFixture(owner)
            val tanks = AquariumTankDataStoreManager(context)
            val store = WaterAnalysisDataStoreManager(context)
            val catalogs = WaterContextCatalogs(DefaultPlantCareCatalogOperations.create(context),
                DefaultLivestockCatalogOperations(context))
            val water = createWaterAnalysisOperations(store, tanks, session.lease, catalogs)
            val health = createHealthObservationOperations(context, store, tanks, session.lease, catalogs)
            try {
                UserDataScope.withOwnerUid(owner) {
                    val original = tanks.addTankFromDraft(TankDraft(name = "Original history",
                        widthCm = 60, lengthCm = 40, heightCm = 40, tankType = "Planted",
                        plants = listOf(TankPlantTag(3L, "duplicate-test-plant", "Original plant", "Foreground"))))
                    val waterId = water.saveAnalysis(waterInput(original))
                    val healthId = health.save(HealthRoomFixture.prepared(tank = original).input)
                    val savedHealth = checkNotNull(health.observation(original, healthId).first())
                    val copied = tanks.duplicateTank(original)
                    assertNotEquals(original, copied)
                    assertEquals(3L, tanks.tanksSnapshotForOwner(owner).single { it.id == copied }.plants.single().id)
                    assertNull(water.latestAnalysis(copied).first())
                    assertNull(water.analysis(copied, waterId).first())
                    assertTrue(water.historyPage(copied).first().records.isEmpty())
                    assertNull(health.observation(copied, healthId).first())
                    val query = HealthObservationQuery(copied, HealthObservationKind.PLANT, subjectId = 3L)
                    assertTrue(health.history(query).first().records.isEmpty())
                    assertEquals(0L, health.history(query).first().totalCount)
                    assertEquals(waterId, water.latestAnalysis(original).first()?.id)
                    assertEquals(savedHealth, health.observation(original, healthId).first())
                }
            } finally {
                withContext(NonCancellable) {
                    session.close()
                    UserDataScope.withOwnerUid(owner) {
                        store.clearAllAnalyses(owner)
                        tanks.clearAllTanks(owner)
                    }
                }
            }
        }
    }

    private fun waterInput(tankId: Long) = WaterAnalysisInput(tankId, System.currentTimeMillis(), null, null,
        listOf(WaterMeasurementInput(WaterParameter.PH, 7.0,
            WaterMeasurementSelection(WaterMeasurementMethod.MANUAL, null,
                WaterMeasurementBasis.PH, WaterMeasurementUnit.NONE))))
}
