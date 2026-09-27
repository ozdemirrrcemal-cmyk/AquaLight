package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterHistoryCursor
import com.aqua.aqualight.application.aquarium.health.WaterHistoryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterAnalysisProtoPagesTest {
    @Test
    fun `tied observations traverse every record in both directions without duplicates`() {
        val records = (1L..125L).map(::record)
        val first = page(records)
        val middle = page(records, first.next)
        val last = page(records, middle.next)
        assertEquals(listOf(50, 50, 25), listOf(first, middle, last).map { it.records.size })
        assertEquals((125L downTo 1L).toList(), (first.records + middle.records + last.records).map { it.id })
        assertEquals(middle.records, page(records, last.previous, newer = true).records)
        assertEquals(first.records, page(records, middle.previous, newer = true).records)
        assertNull(first.previous)
        assertNull(last.next)
        assertTrue(listOf(first, middle, last).all { it.totalCount == 125L })
    }

    @Test
    fun `deleted page and deleted anchor still allow returning to newer history`() {
        val records = (1L..75L).map(::record)
        val first = page(records)
        val oldPage = page(records, first.next)
        val retained = records.filter { it.id > 25L }
        val reset = page(retained, first.next)
        assertEquals(retained.asReversed().map { it.id }, reset.records.map { it.id })
        assertNull(reset.previous)
        val withoutAnchor = records.filter { it.id != oldPage.previous!!.analysisId }
        assertEquals(first.records, page(withoutAnchor, oldPage.previous, newer = true).records)
    }

    @Test
    fun `sample time wins over commit time and both win over identity`() {
        val rows = listOf(record(900).copy(measuredAtMillis = 99), record(8),
            record(7).copy(createdAtMillis = 101), record(6).copy(measuredAtMillis = 101))
        assertEquals(listOf(6L, 7L, 8L, 900L), page(rows).records.map { it.id })
    }

    @Test
    fun `cursor and records from another tank are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            page(listOf(record(1)), WaterHistoryCursor(99, 100, 100, 1))
        }
        assertThrows(IllegalArgumentException::class.java) { page(listOf(record(1).copy(tankId = 99))) }
        assertThrows(IllegalArgumentException::class.java) { page(emptyList(), newer = true) }
    }

    private fun page(rows: List<WaterAnalysisRecord>, cursor: WaterHistoryCursor? = null,
        newer: Boolean = false): WaterHistoryPage = WaterAnalysisProtoPages.page(rows, 2L, cursor, newer)

    private fun record(id: Long) = WaterAnalysisRecord(id, "owner", 2L, 100L, null, null, emptyList(), 100L)
}
