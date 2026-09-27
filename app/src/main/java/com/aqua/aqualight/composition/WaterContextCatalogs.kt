package com.aqua.aqualight.composition

import com.aqua.aqualight.application.aquarium.LivestockCatalogOperations
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogOperations
import com.aqua.aqualight.data.aquarium.health.DefaultAquariumHealthContextProvider
import com.aqua.aqualight.data.aquarium.health.DefaultWaterAnalysisEvaluationPreparation
import com.aqua.aqualight.data.aquarium.health.DefaultWaterAnalysisOperations
import com.aqua.aqualight.data.aquarium.health.WaterAnalysisDataStoreManager
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.aquarium.toApplicationSnapshot
import com.aqua.aqualight.data.auth.OwnerSessionWriteLease
import android.content.Context
import com.aqua.aqualight.data.aquarium.health.observation.DefaultHealthObservationOperations
import com.aqua.aqualight.data.aquarium.health.observation.HealthObservationWriteAccess

internal data class WaterContextCatalogs(
    val plants: PlantCareCatalogOperations,
    val livestock: LivestockCatalogOperations
)

internal fun createWaterAnalysisOperations(
    store: WaterAnalysisDataStoreManager,
    tanks: AquariumTankDataStoreManager,
    session: OwnerSessionWriteLease,
    catalogs: WaterContextCatalogs
): DefaultWaterAnalysisOperations = DefaultWaterAnalysisOperations(store, session,
    DefaultWaterAnalysisEvaluationPreparation(createHealthContext(tanks, session, catalogs)))

internal fun createHealthObservationOperations(context: Context, graph: OwnerDependencyGraph,
    catalogs: WaterContextCatalogs): DefaultHealthObservationOperations = createHealthObservationOperations(
    context, graph.waterAnalysisStore, graph.aquariumTankStore, graph.waterAnalysisSession, catalogs)

internal fun createHealthObservationOperations(context: Context, store: WaterAnalysisDataStoreManager,
    tanks: AquariumTankDataStoreManager, session: OwnerSessionWriteLease,
    catalogs: WaterContextCatalogs): DefaultHealthObservationOperations = DefaultHealthObservationOperations(
    HealthObservationWriteAccess(context, session, tanks), createHealthContext(tanks, session, catalogs),
    createWaterAnalysisOperations(store, tanks, session, catalogs)
)

private fun createHealthContext(tanks: AquariumTankDataStoreManager, session: OwnerSessionWriteLease,
    catalogs: WaterContextCatalogs) = DefaultAquariumHealthContextProvider(
        loadTank = { tankId ->
            tanks.tanksSnapshotForOwner(session.ownerUid).firstOrNull { it.id == tankId }?.toApplicationSnapshot()
        },
        plants = catalogs.plants,
        livestock = catalogs.livestock,
        session = session
    )
