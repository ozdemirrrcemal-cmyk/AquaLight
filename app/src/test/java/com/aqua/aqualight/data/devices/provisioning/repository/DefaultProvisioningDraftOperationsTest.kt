package com.aqua.aqualight.data.devices.provisioning.repository

import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftRequest
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningDraftStorage
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultProvisioningDraftOperationsTest {

    @Test
    fun `creates encrypted-storage draft from primitive application request`() {
        val storage = FakeProvisioningDraftStorage()
        val operations = DefaultProvisioningDraftOperations(
            draftStore = storage,
            cachedBleAddress = { "" }
        )

        val session = operations.createDraft(
            request(bleAddress = "AA:BB:CC:DD:EE:FF")
        ).getOrThrow()

        val draft = requireNotNull(storage.get(session.sessionId))
        assertEquals("candidate-1", draft.candidateId)
        assertEquals("AA:BB:CC:DD:EE:FF", draft.bleAddress)
        assertEquals("AQL-SETUP-123456", draft.bleName)
        assertEquals("claim-1", draft.claimCode)
        assertEquals("Home WiFi", draft.wifiCredentials.ssid)
        assertEquals("secret-password", draft.wifiCredentials.password)
        assertEquals("Europe/Istanbul|180", draft.wifiCredentials.timezone)
        assertEquals(180, draft.wifiCredentials.utcOffsetMinutes)
    }

    @Test
    fun `uses injected BLE cache when navigation carries no address`() {
        val storage = FakeProvisioningDraftStorage()
        val operations = DefaultProvisioningDraftOperations(
            draftStore = storage,
            cachedBleAddress = { bleName ->
                assertEquals("AQL-SETUP-123456", bleName)
                "11:22:33:44:55:66"
            }
        )

        val session = operations.createDraft(
            request(bleAddress = "")
        ).getOrThrow()

        assertEquals("11:22:33:44:55:66", storage.get(session.sessionId)?.bleAddress)
    }

    private fun request(bleAddress: String): ProvisioningDraftRequest =
        ProvisioningDraftRequest(
            candidateId = "candidate-1",
            bleAddress = bleAddress,
            bleName = "AQL-SETUP-123456",
            claimCode = "claim-1",
            rawQrPayload = "aql://setup",
            deviceTitle = "AquaLight",
            deviceSerial = "AQL-0001",
            deviceModel = "AQL-Pro",
            wifiSsid = "Home WiFi",
            wifiPassword = "secret-password",
            timezone = "Europe/Istanbul|180",
            utcOffsetMinutes = 180
        )

    private class FakeProvisioningDraftStorage : ProvisioningDraftStorage {
        private val drafts = linkedMapOf<String, AqlProvisioningDraft>()
        private var nextId = 1

        override fun create(
            candidateId: String,
            bleAddress: String,
            bleName: String,
            claimCode: String,
            rawQrPayload: String,
            deviceTitle: String,
            deviceSerial: String,
            deviceModel: String,
            wifiCredentials: AqlWifiCredentials,
            createdAtMillis: Long
        ): AqlProvisioningDraft {
            val draft = AqlProvisioningDraft(
                sessionId = "session-${nextId++}",
                candidateId = candidateId,
                bleAddress = bleAddress,
                bleName = bleName,
                claimCode = claimCode,
                rawQrPayload = rawQrPayload,
                deviceTitle = deviceTitle,
                deviceSerial = deviceSerial,
                deviceModel = deviceModel,
                wifiCredentials = wifiCredentials,
                createdAtMillis = createdAtMillis
            )
            drafts[draft.sessionId] = draft
            return draft
        }

        override fun get(sessionId: String): AqlProvisioningDraft? = drafts[sessionId]

        override fun remove(sessionId: String) {
            drafts.remove(sessionId)
        }

        override fun clearOwner() {
            drafts.clear()
        }
    }
}
