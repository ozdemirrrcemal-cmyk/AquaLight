package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.health.room.WaterDeletionStageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WaterDeletionStageDigestTest {
    @Test
    fun framingDistinguishesSplitPayloadsAndTracksExactCountAndOrder() {
        val first = digest("ab", "c")
        assertEquals(2L, first.count)
        assertNotEquals(first.finish(), digest("a", "bc").finish())
        assertNotEquals(digest("ab", "c").finish(), digest("c", "ab").finish())
        assertNotEquals(digest("ab", "c").finish(), digest("abc").finish())
        assertEquals(digest("ab", "c").finish(), digest("ab", "c").finish())
    }

    @Test
    fun emptyStagesStillBindOwnerTankAndTransaction() {
        val original = WaterDeletionStageDigest("owner", 2L, "transaction").finish()
        assertNotEquals(original, WaterDeletionStageDigest("foreign", 2L, "transaction").finish())
        assertNotEquals(original, WaterDeletionStageDigest("owner", 3L, "transaction").finish())
        assertNotEquals(original, WaterDeletionStageDigest("owner", 2L, "other").finish())
        assertEquals(0L, WaterDeletionStageDigest("owner", 2L, "transaction").count)
    }

    private fun digest(vararg payloads: String) = WaterDeletionStageDigest("owner", 2L, "transaction").apply {
        payloads.forEachIndexed { index, payload ->
            add(WaterDeletionStageEntity("owner", 2L, index.toLong() + 1L, payload.toByteArray()))
        }
    }
}
