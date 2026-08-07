package com.aqua.aqualight.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerBackgroundLeaseRegistryTest {

    @Test
    fun finalBackgroundLeaseAloneOwnsRuntimeClosure() {
        val registry = OwnerBackgroundLeaseRegistry()
        val first = registry.markCreated(OWNER_UID, GENERATION)
        val second = registry.acquireExisting(OWNER_UID, GENERATION)

        assertFalse(registry.release(first))
        assertTrue(registry.release(second))
    }

    @Test
    fun existingForegroundRuntimeIsBorrowedAndNeverClosedByBackgroundRelease() {
        val registry = OwnerBackgroundLeaseRegistry()

        val lease = registry.acquireExisting(OWNER_UID, GENERATION)

        assertFalse(lease.backgroundOwned)
        assertFalse(registry.release(lease))
    }

    @Test
    fun foregroundPromotionRevokesBackgroundClosureAuthority() {
        val registry = OwnerBackgroundLeaseRegistry()
        val first = registry.markCreated(OWNER_UID, GENERATION)
        val second = registry.acquireExisting(OWNER_UID, GENERATION)

        assertTrue(registry.promote(OWNER_UID, GENERATION))
        assertFalse(registry.release(first))
        assertFalse(registry.release(second))
    }

    @Test
    fun staleGenerationCannotCloseNewOwnerRuntime() {
        val registry = OwnerBackgroundLeaseRegistry()
        val stale = registry.markCreated(OWNER_UID, GENERATION)
        registry.clear(OWNER_UID)
        registry.markCreated(OWNER_UID, NEXT_GENERATION)

        assertFalse(registry.release(stale))
        assertTrue(registry.isOwned(OWNER_UID, NEXT_GENERATION))
    }

    private companion object {
        const val OWNER_UID = "owner-a"
        const val GENERATION = 7L
        const val NEXT_GENERATION = 8L
    }
}
