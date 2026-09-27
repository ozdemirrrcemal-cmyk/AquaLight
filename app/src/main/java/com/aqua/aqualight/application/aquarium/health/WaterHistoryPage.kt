package com.aqua.aqualight.application.aquarium.health

import java.util.Collections

/** Complete ordering key; a cursor cannot be reused for a different tank. */
data class WaterHistoryCursor(
    val tankId: Long,
    val observedAtMillis: Long,
    val createdAtMillis: Long,
    val analysisId: Long
) {
    init { require(tankId > 0L && observedAtMillis > 0L && createdAtMillis > 0L && analysisId > 0L) }
}

class WaterHistoryPage(
    records: List<WaterAnalysisSnapshot>,
    val totalCount: Long,
    val next: WaterHistoryCursor?,
    val previous: WaterHistoryCursor? = null
) {
    val records: List<WaterAnalysisSnapshot> = Collections.unmodifiableList(records.toList())

    init { require(records.size <= PAGE_SIZE && totalCount >= records.size) }

    companion object { const val PAGE_SIZE = 50 }
}
