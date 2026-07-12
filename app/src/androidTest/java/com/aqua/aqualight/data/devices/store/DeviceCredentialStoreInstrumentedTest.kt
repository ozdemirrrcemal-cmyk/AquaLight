package com.aqua.aqualight.data.devices.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceCredentialStoreInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun stagedTokenDoesNotOverwriteCommittedTokenUntilCommit() = runBlocking {
        val ownerUid = uniqueOwner("stage")
        val deviceUid = DeviceUid("device-stage")
        val store = DeviceCredentialStore(context, ownerUid)

        try {
            store.saveToken(deviceUid, COMMITTED_TOKEN)
            store.stageToken(deviceUid, STAGED_TOKEN)

            assertEquals(STAGED_TOKEN, store.getToken(deviceUid))
            assertEquals(COMMITTED_TOKEN, store.getCommittedToken(deviceUid))

            store.rollbackStagedToken(deviceUid)

            assertEquals(COMMITTED_TOKEN, store.getToken(deviceUid))
            assertEquals(COMMITTED_TOKEN, store.getCommittedToken(deviceUid))

            store.stageToken(deviceUid, STAGED_TOKEN)
            store.commitStagedToken(deviceUid)

            assertEquals(STAGED_TOKEN, store.getToken(deviceUid))
            assertEquals(STAGED_TOKEN, store.getCommittedToken(deviceUid))
        } finally {
            store.clearOwner()
        }
    }

    @Test
    fun processRestartCleanupDiscardsOnlyStagedTokens() = runBlocking {
        val ownerUid = uniqueOwner("restart")
        val committedDeviceUid = DeviceUid("device-committed")
        val stagedOnlyDeviceUid = DeviceUid("device-staged-only")
        val store = DeviceCredentialStore(context, ownerUid)

        try {
            store.saveToken(committedDeviceUid, COMMITTED_TOKEN)
            store.stageToken(committedDeviceUid, STAGED_TOKEN)
            store.stageToken(stagedOnlyDeviceUid, SECOND_STAGED_TOKEN)

            val restartedStore = DeviceCredentialStore(context, ownerUid)

            assertEquals(2, restartedStore.discardStagedTokens())
            assertEquals(
                COMMITTED_TOKEN,
                restartedStore.getToken(committedDeviceUid)
            )
            assertEquals(
                COMMITTED_TOKEN,
                restartedStore.getCommittedToken(committedDeviceUid)
            )
            assertNull(restartedStore.getToken(stagedOnlyDeviceUid))
            assertEquals(0, restartedStore.discardStagedTokens())
        } finally {
            store.clearOwner()
        }
    }

    @Test
    fun orphanReconciliationKeepsOnlyDurableDeviceCredentials() = runBlocking {
        val ownerUid = uniqueOwner("orphan")
        val durableDeviceUid = DeviceUid("device-durable")
        val orphanDeviceUid = DeviceUid("device-orphan")
        val store = DeviceCredentialStore(context, ownerUid)

        try {
            store.saveToken(durableDeviceUid, COMMITTED_TOKEN)
            store.saveToken(orphanDeviceUid, STAGED_TOKEN)

            assertEquals(
                1,
                store.retainTokensFor(listOf(durableDeviceUid))
            )
            assertEquals(COMMITTED_TOKEN, store.getToken(durableDeviceUid))
            assertNull(store.getToken(orphanDeviceUid))
        } finally {
            store.clearOwner()
        }
    }

    @Test
    fun sameDeviceCredentialIsIsolatedBetweenOwners() = runBlocking {
        val ownerA = uniqueOwner("owner-a")
        val ownerB = uniqueOwner("owner-b")
        val deviceUid = DeviceUid("shared-device")
        val storeA = DeviceCredentialStore(context, ownerA)
        val storeB = DeviceCredentialStore(context, ownerB)

        try {
            storeA.saveToken(deviceUid, COMMITTED_TOKEN)
            storeB.saveToken(deviceUid, STAGED_TOKEN)

            assertEquals(COMMITTED_TOKEN, storeA.getToken(deviceUid))
            assertEquals(STAGED_TOKEN, storeB.getToken(deviceUid))

            storeA.clearOwner()

            assertNull(storeA.getToken(deviceUid))
            assertEquals(STAGED_TOKEN, storeB.getToken(deviceUid))
        } finally {
            storeA.clearOwner()
            storeB.clearOwner()
        }
    }

    private fun uniqueOwner(prefix: String): String {
        return "$prefix-${UUID.randomUUID()}"
    }

    private companion object {
        val COMMITTED_TOKEN = token('a')
        val STAGED_TOKEN = token('b')
        val SECOND_STAGED_TOKEN = token('c')

        fun token(character: Char): String {
            return character.toString().repeat(
                AqlBleProvisioningContract.RUNTIME_TOKEN_HEX_LENGTH
            )
        }
    }
}
