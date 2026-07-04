package com.aqua.aqualight.data.devices.provisioning.store

/**
 * Small in-memory cache for QR setup sessions.
 *
 * QR labels carry a stable BLE setup name, not the Android runtime MAC address.
 * Once Android resolves AQL-SETUP-xxxxxx to a MAC, keep it for the current app
 * process so Back -> Continue or a wrong-password retry does not require a new
 * scan response before the secure DeviceInfo check runs again.
 */
object AqlProvisioningBleAddressCache {
    private const val MAX_ITEMS = 16
    private val lock = Any()
    private val addresses = linkedMapOf<String, String>()

    fun put(bleName: String, bleAddress: String) {
        val name = bleName.trim()
        val address = bleAddress.trim()
        if (name.isBlank() || address.isBlank()) return
        synchronized(lock) {
            addresses[name] = address
            while (addresses.size > MAX_ITEMS) {
                addresses.remove(addresses.keys.firstOrNull() ?: return@synchronized)
            }
        }
    }

    fun get(bleName: String): String {
        val name = bleName.trim()
        if (name.isBlank()) return ""
        return synchronized(lock) {
            addresses[name].orEmpty()
        }
    }

    fun clear() = synchronized(lock) { addresses.clear() }
}
