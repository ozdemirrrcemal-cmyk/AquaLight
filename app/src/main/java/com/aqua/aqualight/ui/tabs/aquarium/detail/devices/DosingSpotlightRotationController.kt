package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the Device-card channel carousel as presentation state only.
 *
 * Visibility controls the ticker lifetime. Hiding the surface cancels the ticker and resets every
 * device to its first channel; becoming visible starts a fresh full interval from channel one.
 * Firmware/state refresh remains completely outside this controller.
 */
internal class DosingSpotlightRotationController(
    private val scope: CoroutineScope,
    private val intervalMillis: Long = DEFAULT_ROTATION_INTERVAL_MILLIS
) {
    private val _indices = MutableStateFlow<Map<String, Int>>(emptyMap())
    val indices: StateFlow<Map<String, Int>> = _indices.asStateFlow()

    private var channelCounts: Map<String, Int> = emptyMap()
    private var visible: Boolean = false
    private var rotationJob: Job? = null

    fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible

        if (visible) {
            resetToFirstChannel()
            reconcileTicker()
        } else {
            stopTicker()
            resetToFirstChannel()
        }
    }

    fun updateChannelCounts(counts: Map<String, Int>) {
        channelCounts = counts.filterValues { count -> count > 0 }
        _indices.update { current ->
            buildMap {
                channelCounts.forEach { (deviceUid, count) ->
                    put(
                        deviceUid,
                        (current[deviceUid] ?: FIRST_CHANNEL_INDEX)
                            .coerceIn(FIRST_CHANNEL_INDEX, count - 1)
                    )
                }
            }
        }
        reconcileTicker()
    }

    private fun reconcileTicker() {
        val shouldRotate = visible && channelCounts.values.any { count -> count > 1 }
        if (!shouldRotate) {
            stopTicker()
            return
        }
        if (rotationJob?.isActive == true) return

        rotationJob = scope.launch {
            while (isActive) {
                delay(intervalMillis)
                _indices.update { current ->
                    buildMap {
                        channelCounts.forEach { (deviceUid, count) ->
                            val nextIndex = if (count <= 1) {
                                FIRST_CHANNEL_INDEX
                            } else {
                                ((current[deviceUid] ?: FIRST_CHANNEL_INDEX) + 1) % count
                            }
                            put(deviceUid, nextIndex)
                        }
                    }
                }
            }
        }
    }

    private fun stopTicker() {
        rotationJob?.cancel()
        rotationJob = null
    }

    private fun resetToFirstChannel() {
        _indices.value = channelCounts.keys.associateWith { FIRST_CHANNEL_INDEX }
    }

    private companion object {
        const val FIRST_CHANNEL_INDEX = 0
        const val DEFAULT_ROTATION_INTERVAL_MILLIS = 10_000L
    }
}
