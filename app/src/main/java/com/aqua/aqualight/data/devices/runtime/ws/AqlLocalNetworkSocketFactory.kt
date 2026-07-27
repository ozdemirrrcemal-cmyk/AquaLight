package com.aqua.aqualight.data.devices.runtime.ws

import android.net.Network
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Creates every new runtime socket from the currently selected Android local Network.
 *
 * OkHttp resolves the app-owned synthetic hostname through [AqlPrivateLanDns] and then asks this
 * factory for the raw socket. The socket therefore stays on Wi-Fi/Ethernet even when VPN or mobile
 * data is the process default route.
 */
internal class AqlLocalNetworkSocketFactory(
    private val networkProvider: () -> Network?
) : SocketFactory() {

    override fun createSocket(): Socket = delegate().createSocket()

    override fun createSocket(host: String, port: Int): Socket =
        delegate().createSocket(host, port)

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int
    ): Socket = delegate().createSocket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress, port: Int): Socket =
        delegate().createSocket(host, port)

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket = delegate().createSocket(address, port, localAddress, localPort)

    private fun delegate(): SocketFactory {
        return networkProvider()?.socketFactory ?: getDefault()
    }
}
