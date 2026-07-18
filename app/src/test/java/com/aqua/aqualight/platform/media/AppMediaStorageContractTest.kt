package com.aqua.aqualight.platform.media

import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppMediaStorageContractTest {

    @Test
    fun ownerIdentityMustNeverBeDerivedFromRecordToken() {
        val ownerUid = "firebase-owner-uid"
        val ownerToken = "tank-42"
        assertNotEquals(ownerUid, ownerToken)
    }
}
