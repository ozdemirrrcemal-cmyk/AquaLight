package com.aqua.aqualight.data.recovery

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.user.EncryptedUserPreferencesSerializer
import com.aqua.aqualight.data.user.UserPreferencesSerializer
import com.aqua.aqualight.data.user.UserPreferencesStoreRules
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
class UserPreferencesCorruptionRecoveryInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun incompleteEncryptedPayloadIsAtomicallyReplacedAndReported() = runBlocking {
        verifyControlledRecovery(
            corruptedBytes = byteArrayOf(0x01, 0x02, 0x03)
        )
    }

    @Test
    fun unauthenticatedCiphertextIsAtomicallyReplacedAndReported() = runBlocking {
        verifyControlledRecovery(
            corruptedBytes = ByteArray(64) { index ->
                (index + 1).toByte()
            }
        )
    }

    private suspend fun verifyControlledRecovery(
        corruptedBytes: ByteArray
    ) {
        LocalDataRecoveryTracker.initialize(context)
        LocalDataRecoveryTracker.consumeRecoveredAreas()

        val file = File(
            context.cacheDir,
            "user-prefs-corruption-${UUID.randomUUID()}.pb"
        )
        file.writeBytes(corruptedBytes)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = DataStoreFactory.create(
            serializer = EncryptedUserPreferencesSerializer(
                context = context,
                delegate = UserPreferencesSerializer
            ),
            corruptionHandler = ReplaceFileCorruptionHandler {
                LocalDataRecoveryTracker.markRecovered(
                    LocalDataRecoveryTracker.Area.USER_PREFERENCES
                )
                UserPreferencesStoreRules.defaultPreferences()
            },
            scope = scope,
            produceFile = { file }
        )

        try {
            val recovered = store.data.first()

            assertEquals(
                UserPreferencesStoreRules.defaultPreferences(),
                recovered
            )
            assertEquals(
                setOf(LocalDataRecoveryTracker.Area.USER_PREFERENCES),
                LocalDataRecoveryTracker.consumeRecoveredAreas()
            )
            assertFalse(
                "Corrupted bytes must be replaced on disk.",
                file.readBytes().contentEquals(corruptedBytes)
            )
        } finally {
            scope.cancel()
            file.delete()
            LocalDataRecoveryTracker.consumeRecoveredAreas()
        }
    }
}
