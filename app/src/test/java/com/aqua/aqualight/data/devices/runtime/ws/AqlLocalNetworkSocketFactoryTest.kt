package com.aqua.aqualight.data.devices.runtime.ws

import java.net.SocketException
import org.junit.Assert.assertThrows
import org.junit.Test

class AqlLocalNetworkSocketFactoryTest {

    @Test
    fun `runtime socket creation fails when no local network is selected`() {
        val factory = AqlLocalNetworkSocketFactory(networkProvider = { null })

        assertThrows(SocketException::class.java) {
            factory.createSocket()
        }
    }
}
