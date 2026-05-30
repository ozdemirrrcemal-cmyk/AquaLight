package com.aqua.aqualight.ui.tabs.devices.detail.light.model

import com.aqua.aqualight.data.devices.light.model.LightOverviewSnapshot

object LightOverviewUiMapper {

    fun map(
        snapshot: LightOverviewSnapshot
    ): LightOverviewUiState {
        if (snapshot.isLoading) {
            return LightOverviewUiState.loading()
        }

        val peakRange =
            snapshot.curvePeakTimeRange.ifBlank {
                snapshot.curvePeakRange
            }

        val firmware =
            snapshot.firmwareVersion.ifBlank {
                snapshot.firmwareLabel
            }

        val hasTimelineData =
            snapshot.curveStartTime.isNotBlank() ||
                peakRange.isNotBlank() ||
                snapshot.curveSunsetTime.isNotBlank() ||
                snapshot.curveRampMinutes != null

        return LightOverviewUiState(
            isLoading = false,
            isOnline = snapshot.isOnline,
            isTimelineActive = hasTimelineData,

            connectionLabel = snapshot.connectionLabel.ifBlank {
                if (snapshot.isOnline) {
                    "Online · 4-channel WRGB"
                } else {
                    "Offline · WRGB"
                }
            },

            programTitle = snapshot.programTitle.ifBlank {
                "Current Program"
            },

            programSubtitle = snapshot.programSubtitle.ifBlank {
                if (snapshot.isOnline) {
                    "Auto mode active · Synced"
                } else {
                    "Waiting for device data"
                }
            },

            modeLabel = snapshot.modeLabel.ifBlank {
                if (snapshot.isOnline) {
                    "AUTO"
                } else {
                    "OFFLINE"
                }
            },

            currentOutputLabel = formatPercent(
                value = snapshot.currentOutputPercent
            ),

            redLabel = formatPercent(
                value = snapshot.redPercent
            ),

            greenLabel = formatPercent(
                value = snapshot.greenPercent
            ),

            blueLabel = formatPercent(
                value = snapshot.bluePercent
            ),

            whiteLabel = formatPercent(
                value = snapshot.whitePercent
            ),

            nowLabel = "Now · ${snapshot.nowLabel.ifBlank { LightOverviewUiState.NO_VALUE }}",

            nextLabel = "Next · ${snapshot.nextLabel.ifBlank { LightOverviewUiState.NO_VALUE }}",

            curveNowLabel = snapshot.curveNowLabel.ifBlank {
                if (hasTimelineData) {
                    "Now ${snapshot.deviceTime.ifBlank { LightOverviewUiState.NO_VALUE }} · ${
                        formatPercent(
                            value = snapshot.currentOutputPercent
                        )
                    }"
                } else {
                    "Waiting for curve data"
                }
            },

            timelineStartLabel = snapshot.timelineStartLabel.ifBlank {
                "00:00"
            },

            timelineMidLabel = snapshot.timelineMidLabel.ifBlank {
                "12:00"
            },

            timelineEndLabel = snapshot.timelineEndLabel.ifBlank {
                "24:00"
            },

            curveStartLabel = snapshot.curveStartTime.ifBlank {
                LightOverviewUiState.NO_VALUE
            },

            curvePeakLabel = peakRange.ifBlank {
                LightOverviewUiState.NO_VALUE
            },

            curveSunsetLabel = snapshot.curveSunsetTime.ifBlank {
                LightOverviewUiState.NO_VALUE
            },

            curveRampLabel = snapshot.curveRampMinutes
                ?.let { "$it min" }
                ?: LightOverviewUiState.NO_VALUE,

            activeProgramName = snapshot.activeProgramName.ifBlank {
                "Programs"
            },

            activeProgramSchedule = snapshot.activeProgramSchedule.ifBlank {
                "Waiting for program data"
            },

            activeProgramChannels = snapshot.activeProgramChannels,

            activeProgramStatusLabel = if (snapshot.isProgramEnabled) {
                "Active"
            } else {
                ""
            },

            healthLabel = snapshot.healthLabel.ifBlank {
                if (snapshot.isOnline) {
                    "Online · Synced"
                } else {
                    "Connecting"
                }
            },

            temperatureLabel = snapshot.temperatureC
                ?.let { "$it°C" }
                ?: LightOverviewUiState.NO_VALUE,

            fanLabel = snapshot.fanLabel.ifBlank {
                LightOverviewUiState.NO_VALUE
            },

            deviceTimeLabel = snapshot.deviceTime.ifBlank {
                LightOverviewUiState.NO_VALUE
            },

            firmwareLabel = firmware.ifBlank {
                LightOverviewUiState.NO_VALUE
            },

            isProgramEnabled = snapshot.isProgramEnabled
        )
    }

    fun toUiState(
        snapshot: LightOverviewSnapshot
    ): LightOverviewUiState {
        return map(
            snapshot = snapshot
        )
    }

    private fun formatPercent(
        value: Int?
    ): String {
        return value
            ?.coerceIn(
                minimumValue = 0,
                maximumValue = 100
            )
            ?.let { "$it%" }
            ?: LightOverviewUiState.NO_VALUE
    }
}

fun LightOverviewSnapshot.toUiState(): LightOverviewUiState {
    return LightOverviewUiMapper.map(
        snapshot = this
    )
}