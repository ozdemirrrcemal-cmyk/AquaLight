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

    private fun pendingSavedMedia(ownerUid: String, ownerToken: String): String {
        val crop = requireNotNull(
            AppMediaStorage.createCropOutputUri(
                context = context,
                scope = AppMediaScope.TANK,
                ownerToken = ownerToken
            )
        )
        File(requireNotNull(crop.path)).writeBytes(byteArrayOf(1, 2, 3, 4))
        return requireNotNull(
            AppMediaStorage.promoteCropOutput(
                context = context,
                scope = AppMediaScope.TANK,
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
