package com.aqua.aqualight.platform.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppMediaStorageInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun recoveryNeverDeletesAnotherOwnersPendingMedia() {
        val first = pendingSavedMedia("owner-a", "a")
        val second = pendingSavedMedia("owner-b", "b")

        AppMediaStorage.reconcilePendingMedia(
            context = context,
            ownerUid = "owner-a",
            referencedUris = emptyList(),
            nowMillis = System.currentTimeMillis() + TWO_DAYS_MILLIS
        )

        assertFalse(AppMediaStorage.isAppOwned(context, first))
        assertTrue(AppMediaStorage.isAppOwned(context, second))
        AppMediaStorage.discardPendingMediaForOwner(context, "owner-b")
        assertFalse(AppMediaStorage.isAppOwned(context, second))
    }

    @Test
    fun failedRollbackRetainsJournalUntilRecoveryCanDeleteCandidate() {
        val pending = pendingSavedMedia("owner-rollback", "retry")

        assertFalse(
            AppMediaStorage.rollbackPendingMedia(context, pending) { false }
        )
        assertTrue(AppMediaStorage.isAppOwned(context, pending))

        AppMediaStorage.reconcilePendingMedia(
            context = context,
            ownerUid = "owner-rollback",
            referencedUris = emptyList(),
            nowMillis = System.currentTimeMillis() + TWO_DAYS_MILLIS
        )

        assertFalse(AppMediaStorage.isAppOwned(context, pending))
    }

    @Test
    fun failedPostCommitDeletionIsRetriedFromDurableDeletionJournal() {
        val committed = pendingSavedMedia("owner-delete", "old")
        AppMediaStorage.commitPendingMedia(context, committed)

        assertFalse(
            AppMediaStorage.deleteAfterCommit(
                context = context,
                ownerUid = "owner-delete",
                uriString = committed
            ) { false }
        )
        assertTrue(AppMediaStorage.isAppOwned(context, committed))

        AppMediaStorage.reconcilePendingDeletions(
            context = context,
            ownerUid = "owner-delete",
            referencedUris = emptyList()
        )

        assertFalse(AppMediaStorage.isAppOwned(context, committed))
    }

    @Test
    fun ownerScopedCommittedSweepCleansOrphanWhenDeletionJournalCouldNotBeWritten() {
        val committed = pendingSavedMedia("owner-sweep", "orphan")
        AppMediaStorage.commitPendingMedia(context, committed)
        val file = requireNotNull(AppMediaStorage.resolveInternalMediaFile(context, committed))
        check(file.setLastModified(System.currentTimeMillis() - TWO_DAYS_MILLIS))

        AppMediaStorage.reconcileUnreferencedCommittedMedia(
            context = context,
            ownerUid = "owner-sweep",
            referencedUris = emptyList()
        )

        assertFalse(AppMediaStorage.isAppOwned(context, committed))
    }

    @Test
    fun plantPhotosUseCanonicalOwnerScopedOrphanRecovery() {
        val committed = pendingSavedMedia(
            ownerUid = "owner-plant",
            ownerToken = "7",
            scope = AppMediaScope.PLANT
        )
        AppMediaStorage.commitPendingMedia(context, committed)
        val file = requireNotNull(
            AppMediaStorage.resolveInternalMediaFile(
                context = context,
                uriString = committed,
                expectedScope = AppMediaScope.PLANT
            )
        )
        check(file.setLastModified(System.currentTimeMillis() - TWO_DAYS_MILLIS))

        AppMediaStorage.reconcileUnreferencedCommittedMedia(
            context = context,
            ownerUid = "owner-plant",
            referencedUris = emptyList()
        )

        assertFalse(AppMediaStorage.isAppOwned(context, committed))
    }

    @Test
    fun committedCandidateIsNeverRemovedByLaterReconciliation() {
        val committed = pendingSavedMedia("owner-a", "committed")
        AppMediaStorage.commitPendingMedia(context, committed)

        AppMediaStorage.reconcilePendingMedia(
            context = context,
            ownerUid = "owner-a",
            referencedUris = emptyList(),
            nowMillis = System.currentTimeMillis() + TWO_DAYS_MILLIS
        )

        assertTrue(AppMediaStorage.isAppOwned(context, committed))
        AppMediaStorage.deleteInternalMedia(context, committed)
    }

    @Test
    fun candidateOwnershipUsesExactUidAndScopeInsteadOfSanitizedFilename() {
        val owner = "plant/owner"
        val photo = pendingSavedMedia(owner, "7", AppMediaScope.PLANT)
        try {
            assertTrue(AppMediaStorage.pendingMediaOwner(context, photo, AppMediaScope.PLANT) == owner)
            assertFalse(AppMediaStorage.pendingMediaOwner(context, photo, AppMediaScope.PLANT) == "plant?owner")
            assertFalse(AppMediaStorage.pendingMediaOwner(context, photo, AppMediaScope.TANK) == owner)
            AppMediaStorage.commitPendingMedia(context, photo)
            assertFalse(AppMediaStorage.pendingMediaOwner(context, photo, AppMediaScope.PLANT) == owner)
        } finally {
            AppMediaStorage.deleteInternalMedia(context, photo)
        }
    }

    @Test
    fun committedOrphanRecoveryDoesNotMatchAnotherOwnersSanitizedOrPrefixedUid() {
        val prefix = "photo-owner-${java.util.UUID.randomUUID()}"
        val owners = listOf(prefix, "${prefix}_other", "$prefix/a", "$prefix?a")
        val photos = owners.map { owner -> pendingSavedMedia(owner, "7", AppMediaScope.LIVESTOCK) }
        try {
            photos.forEach { AppMediaStorage.commitPendingMedia(context, it) }
            owners.forEachIndexed { index, owner ->
                AppMediaStorage.reconcileUnreferencedCommittedMedia(
                    context, owner, emptyList(), System.currentTimeMillis() + TWO_DAYS_MILLIS
                )
                assertFalse(AppMediaStorage.isAppOwned(context, photos[index]))
                photos.drop(index + 1).forEach { photo ->
                    assertTrue(AppMediaStorage.isAppOwned(context, photo))
                }
            }
        } finally {
            photos.forEach { AppMediaStorage.deleteInternalMedia(context, it) }
        }
    }

    private fun pendingSavedMedia(
        ownerUid: String,
        ownerToken: String,
        scope: AppMediaScope = AppMediaScope.TANK
    ): String {
        val crop = requireNotNull(
            AppMediaStorage.createCropOutputUri(
                context = context,
                scope = scope,
                ownerToken = ownerToken
            )
        )
        File(requireNotNull(crop.path)).writeBytes(byteArrayOf(1, 2, 3, 4))
        return requireNotNull(
            AppMediaStorage.promoteCropOutput(
                context = context,
                scope = scope,
                ownerToken = ownerToken,
                ownerUid = ownerUid,
                outputUri = crop
            )
        ).toString()
    }

    private companion object {
        const val TWO_DAYS_MILLIS = 2L * 24L * 60L * 60L * 1000L
    }
}
