package com.aqua.aqualight.data.devices.provisioning

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningCandidate
import com.aqua.aqualight.data.devices.provisioning.qr.AqlProvisioningQrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DefaultProvisioningDiscoveryOperationsMapperTest {

    @Test
    fun `BLE candidate maps every discovery field into application snapshot`() {
        val mapped = AqlBleProvisioningCandidate(
            address = "AA:BB:CC:DD:EE:FF",
            name = "AQL-SETUP-0001",
            rssi = -47,
            firstSeenAtMillis = 10L,
            lastSeenAtMillis = 20L,
            deviceUid = "device-1",
            productName = "AquaLight One",
            model = "AQL-Light",
            serialNumber = "AQL-0001",
            claimState = "Ready",
            rawAdvertisementPayload = "payload"
        ).toApplicationSnapshot()

        assertEquals("AA:BB:CC:DD:EE:FF", mapped.address)
        assertEquals("AQL-SETUP-0001", mapped.bleName)
        assertEquals(-47, mapped.rssi)
        assertEquals("device-1", mapped.deviceUid)
        assertEquals("AquaLight One", mapped.displayTitle)
        assertEquals("AQL-Light", mapped.model)
        assertEquals("AQL-0001", mapped.displaySerial)
        assertEquals("Ready", mapped.displayStatus)
        assertEquals("payload", mapped.rawAdvertisementPayload)
    }

    @Test
    fun `QR payload maps identity and encrypted secret reference without claim data`() {
        val source = AqlProvisioningQrPayload(
            version = 1,
            brand = "AquaLight",
            deviceUid = DeviceUid("device-1"),
            serialNumber = "AQL-0001",
            productId = "product-1",
            model = "AQL-Light",
            displayName = "AquaLight One",
            hardwareRevision = "rev-a",
            skuCode = "sku-1",
            provisioningId = "provisioning-1",
            claimCode = "claim-1",
            bleName = "AQL-SETUP-0001",
            raw = "raw-qr"
        )
        val mapped = source.toApplicationPayload("secret-reference-1")

        assertEquals("device-1", mapped.deviceUid)
        assertEquals("AQL-0001", mapped.serialNumber)
        assertEquals("product-1", mapped.productId)
        assertEquals("AQL-Light", mapped.model)
        assertEquals("AquaLight One", mapped.displayName)
        assertEquals("rev-a", mapped.hardwareRevision)
        assertEquals("sku-1", mapped.skuCode)
        assertEquals("provisioning-1", mapped.provisioningId)
        assertEquals("secret-reference-1", mapped.secretReference)
        assertEquals("AQL-SETUP-0001", mapped.bleName)
        assertFalse(mapped.toString().contains(source.claimCode))
        assertFalse(mapped.toString().contains(source.raw))
    }
}
