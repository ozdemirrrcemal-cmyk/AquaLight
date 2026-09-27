package com.aqua.aqualight.application.aquarium.catalog.plant

import com.aqua.aqualight.application.aquarium.AquariumPlantCatalogRecord
import java.util.Collections

fun interface PlantCareCatalogOperations {
    suspend fun snapshot(): PlantCareCatalogResult
}

sealed interface PlantCareCatalogResult {
    data class Available(val snapshot: PlantCareCatalogSnapshot) : PlantCareCatalogResult
    data class Unavailable(val reason: PlantCareCatalogFailure) : PlantCareCatalogResult
}

enum class PlantCareCatalogFailure { UNREADABLE, INVALID_CONTENT }

sealed interface PlantCareLookup {
    data class Found(val record: AquariumPlantCatalogRecord) : PlantCareLookup
    data class Missing(val catalogId: String) : PlantCareLookup
}

/** One deeply immutable identity/care revision shared by picker and assessment context. */
class PlantCareCatalogSnapshot(
    val revision: String,
    val contentSha256: String,
    records: List<AquariumPlantCatalogRecord>
) {
    val records: List<AquariumPlantCatalogRecord> = Collections.unmodifiableList(records.map { record ->
        record.copy(
            searchNames = immutableSet(record.searchNames),
            placement = immutableSet(record.placement),
            catalogFlags = immutableSet(record.catalogFlags),
            care = record.care.copy(verifiedCareFields = immutableSet(record.care.verifiedCareFields))
        )
    })
    private val byId = this.records.associateBy(AquariumPlantCatalogRecord::id)

    init {
        require(revision.isNotBlank())
        require(contentSha256.matches(Regex("[0-9a-f]{64}")))
        require(byId.size == records.size)
    }

    fun find(catalogId: String): PlantCareLookup = byId[catalogId]?.let(PlantCareLookup::Found)
        ?: PlantCareLookup.Missing(catalogId)
}

private fun <T> immutableSet(values: Set<T>): Set<T> = Collections.unmodifiableSet(LinkedHashSet(values))
