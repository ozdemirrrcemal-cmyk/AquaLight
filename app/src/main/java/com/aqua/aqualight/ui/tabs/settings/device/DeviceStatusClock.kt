package com.aqua.aqualight.ui.tabs.settings.device

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/** UI clock used only to refresh relative last-seen labels. */
interface DeviceStatusClock {
    val ticks: Flow<Long>
}

internal class SystemDeviceStatusClock(
    private val nowMillis: () -> Long = System::currentTimeMillis
) : DeviceStatusClock {

    override val ticks: Flow<Long> = flow {
        emit(nowMillis())
        while (currentCoroutineContext().isActive) {
            delay(LAST_SEEN_TICK_MS)
            emit(nowMillis())
        }
    }

    private companion object {
        const val LAST_SEEN_TICK_MS = 15_000L
    }
}
