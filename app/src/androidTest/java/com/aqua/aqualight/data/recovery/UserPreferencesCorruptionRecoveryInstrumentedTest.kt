package com.aqua.aqualight.data.recovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.user.EncryptedUserPreferencesSerializer
import com.aqua.aqualight.data.user.UserPreferencesSerializer
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesCorruptionRecoveryInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun incompleteEncryptedPayloadRecoversToVersionedDefaultAndReportsArea() = runBlocking {
        LocalDataRecoveryTracker.initialize(context)
        LocalDataRecoveryTracker.consumeRecoveredAreas()

        val serializer = EncryptedUserPreferencesSerializer(
            context = context,
            delegate = UserPreferencesSerializer
        )

        val recovered = serializer.readFrom(
            ByteArrayInputStream(byteArrayOf(0x01, 0x02, 0x03))
        )

        assertEquals(UserPreferencesSerializer.defaultValue, recovered)
        assertTrue(
            LocalDataRecoveryTracker.consumeRecoveredAreas().contains(
                LocalDataRecoveryTracker.Area.USER_PREFERENCES
            )
        )
    }

    @Test
    fun unauthenticatedCiphertextRecoversWithoutLeakingStoredContent() = runBlocking {
        LocalDataRecoveryTracker.initialize(context)
        LocalDataRecoveryTracker.consumeRecoveredAreas()

        val serializer = EncryptedUserPreferencesSerializer(
            context = context,
            delegate = UserPreferencesSerializer
        )
        val invalidAuthenticatedPayload = ByteArray(64) { index ->
            (index + 1).toByte()
        }

        val recovered = serializer.readFrom(
            ByteArrayInputStream(invalidAuthenticatedPayload)
        )

        assertEquals(UserPreferencesSerializer.defaultValue, recovered)
        assertEquals(
            setOf(LocalDataRecoveryTracker.Area.USER_PREFERENCES),
            LocalDataRecoveryTracker.consumeRecoveredAreas()
        )
    }
}
