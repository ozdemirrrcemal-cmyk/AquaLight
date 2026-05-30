package com.aqua.aqualight.ui.tabs.devices.detail.light.model

import com.aqua.aqualight.data.devices.light.model.LightOverviewSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.LightOverviewUiState.Companion.NO_VALUE

object LightOverviewUiMapper {

    fun map(
        snapshot: LightOverviewSnapshot
    ): LightOverviewUiState {
        return LightOverviewUiState(
            connectionLabel = snapshot.connectionLabel.ifBlank {
                if (snapshot.isOnline) {
                    "Online · 4-channel WRGB"
                } else {
                    "Connecting · WRGB"
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
                    "SYNCING"
                }
            },

            currentOutputLabel = percentLabel(snapshot.currentOutputPercent),

            redLabel = percentLabel(snapshot.redPercent),
            greenLabel = percentLabel(snapshot.greenPercent),
            blueLabel = percentLabel(snapshot.bluePercent),
            whiteLabel = percentLabel(snapshot.whitePercent),

            nowLabel = "Now · ${snapshot.nowLabel.ifBlank { NO_VALUE }}",
            nextLabel = "Next · ${snapshot.nextLabel.ifBlank { NO_VALUE }}",

            curveNowLabel = snapshot.curveNowLabel.ifBlank {
                if (snapshot.deviceTime.isNotBlank() || snapshot.currentOutputPercent != null) {
                    "Now ${snapshot.deviceTime.ifBlank { NO_VALUE }} · ${percentLabel(snapshot.currentOutputPercent)}"
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
                NO_VALUE
            },
            curvePeakLabel = snapshot.curvePeakTimeRange.ifBlank {
                NO_VALUE
            },
            curveSunsetLabel = snapshot.curveSunsetTime.ifBlank {
                NO_VALUE
            },
            curveRampLabel = snapshot.curveRampMinutes
                ?.takeIf { it >= 0 }
                ?.let { "$it min" }
                ?: NO_VALUE,

            activeProgramName = snapshot.activeProgramName.ifBlank {
                NO_VALUE
            },
            activeProgramSchedule = snapshot.activeProgramSchedule.ifBlank {
                NO_VALUE
            },
            activeProgramChannels = snapshot.activeProgramChannels.ifBlank {
                channelSummary(
                    red = snapshot.redPercent,
                    green = snapshot.greenPercent,
                    blue = snapshot.bluePercent,
                    white = snapshot.whitePercent
                )
            },
            activeProgramStatusLabel = if (snapshot.isProgramEnabled) {
                "Active"
            } else {
                "Off"
            },

            healthLabel = snapshot.healthLabel.ifBlank {
                if (snapshot.isOnline) {
                    "Online"
                } else {
                    "Waiting for device data"
                }
            },

            temperatureLabel = snapshot.temperatureC
                ?.let { "$it°C" }
                ?: NO_VALUE,

            fanLabel = snapshot.fanLabel.ifBlank {
                NO_VALUE
            },

            deviceTimeLabel = snapshot.deviceTime.ifBlank {
                NO_VALUE
            },

            firmwareLabel = snapshot.firmware.ifBlank {
                NO_VALUE
            },

            isProgramEnabled = snapshot.isProgramEnabled
        )
    }

    private fun percentLabel(
        value: Int?
    ): String {
        return value
            ?.coerceIn(0, 100)
            ?.let { "$it%" }
            ?: NO_VALUE
    }

    private fun channelSummary(
        red: Int?,
        green: Int?,
        blue: Int?,
        white: Int?
    ): String {
        if (
            red == null &&
            green == null &&
            blue == null &&
            white == null
        ) {
            return NO_VALUE
        }

        return "R${plainPercent(red)}  G${plainPercent(green)}  B${plainPercent(blue)}  W${plainPercent(white)}"
    }

    private fun plainPercent(
        value: Int?
    ): String {
        return value
            ?.coerceIn(0, 100)
            ?.toString()
            ?: NO_VALUE
    }
}