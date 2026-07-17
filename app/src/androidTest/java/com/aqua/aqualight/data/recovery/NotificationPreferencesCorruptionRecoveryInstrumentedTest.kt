package com.aqua.aqualight.data.recovery

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.notifications.NotificationPreferenceStoreRules
import com.aqua.aqualight.data.notifications.NotificationPreferencesSerializer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPreferencesCorruptionRecoveryInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun malformedOwnerPreferenceStoreIsReplacedAndReported() = runBlocking {
        LocalDataRecoveryTracker.initialize(context)
        LocalDataRecoveryTracker.consumeRecoveredAreas()

        val corruptedBytes = byteArrayOf(0x0A, 0x7F, 0x01, 0x02)
        val file = File(
            context.cacheDir,
            "notification-prefs-corruption-${UUID.randomUUID()}.pb"
        ).apply {
            writeBytes(corruptedBytes)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = DataStoreFactory.create(
            serializer = NotificationPreferencesSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler {
                LocalDataRecoveryTracker.markRecovered(
                    LocalDataRecoveryTracker.Area.NOTIFICATION_PREFERENCES
                )
                NotificationPreferenceStoreRules.defaultStore()
            },
            scope = scope,
            produceFile = { file }
        )

        try {
            assertEquals(
                NotificationPreferenceStoreRules.defaultStore(),
                store.data.first()
            )
            assertEquals(
                setOf(LocalDataRecoveryTracker.Area.NOTIFICATION_PREFERENCES),
                LocalDataRecoveryTracker.consumeRecoveredAreas()
            )
            assertFalse(file.readBytes().contentEquals(corruptedBytes))
        } finally {
            scope.cancel()
            file.delete()
            LocalDataRecoveryTracker.consumeRecoveredAreas()
        }
    }
}
