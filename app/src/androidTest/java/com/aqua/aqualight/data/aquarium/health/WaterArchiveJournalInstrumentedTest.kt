package com.aqua.aqualight.data.aquarium.health

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.user.archive.UserDataRestoreJournal
import com.aqua.aqualight.data.user.archive.UserDataRestoreMetadataFiles
import com.aqua.aqualight.data.user.archive.UserDataRestoreTransactionState
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterArchiveJournalInstrumentedTest {
    @Test
    fun waterTransactionIdentitySurvivesJournalReopenAndCommit() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val owner = "archive-journal-${UUID.randomUUID()}"
        val journal = UserDataRestoreJournal(context)
        try {
            journal.begin(owner, emptySet())
            val transaction = requireNotNull(journal.pending(owner)).waterTransactionId
            assertNotNull(transaction)
            assertEquals(transaction, UserDataRestoreJournal(context).pending(owner)?.waterTransactionId)
            journal.markCommitted(owner)
            val reopened = requireNotNull(UserDataRestoreJournal(context).pending(owner))
            assertEquals(UserDataRestoreTransactionState.COMMITTED, reopened.state)
            assertEquals(transaction, reopened.waterTransactionId)
        } finally { journal.clearOwner(owner) }
    }

    @Test
    fun legacyJournalRemainsReadableAfterCommittedRewrite() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val owner = "archive-legacy-${UUID.randomUUID()}"
        val files = UserDataRestoreMetadataFiles(context, "journal")
        val journal = UserDataRestoreJournal(context)
        try {
            files.write(owner, """{"version":2,"ownerUid":"$owner","state":"ACTIVE",
                "createdTanks":[],"createdTasks":[],"createdAssignments":[]}""")
            assertNull(requireNotNull(journal.pending(owner)).waterTransactionId)
            journal.markCommitted(owner)
            val reopened = requireNotNull(UserDataRestoreJournal(context).pending(owner))
            assertEquals(UserDataRestoreTransactionState.COMMITTED, reopened.state)
            assertNull(reopened.waterTransactionId)
        } finally { journal.clearOwner(owner) }
    }

    @Test
    fun currentJournalWithMissingWaterIdentityIsRejectedWithoutDiscardingEvidence() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val owner = "archive-corrupt-${UUID.randomUUID()}"
        val files = UserDataRestoreMetadataFiles(context, "journal")
        val journal = UserDataRestoreJournal(context)
        try {
            files.write(owner, """{"version":3,"ownerUid":"$owner","state":"ACTIVE",
                "createdTanks":[],"createdTasks":[],"createdAssignments":[]}""")
            assertThrows(IllegalStateException::class.java) { journal.pending(owner) }
            assertNotNull(files.read(owner))
        } finally { journal.clearOwner(owner) }
    }
}
