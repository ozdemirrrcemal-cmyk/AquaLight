package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.MessageDigest
import okhttp3.Dns

internal data class AqlPrivateLanRoute(
    val url: String,
    val syntheticHostname: String,
    val addressBytes: ByteArray
)

/**
 * Maps one verified private-LAN IP to an app-owned synthetic hostname.
 *
 * Android's network-security config can allow cleartext for this hostname tree
 * without globally allowing cleartext or attempting to express dynamic CIDRs.
 */
internal object AqlPrivateLanEndpoint {
    private const val HOST_SUFFIX = ".device.aql.local"

    fun route(
        deviceUid: DeviceUid,
        endpoint: DeviceRuntimeEndpoint
    ): AqlPrivateLanRoute? {
        if (!endpoint.hasWebSocketEndpoint) return null
        val address = endpoint.privateLanAddressBytes() ?: return null
        val host = syntheticHostname(deviceUid)
        return AqlPrivateLanRoute(
            url = "ws://$host:${endpoint.wsPort}${endpoint.wsPath}",
            syntheticHostname = host,
            addressBytes = address
        )
    }

    private fun syntheticHostname(deviceUid: DeviceUid): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(deviceUid.value.trim().lowercase().toByteArray(Charsets.UTF_8))
        val prefix = digest.take(HOST_HASH_BYTES).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        return "aql-$prefix$HOST_SUFFIX"
    }

    private const val HOST_HASH_BYTES = 12
}

internal class AqlPrivateLanDns(
    private val route: AqlPrivateLanRoute
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname != route.syntheticHostname) {
            throw UnknownHostException("Host is outside the AquaLight private-LAN route.")
        }
        return listOf(
            InetAddress.getByAddress(
                route.syntheticHostname,
                route.addressBytes.copyOf()
            )
        )
    }
}
