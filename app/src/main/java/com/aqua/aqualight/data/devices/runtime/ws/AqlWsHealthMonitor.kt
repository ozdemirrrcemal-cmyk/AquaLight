package com.aqua.aqualight.data.devices.runtime.ws

class AqlWsHealthMonitor(
    private val commandClient: AqlWsCommandClient,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private var lastPingAtMillis: Long = 0L

    fun tick(): Boolean {
        val now = clockMillis()
        if (now - lastPingAtMillis < PING_INTERVAL_MS) {
            return false
        }
        lastPingAtMillis = now
        return commandClient.ping()
    }

    companion object {
        const val PING_INTERVAL_MS = 20_000L
    }
}
