package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterHistoryCursor
import com.aqua.aqualight.application.aquarium.health.WaterHistoryPage

/** Transitional projection. Only Room cutover removes the legacy whole-file read. */
internal object WaterAnalysisProtoPages {
    fun page(records: List<WaterAnalysisRecord>, tankId: Long,
        cursor: WaterHistoryCursor?, newer: Boolean): WaterHistoryPage {
        require(tankId > 0L && (cursor == null || cursor.tankId == tankId))
        require(cursor != null || !newer)
        require(records.all { it.tankId == tankId })
        val ordered = WaterAnalysisIdentityRules.newestFirst(records)
        val matching = if (cursor == null) ordered else ordered.filter {
            val comparison = compare(it, cursor)
            if (newer) comparison > 0 else comparison < 0
        }
        val selected = if (newer) matching.takeLast(WaterHistoryPage.PAGE_SIZE)
            else matching.take(WaterHistoryPage.PAGE_SIZE)
        // Deleting an entire visited page must not trap the route on an empty cursor.
        val rows = selected.ifEmpty { ordered.take(WaterHistoryPage.PAGE_SIZE) }
        val first = rows.firstOrNull()?.cursor()
        val last = rows.lastOrNull()?.cursor()
        return WaterHistoryPage(rows.map(WaterAnalysisRecord::toApplicationSnapshot), records.size.toLong(),
            last?.takeIf { key -> ordered.any { compare(it, key) < 0 } },
            first?.takeIf { key -> ordered.any { compare(it, key) > 0 } })
    }

    private fun WaterAnalysisRecord.cursor() = WaterHistoryCursor(tankId, measuredAtMillis, createdAtMillis, id)

    private fun compare(record: WaterAnalysisRecord, cursor: WaterHistoryCursor): Int =
        compareValuesBy(record.cursor(), cursor, WaterHistoryCursor::observedAtMillis,
            WaterHistoryCursor::createdAtMillis, WaterHistoryCursor::analysisId)
}
