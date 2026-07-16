package com.aqua.aqualight.data.devices.provisioning.repository

import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftRequest
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningDraftStorage
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningQrSecret
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningQrSecretStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProvisioningDraftOperationsTest {

    @Test
    fun `creates encrypted-storage draft from primitive application request`() {
        val storage = FakeProvisioningDraftStorage()
        val secretStorage = FakeProvisioningQrSecretStorage().apply {
            secrets["secret-reference-1"] = ProvisioningQrSecret(
                claimCode = "claim-1",
                rawPayload = "raw-qr-secret"
            )
        }
        val operations = DefaultProvisioningDraftOperations(
            draftStore = storage,
            qrSecretStore = secretStorage,
            cachedBleAddress = { "" }
        )

        val session = operations.createDraft(
            request(
                bleAddress = "AA:BB:CC:DD:EE:FF",
                qrSecretReference = "secret-reference-1"
            )
        ).getOrThrow()

        val draft = requireNotNull(storage.get(session.sessionId))
        assertEquals("candidate-1", draft.candidateId)
        assertEquals("AA:BB:CC:DD:EE:FF", draft.bleAddress)
        assertEquals("AQL-SETUP-123456", draft.bleName)
        assertEquals("claim-1", draft.claimCode)
        assertEquals("raw-qr-secret", draft.rawQrPayload)
        assertEquals("Home WiFi", draft.wifiCredentials.ssid)
        assertEquals("secret-password", draft.wifiCredentials.password)
        assertEquals("Europe/Istanbul|180", draft.wifiCredentials.timezone)
        assertEquals(180, draft.wifiCredentials.utcOffsetMinutes)
        assertTrue(secretStorage.secrets.containsKey("secret-reference-1"))
    }

    @Test
    fun `same QR secret creates replacement draft after Wi-Fi credential rejection`() {
        val storage = FakeProvisioningDraftStorage()
        val secretStorage = FakeProvisioningQrSecretStorage().apply {
            secrets["secret-reference-1"] = ProvisioningQrSecret(
                claimCode = "claim-1",
                rawPayload = "raw-qr-secret"
            )
        }
        val operations = DefaultProvisioningDraftOperations(
            draftStore = storage,
            qrSecretStore = secretStorage,
            cachedBleAddress = { "" }
        )

        val rejectedSession = operations.createDraft(
            request(
                bleAddress = "AA:BB:CC:DD:EE:FF",
                qrSecretReference = "secret-reference-1",
                wifiPassword = "wrong-password"
            )
        ).getOrThrow()
        val retrySession = operations.createDraft(
            request(
                bleAddress = "AA:BB:CC:DD:EE:FF",
                qrSecretReference = "secret-reference-1",
                wifiPassword = "correct-password"
            )
        ).getOrThrow()

        assertEquals(
            "wrong-password",
            storage.get(rejectedSession.sessionId)?.wifiCredentials?.password
        )
        assertEquals(
            "correct-password",
            storage.get(retrySession.sessionId)?.wifiCredentials?.password
        )
        assertTrue(secretStorage.secrets.containsKey("secret-reference-1"))
        assertEquals(2, storage.size)
    }

    @Test
    fun `uses injected BLE cache when navigation carries no address`() {
        val storage = FakeProvisioningDraftStorage()
        val operations = DefaultProvisioningDraftOperations(
            draftStore = storage,
            qrSecretStore = FakeProvisioningQrSecretStorage(),
            cachedBleAddress = { bleName ->
                assertEquals("AQL-SETUP-123456", bleName)
                "11:22:33:44:55:66"
            }
        )

        val session = operations.createDraft(
            request(bleAddress = "", qrSecretReference = "")
        ).getOrThrow()

        assertEquals("11:22:33:44:55:66", storage.get(session.sessionId)?.bleAddress)
        assertEquals("", storage.get(session.sessionId)?.claimCode)
    }

    @Test
    fun `expired or foreign QR secret reference fails closed before draft creation`() {
        val storage = FakeProvisioningDraftStorage()
        val operations = DefaultProvisioningDraftOperations(
            draftStore = storage,
            qrSecretStore = FakeProvisioningQrSecretStorage(),
            cachedBleAddress = { "" }
        )

        val result = operations.createDraft(
            request(
                bleAddress = "AA:BB:CC:DD:EE:FF",
                qrSecretReference = "missing-reference"
            )
        )

        assertTrue(result.isFailure)
        assertEquals(0, storage.size)
    }

    private fun request(
        bleAddress: String,
        qrSecretReference: String,
        wifiPassword: String = "secret-password"
    ): ProvisioningDraftRequest = ProvisioningDraftRequest(
        candidateId = "candidate-1",
        bleAddress = bleAddress,
        bleName = "AQL-SETUP-123456",
        qrSecretReference = qrSecretReference,
        deviceTitle = "AquaLight",
        deviceSerial = "AQL-0001",
        deviceModel = "AQL-Pro",
        wifiSsid = "Home WiFi",
        wifiPassword = wifiPassword,
        timezone = "Europe/Istanbul|180",
        utcOffsetMinutes = 180
    )

    private class FakeProvisioningDraftStorage : ProvisioningDraftStorage {
        private val drafts = linkedMapOf<String, AqlProvisioningDraft>()
        private var nextId = 1
        val size: Int
            get() = drafts.size

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

    private class FakeProvisioningQrSecretStorage : ProvisioningQrSecretStorage {
        val secrets = linkedMapOf<String, ProvisioningQrSecret>()

        override fun create(
            claimCode: String,
            rawPayload: String,
            createdAtMillis: Long
        ): String = error("not used")

        override fun get(reference: String): ProvisioningQrSecret? = secrets[reference]

        override fun remove(reference: String) {
            secrets.remove(reference)
        }

        override fun clearOwner() {
            secrets.clear()
        }
    }
}
