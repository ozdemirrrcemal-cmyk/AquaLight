package com.aqua.aqualight.application.aquarium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumLivestockIdentityTest {

    @Test
    fun customIdentityIsExplicitAndBoundToLivestockId() {
        val identity = AquariumLivestockIdentity.custom(42L)

        assertEquals("custom:42", identity)
        assertTrue(AquariumLivestockIdentity.isCustom(identity))
        AquariumLivestockIdentity.requireValid(42L, identity)
    }

    @Test
    fun blankAndMismatchedCustomIdentitiesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AquariumLivestockIdentity.requireValid(42L, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AquariumLivestockIdentity.requireValid(42L, "custom:41")
        }
    }

    @Test
    fun catalogIdentityIsNotTreatedAsCustom() {
        assertFalse(AquariumLivestockIdentity.isCustom("b9450a4fded15fe2"))
        AquariumLivestockIdentity.requireValid(42L, "b9450a4fded15fe2")
    }
}
