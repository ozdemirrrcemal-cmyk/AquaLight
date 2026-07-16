package com.aqua.aqualight.data.devices.provisioning.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProvisioningCommitRecoveryStoreInstrumentedTest {

    private lateinit var context: Context
    private lateinit var ownerUid: String
    private lateinit var otherOwnerUid: String
    private lateinit var deviceUid: DeviceUid

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        ownerUid = "commit-recovery-${System.nanoTime()}"
        otherOwnerUid = "other-$ownerUid"
        deviceUid = DeviceUid("device-${System.nanoTime()}")
        clearOwner(ownerUid)
        clearOwner(otherOwnerUid)
    }

    @After
    fun tearDown() = runBlocking {
        clearOwner(ownerUid)
        clearOwner(otherOwnerUid)
    }

    @Test
    fun journalRecoversVerifiedSnapshotAndTokenIdempotently() = runBlocking {
        val recoveryStore = ProvisioningCommitRecoveryStore(context)
        val expectedSnapshot = snapshot()

        recoveryStore.record(
            ownerUid = ownerUid,
            snapshot = expectedSnapshot,
            runtimeToken = RUNTIME_TOKEN
        )

        assertNull(
            DeviceKnownStore(context, ownerUid)
                .loadSnapshots()
                .firstOrNull { stored -> stored.deviceUid == deviceUid }
        )
        assertNull(DeviceCredentialStore(context, ownerUid).getCommittedToken(deviceUid))

        assertEquals(1, recoveryStore.recoverOwner(ownerUid))

        val restored = DeviceKnownStore(context, ownerUid)
            .loadSnapshots()
            .single { stored -> stored.deviceUid == deviceUid }
        assertEquals(expectedSnapshot.identity.serialNumber, restored.identity.serialNumber)
        assertEquals(expectedSnapshot.product.family, restored.product.family)
        assertEquals(expectedSnapshot.endpoint.ip, restored.endpoint.ip)
        assertEquals(
            RUNTIME_TOKEN,
            DeviceCredentialStore(context, ownerUid).getCommittedToken(deviceUid)
        )
        assertEquals(0, recoveryStore.recoverOwner(ownerUid))

        val encryptedFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/aql_provisioning_commit_recovery.xml"
        )
        val encryptedContent = encryptedFile.readText()
        assertFalse(encryptedContent.contains(RUNTIME_TOKEN))
        assertFalse(encryptedContent.contains(deviceUid.value))
        assertFalse(encryptedContent.contains(ownerUid))
    }

    @Test
    fun anotherOwnerCannotRecoverOrConsumeTheJournal() = runBlocking {
        val recoveryStore = ProvisioningCommitRecoveryStore(context)
        recoveryStore.record(
            ownerUid = ownerUid,
            snapshot = snapshot(),
            runtimeToken = RUNTIME_TOKEN
        )

        assertEquals(0, recoveryStore.recoverOwner(otherOwnerUid))
        assertNull(DeviceCredentialStore(context, otherOwnerUid).getCommittedToken(deviceUid))
        assertEquals(1, recoveryStore.recoverOwner(ownerUid))
        assertEquals(
            RUNTIME_TOKEN,
            DeviceCredentialStore(context, ownerUid).getCommittedToken(deviceUid)
        )
    }

    private suspend fun clearOwner(owner: String) {
        ProvisioningCommitRecoveryStore(context).clearOwner(owner)
        DeviceKnownStore(context, owner).clearOwnerData()
        DeviceCredentialStore(context, owner).clearOwner()
    }

    private fun snapshot() = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = deviceUid,
            macAddress = "AA:BB:CC:DD:EE:FF",
            serialNumber = "AQL-COMMIT-001",
            displayName = "AquaLight Commit Recovery"
        ),
        product = DeviceProduct(
            brand = "AquaLight",
            family = DeviceFamily.LIGHT,
            familyRaw = DeviceFamily.LIGHT.wireValue,
            model = "AQL-Light",
            displayName = "AquaLight Commit Recovery"
        ),
        firmwareVersion = "1.0.0",
        endpoint = DeviceRuntimeEndpoint(
            ip = "192.168.1.44",
            wifiMode = "station",
            wifiConnected = true,
            runtimeTransport = "websocket",
            wsPort = 81,
            wsPath = "/aql",
            wsProtocol = "aql.v1",
            wsProtocolVersion = 1,
            discoveryPort = 4210
        ),
        lastSeenAtMillis = 1_800_000_000_000L
    )

    private companion object {
        val RUNTIME_TOKEN = "a".repeat(64)
    }
}
