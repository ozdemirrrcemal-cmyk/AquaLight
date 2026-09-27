package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.application.aquarium.LivestockCatalogOperations
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogOperations
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogResult
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContextProvider
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContextResult
import com.aqua.aqualight.application.aquarium.health.context.HealthContextFailure
import com.aqua.aqualight.application.aquarium.health.context.HealthMaterialContext
import com.aqua.aqualight.application.aquarium.health.context.HealthTankFacts
import com.aqua.aqualight.application.aquarium.health.context.HealthCatalogRevisions
import com.aqua.aqualight.application.aquarium.health.context.HealthContextCapture
import com.aqua.aqualight.application.aquarium.health.context.HealthTankDimensions
import com.aqua.aqualight.application.aquarium.health.context.geometricVolumeLitres
import com.aqua.aqualight.data.auth.OwnerSessionWriteLease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The loader is bound to the committed graph's owner, never the mutable current-user singleton. */
internal class DefaultAquariumHealthContextProvider(
    private val loadTank: suspend (Long) -> AquariumTankSnapshot?,
    private val plants: PlantCareCatalogOperations,
    private val livestock: LivestockCatalogOperations,
    private val session: OwnerSessionWriteLease,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AquariumHealthContextProvider {
    override suspend fun capture(tankId: Long): AquariumHealthContextResult = withContext(dispatcher) {
        session.requireCurrent()
        val tank = try {
            loadTank(tankId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext current(AquariumHealthContextResult.Unavailable(HealthContextFailure.TANK_READ_FAILED))
        } ?: return@withContext current(AquariumHealthContextResult.TankMissing)
        if (tank.id != tankId || tankId <= 0) {
            return@withContext current(AquariumHealthContextResult.Unavailable(HealthContextFailure.INVALID_TANK))
        }
        val plantSnapshot = (plants.snapshot() as? PlantCareCatalogResult.Available)?.snapshot
        val livestockItems = try {
            livestock.entries().takeIf { it.isNotEmpty() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val entities = HealthContextEntityResolver.resolve(tank, plantSnapshot, livestockItems)
        val materials = tank.materials.sortedBy { it.id }.map {
            HealthMaterialContext(it.id, it.productId, it.categoryKey)
        }
        val context = try {
            AquariumHealthContext(
                HealthContextCapture(clock(), "pending"), HealthTankFacts(tank.id, tank.tankType,
                    AquariumTankTaxonomy.environmentForTankType(tank.tankType),
                    tank.setupDateEpochDay, tank.geometricVolumeLitres(),
                    HealthTankDimensions(tank.widthCm, tank.lengthCm, tank.heightCm)),
                HealthCatalogRevisions(plantSnapshot?.revision, entities.livestockRevision),
                entities.plants, entities.livestock, materials
            )
        } catch (_: IllegalArgumentException) {
            return@withContext current(AquariumHealthContextResult.Unavailable(HealthContextFailure.INVALID_TANK))
        }
        session.requireCurrent()
        AquariumHealthContextResult.Available(AquariumHealthContext(
            context.capture.copy(revision = WaterHealthContextDocument.contentRevision(context)),
            context.tank, context.catalogs, context.plants, context.livestock, context.materials
        ))
    }
    private fun current(result: AquariumHealthContextResult): AquariumHealthContextResult {
        session.requireCurrent()
        return result
    }
}
