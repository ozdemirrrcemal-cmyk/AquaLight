package com.aqua.aqualight.data.devices.provisioning.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class AqlProvisioningQrSecretStoreInstrumentedTest {

    private lateinit var context: Context
    private lateinit var ownerUid: String
    private var nowMillis: Long = 1_800_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ownerUid = "qr-secret-${System.nanoTime()}"
        store(ownerUid).clearOwner()
    }

    @After
    fun tearDown() {
        store(ownerUid).clearOwner()
        store("other-$ownerUid").clearOwner()
    }

    @Test
    fun encryptedSecretSurvivesStoreRecreationWithoutPlaintextClaimData() {
        val reference = store(ownerUid).create(
            claimCode = "claim-secret",
            rawPayload = "raw-qr-secret",
            createdAtMillis = nowMillis
        )

        val restored = store(ownerUid).get(reference)

        assertNotNull(restored)
        requireNotNull(restored)
        assertEquals("claim-secret", restored.claimCode)
        assertEquals("raw-qr-secret", restored.rawPayload)

        val encryptedFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/aql_provisioning_qr_secrets.xml"
        )
        val encryptedContent = encryptedFile.readText()
        assertFalse(encryptedContent.contains("claim-secret"))
        assertFalse(encryptedContent.contains("raw-qr-secret"))
        assertFalse(encryptedContent.contains(ownerUid))
    }

    @Test
    fun anotherOwnerCannotReadOrDeleteTheSecret() {
        val reference = store(ownerUid).create(
            claimCode = "claim-secret",
            rawPayload = "raw-qr-secret",
            createdAtMillis = nowMillis
        )

        val otherStore = store("other-$ownerUid")
        assertNull(otherStore.get(reference))
        otherStore.remove(reference)

        assertNotNull(store(ownerUid).get(reference))
    }

    @Test
    fun expiredSecretFailsClosedAfterProcessRecreation() {
        val reference = store(ownerUid).create(
            claimCode = "claim-secret",
            rawPayload = "raw-qr-secret",
            createdAtMillis = nowMillis
        )

        nowMillis += 16 * 60 * 1000L

        assertNull(store(ownerUid).get(reference))
    }

    private fun store(owner: String): AqlProvisioningQrSecretStore =
        AqlProvisioningQrSecretStore(
            context = context,
            ownerUidProvider = { owner },
            clock = { nowMillis }
        )
}
