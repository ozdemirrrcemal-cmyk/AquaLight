package com.aqua.aqualight.data.devices.provisioning.repository

import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftRequest
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningBleAddressCache
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DefaultProvisioningDraftOperationsTest {

    private val operations = DefaultProvisioningDraftOperations()

    @After
    fun tearDown() {
        AqlProvisioningDraftStore.clear()
        AqlProvisioningBleAddressCache.clear()
    }

    @Test
    fun `creates persisted draft from primitive application request`() {
        val result = operations.createDraft(
            request(
                bleAddress = "AA:BB:CC:DD:EE:FF"
            )
        )

        val session = result.getOrThrow()
        val draft = AqlProvisioningDraftStore.get(session.sessionId)

        assertNotNull(draft)
        requireNotNull(draft)
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
    fun `uses process cache when navigation carries no BLE address`() {
        AqlProvisioningBleAddressCache.put(
            bleName = "AQL-SETUP-123456",
            bleAddress = "11:22:33:44:55:66"
        )

        val session = operations.createDraft(
            request(bleAddress = "")
        ).getOrThrow()

        val draft = AqlProvisioningDraftStore.get(session.sessionId)
        assertEquals("11:22:33:44:55:66", draft?.bleAddress)
    }

    private fun request(
        bleAddress: String
    ): ProvisioningDraftRequest = ProvisioningDraftRequest(
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
}
