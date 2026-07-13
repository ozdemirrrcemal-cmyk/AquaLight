package com.aqua.aqualight.ui.tabs.devices.add

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningFailurePolicyTest {

    @Test
    fun `known stale secure-session failures require a fresh setup`() {
        assertTrue(
            ProvisioningFailurePolicy.isSecureSessionFailure(
                "BLE connection failed with status 147."
            )
        )
        assertTrue(
            ProvisioningFailurePolicy.isSecureSessionFailure(
                "Device ECDH public key is missing."
            )
        )
        assertTrue(
            ProvisioningFailurePolicy.isSecureSessionFailure(
                "error:1e000065:Cipher functions:OPENSSL_internal:BAD_DECRYPT"
            )
        )
        assertTrue(
            ProvisioningFailurePolicy.isSecureSessionFailure(
                "BLE GATT connection is not active."
            )
        )
    }

    @Test
    fun `wifi credential failure is not classified as stale secure session`() {
        assertFalse(
            ProvisioningFailurePolicy.isSecureSessionFailure(
                "Wi-Fi authentication failed."
            )
        )
    }

    @Test
    fun `runtime identity timeout is classified separately`() {
        assertTrue(
            ProvisioningFailurePolicy.isRuntimeConfirmationFailure(
                "Runtime identity and capabilities were not received before timeout."
            )
        )
    }
}
