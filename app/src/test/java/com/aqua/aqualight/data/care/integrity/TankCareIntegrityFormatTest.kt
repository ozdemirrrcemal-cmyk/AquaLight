package com.aqua.aqualight.data.care.integrity

import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TankCareIntegrityFormatTest {
    @Test
    fun legacyAndUnreferencedEntriesDoNotInventAWaterRollbackSnapshot() {
        assertNull(TankCareIntegrityFormat.parse("v1|S|b3duZXI|7|").waterTransaction)
        assertNull(TankCareIntegrityFormat.parse("v2|S|b3duZXI|7||").waterTransaction)
        assertEquals("task-bytes", TankCareIntegrityFormat.parse("v1|S|b3duZXI|7|task-bytes").fields[4])
    }

    @Test
    fun currentEntryRetainsTheExactExternalTransactionReference() {
        val transaction = "18c8073b-5c59-4a5b-b74f-6f0ba6ae7a03"
        assertEquals(transaction, TankCareIntegrityFormat.parse("v2|S|b3duZXI|7||$transaction").waterTransaction)
    }

    @Test
    fun unknownTruncatedExtraOrNoncanonicalFieldsCannotBecomeLegacyEntries() {
        listOf("v3|S|b3duZXI|7||", "v2|S|b3duZXI|7|", "v1|S|b3duZXI|7|||",
            "v2|S|b3duZXI|7||not-a-transaction", "v2|S|b3duZXI|7||1-1-1-1-1",
            "v2|S|b3duZXI|7||18C8073B-5C59-4A5B-B74F-6F0BA6AE7A03").forEach { encoded ->
            assertThrows(StoreInvariantViolation::class.java) { TankCareIntegrityFormat.parse(encoded) }
        }
    }
}
