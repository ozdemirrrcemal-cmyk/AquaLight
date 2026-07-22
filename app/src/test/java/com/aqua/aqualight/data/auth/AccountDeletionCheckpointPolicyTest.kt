package com.aqua.aqualight.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionCheckpointPolicyTest {

    @Test
    fun everyCommercialStageAdvancesExactlyOnce() {
        val stages = AccountDeletionCheckpoint.Stage.entries

        stages.zipWithNext().forEach { (current, next) ->
            assertTrue(AccountDeletionCheckpointPolicy.canAdvance(current, next))
            assertTrue(AccountDeletionCheckpointPolicy.canAdvance(current, current))
        }
    }

    @Test
    fun skippingOrReversingAStageIsRejected() {
        assertFalse(
            AccountDeletionCheckpointPolicy.canAdvance(
                AccountDeletionCheckpoint.Stage.STARTED,
                AccountDeletionCheckpoint.Stage.AUTH_DELETE_REQUESTED
            )
        )
        assertFalse(
            AccountDeletionCheckpointPolicy.canAdvance(
                AccountDeletionCheckpoint.Stage.ACCOUNT_DELETED,
                AccountDeletionCheckpoint.Stage.CLOUD_CLEARED
            )
        )
    }
}
