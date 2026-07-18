package com.aqua.aqualight.data.feedback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackSubmissionJournalStoreInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun journalEntrySurvivesStoreRecreationAndCanBeRemovedDurably() {
        clearJournal()
        val entry = PendingFeedbackUpload(
            documentId = "document-process-recreation",
            ownerUid = "owner-process-recreation",
            storagePath = "feedback_screenshots/owner-process-recreation/document.jpg"
        )

        try {
            SharedPreferencesFeedbackSubmissionJournalStore(context).put(entry)

            val recreatedStore = SharedPreferencesFeedbackSubmissionJournalStore(context)
            assertEquals(listOf(entry), recreatedStore.pendingEntries())

            recreatedStore.remove(entry.documentId)
            assertTrue(
                SharedPreferencesFeedbackSubmissionJournalStore(context)
                    .pendingEntries()
                    .isEmpty()
            )
        } finally {
            clearJournal()
        }
    }

    @Test
    fun multipleOwnerAwareEntriesAreUpdatedWithoutMutatingPreferenceSnapshots() {
        clearJournal()
        val first = PendingFeedbackUpload(
            "document-a",
            "owner-a",
            "feedback_screenshots/owner-a/a.jpg"
        )
        val second = PendingFeedbackUpload(
            "document-b",
            "owner-b",
            "feedback_screenshots/owner-b/b.jpg"
        )

        try {
            val store = SharedPreferencesFeedbackSubmissionJournalStore(context)
            store.put(first)
            store.put(second)
            store.remove(first.documentId)

            assertEquals(
                listOf(second),
                SharedPreferencesFeedbackSubmissionJournalStore(context).pendingEntries()
            )
        } finally {
            clearJournal()
        }
    }

    private fun clearJournal() {
        check(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        ) { "Feedback journal test preferences could not be cleared" }
    }

    private companion object {
        const val PREFERENCES_NAME = "feedback_submission_journal_v2"
    }
}
