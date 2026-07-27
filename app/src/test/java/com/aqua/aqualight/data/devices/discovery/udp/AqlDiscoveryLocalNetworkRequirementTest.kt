package com.aqua.aqualight.data.devices.discovery.udp

import java.net.InetAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class AqlDiscoveryLocalNetworkRequirementTest {

    @Test
    fun `production refresh does not fall back to process default network`() = runTest {
        val sender = AqlDiscoveryRefreshSender(
            addressResolver = {
                listOf(
                    InetAddress.getByAddress(
                        byteArrayOf(192.toByte(), 168.toByte(), 1, 255.toByte())
                    )
                )
            },
            networkProvider = { null },
            requireLocalNetwork = true
        )

        val result = sender.sendRefresh()

        assertFalse(result.hasSuccess)
        assertEquals(1, result.attemptedAddressCount)
        assertEquals(0, result.sentAddressCount)
        assertNotNull(result.lastErrorMessage)
    }
}
