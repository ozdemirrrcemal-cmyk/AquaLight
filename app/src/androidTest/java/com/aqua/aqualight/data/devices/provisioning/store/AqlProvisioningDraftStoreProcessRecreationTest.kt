package com.aqua.aqualight.data.devices.provisioning.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AqlProvisioningDraftStoreProcessRecreationTest {

    private lateinit var context: Context
    private lateinit var ownerUid: String
    private var nowMillis: Long = 1_800_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ownerUid = "provisioning-test-${System.nanoTime()}"
        store(ownerUid).clearOwner()
    }

    @After
    fun tearDown() {
        store(ownerUid).clearOwner()
    }

    @Test
    fun encryptedSessionSurvivesStoreRecreationWithoutPlaintextSecrets() {
        val firstProcessStore = store(ownerUid)
        val draft = firstProcessStore.create(
            candidateId = "candidate-1",
            bleAddress = "AA:BB:CC:DD:EE:FF",
            bleName = "AQL-SETUP-0001",
            claimCode = "claim-secret",
            rawQrPayload = "raw-qr-secret",
            deviceTitle = "AquaLight Test",
            deviceSerial = "AQL-TEST-001",
            deviceModel = "AQL-Light",
            wifiCredentials = credentials(),
            createdAtMillis = nowMillis
        )

        val recreatedProcessStore = store(ownerUid)
        val restored = recreatedProcessStore.get(draft.sessionId)

        assertNotNull(restored)
        requireNotNull(restored)
        assertEquals("Home WiFi", restored.wifiCredentials.ssid)
        assertEquals("secret-password", restored.wifiCredentials.password)
        assertEquals("claim-secret", restored.claimCode)

        val encryptedFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/aql_provisioning_sessions.xml"
        )
        val encryptedContent = encryptedFile.readText()
        assertFalse(encryptedContent.contains("Home WiFi"))
        assertFalse(encryptedContent.contains("secret-password"))
        assertFalse(encryptedContent.contains("claim-secret"))
        assertFalse(encryptedContent.contains("raw-qr-secret"))
    }

    @Test
    fun anotherOwnerCannotReadOrDeleteTheSession() {
        val ownerStore = store(ownerUid)
        val draft = ownerStore.create(
            candidateId = "candidate-1",
            bleAddress = "AA:BB:CC:DD:EE:FF",
            deviceTitle = "AquaLight Test",
            deviceSerial = "AQL-TEST-001",
            deviceModel = "AQL-Light",
            wifiCredentials = credentials(),
            createdAtMillis = nowMillis
        )

        val otherOwnerStore = store("other-$ownerUid")
        assertNull(otherOwnerStore.get(draft.sessionId))
        otherOwnerStore.remove(draft.sessionId)

        assertNotNull(ownerStore.get(draft.sessionId))
    }

    @Test
    fun expiredSessionFailsClosedAfterProcessRecreation() {
        val firstProcessStore = store(ownerUid)
        val draft = firstProcessStore.create(
            candidateId = "candidate-1",
            bleAddress = "AA:BB:CC:DD:EE:FF",
            deviceTitle = "AquaLight Test",
            deviceSerial = "AQL-TEST-001",
            deviceModel = "AQL-Light",
            wifiCredentials = credentials(),
            createdAtMillis = nowMillis
        )

        nowMillis += 16 * 60 * 1000L

        assertNull(store(ownerUid).get(draft.sessionId))
    }

    private fun store(owner: String): AqlProvisioningDraftStore =
        AqlProvisioningDraftStore(
            context = context,
            ownerUidProvider = { owner },
            clock = { nowMillis }
        )

    private fun credentials() = AqlWifiCredentials(
        ssid = "Home WiFi",
        password = "secret-password",
        timezone = "Europe/Istanbul|180",
        utcOffsetMinutes = 180
    )
}
