package com.aqua.aqualight.data.devices.discovery.udp

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Resolves candidate IPv4 broadcast addresses for LAN discovery refresh packets.
 *
 * Android devices can move between Wi-Fi, hotspot and Ethernet networks. Using the interface
 * broadcast address first is more reliable than sending only to 255.255.255.255, but the global
 * broadcast address remains as a fallback for simple home networks.
 */
object AqlBroadcastAddressResolver {

    private const val GLOBAL_BROADCAST = "255.255.255.255"

    fun resolve(): List<InetAddress> {
        val resolved = linkedSetOf<InetAddress>()

        runCatching {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.interfaceAddresses.asSequence() }
                ?.mapNotNull { it.broadcast }
                ?.filterIsInstance<Inet4Address>()
                ?.forEach { resolved += it }
        }

        resolved += InetAddress.getByName(GLOBAL_BROADCAST)
        return resolved.toList()
    }
}
