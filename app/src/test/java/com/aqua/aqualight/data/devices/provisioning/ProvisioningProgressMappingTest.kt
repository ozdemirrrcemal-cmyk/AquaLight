package com.aqua.aqualight.data.devices.provisioning

import com.aqua.aqualight.application.devices.provisioning.ProvisioningStatus
import com.aqua.aqualight.application.devices.provisioning.ProvisioningTransportEvent
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattEvent
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningStatusMessage
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningStatus
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningProgressMappingTest {

    @Test
    fun `draft session mapping hides WiFi password and claim data`() {
        val session = draft().toApplicationSession()

        assertEquals("session-1", session.sessionId)
        assertEquals("Test WiFi", session.wifiSsid)
        assertEquals("AquaLight Test", session.deviceTitle)
        assertEquals("AQL-TEST-001", session.deviceSerial)
        assertFalse(session.toString().contains("secret-password"))
        assertFalse(session.toString().contains("claim-secret"))
        assertFalse(session.toString().contains("raw-qr-secret"))
    }

    @Test
    fun `all provisioning statuses map with exact enum parity`() {
        val mappedStatuses = AqlProvisioningStatus.entries.map { status ->
            val event = AqlBleProvisioningGattEvent.StatusReceived(
                AqlBleProvisioningStatusMessage(status = status)
            ).toApplicationEvent() as ProvisioningTransportEvent.StatusReceived
            event.statusMessage.status
        }

        assertEquals(ProvisioningStatus.entries, mappedStatuses)
    }

    @Test
    fun `runtime handoff exposes endpoint and identity without credential`() {
        val original = AqlProvisioningRuntimeHandoff(
            deviceUid = DeviceUid("device-1"),
            endpoint = DeviceRuntimeEndpoint(
                ip = "192.168.1.44",
                wifiMode = "station",
                wifiConnected = true,
                setupApActive = false,
                runtimeTransport = "websocket",
                wsPort = 81,
                wsPath = "/aql",
                wsProtocol = "aql.v1",
                wsProtocolVersion = 1,
                discoveryPort = 4210
            ),
            webSocketToken = "a".repeat(64)
        )

        val mappedEvent = AqlBleProvisioningGattEvent.RuntimeHandoffReceived(original)
            .toApplicationEvent { handoff ->
                handoff.toApplicationReference("handoff-1")
            } as ProvisioningTransportEvent.RuntimeHandoffReceived

        assertEquals("handoff-1", mappedEvent.handoff.handoffId)
        assertEquals(original.deviceUid.value, mappedEvent.handoff.deviceUid)
        assertEquals(original.endpoint.ip, mappedEvent.handoff.endpoint.ip)
        assertFalse(mappedEvent.handoff.toString().contains(original.webSocketToken))
    }

    @Test
    fun `device info event maps verified identity fields`() {
        val event = AqlBleProvisioningGattEvent.DeviceInfoVerified(
            deviceTitle = "AquaLight Verified",
            deviceSerial = "AQL-VERIFIED-1",
            deviceModel = "AQL-Light"
        ).toApplicationEvent() as ProvisioningTransportEvent.DeviceInfoVerified

        assertEquals("AquaLight Verified", event.info.title)
        assertEquals("AQL-VERIFIED-1", event.info.serial)
        assertEquals("AQL-Light", event.info.model)
        assertTrue(event.info.title.isNotBlank())
    }

    private fun draft() = AqlProvisioningDraft(
        sessionId = "session-1",
        candidateId = "candidate-1",
        bleAddress = "AA:BB:CC:DD:EE:FF",
        bleName = "AquaLight-Setup",
        claimCode = "claim-secret",
        rawQrPayload = "raw-qr-secret",
        deviceTitle = "AquaLight Test",
        deviceSerial = "AQL-TEST-001",
        deviceModel = "AQL-Light",
        wifiCredentials = AqlWifiCredentials(
            ssid = "Test WiFi",
            password = "secret-password"
        ),
        createdAtMillis = 1L
    )
}
