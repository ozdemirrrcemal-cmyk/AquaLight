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
    DefaultWaterAnalysisEvaluationPreparation(DefaultAquariumHealthContextProvider(
        loadTank = { tankId ->
            tanks.tanksSnapshotForOwner(session.ownerUid).firstOrNull { it.id == tankId }?.toApplicationSnapshot()
        },
        plants = catalogs.plants,
        livestock = catalogs.livestock,
        session = session
    )))
