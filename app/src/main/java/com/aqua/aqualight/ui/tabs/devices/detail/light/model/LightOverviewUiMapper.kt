package com.aqua.aqualight.ui.tabs.devices.detail.light.model

import com.aqua.aqualight.data.devices.light.model.LightOverviewSnapshot

object LightOverviewUiMapper {

    fun map(
        snapshot: LightOverviewSnapshot
    ): LightOverviewUiState {
        val isLoading =
            snapshot.isLoading

        return LightOverviewUiState(
            isLoading = isLoading,
            isOnline = snapshot.isOnline,

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
                if (isLoading) {
                    "Waiting for device data"
                } else {
                    "Auto mode active · Synced"
                }
            },

            modeLabel = snapshot.modeLabel.ifBlank {
                if (isLoading) {
                    "SYNCING"
                } else {
                    "AUTO"
                }
            },

            currentOutputLabel = percentLabel(
                value = snapshot.currentOutputPercent
            ),

            redLabel = percentLabel(
                value = snapshot.redPercent
            ),

            greenLabel = percentLabel(
                value = snapshot.greenPercent
            ),

            blueLabel = percentLabel(
                value = snapshot.bluePercent
            ),

            whiteLabel = percentLabel(
                value = snapshot.whitePercent
            ),

            nowLabel = "Now · ${snapshot.nowLabel.ifBlank { LightOverviewUiState.NO_VALUE }}",

            nextLabel = "Next · ${snapshot.nextLabel.ifBlank { LightOverviewUiState.NO_VALUE }}",

            curveNowLabel = snapshot.curveNowLabel.ifBlank {
                if (isLoading) {
                    "Waiting for curve data"
                } else {
                    LightOverviewUiState.NO_VALUE
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

            curvePeakLabel = snapshot.curvePeakRange.ifBlank {
                LightOverviewUiState.NO_VALUE
            },

            curveSunsetLabel = snapshot.curveSunsetTime.ifBlank {
                LightOverviewUiState.NO_VALUE
            },

            curveRampLabel = snapshot.curveRampMinutes?.let {
                "$it min"
            } ?: LightOverviewUiState.NO_VALUE,

            activeProgramName = if (isLoading) {
                "Waiting for program data"
            } else {
                snapshot.activeProgramName.ifBlank {
                    LightOverviewUiState.NO_VALUE
                }
            },

            activeProgramSchedule = if (isLoading) {
                ""
            } else {
                snapshot.activeProgramSchedule.ifBlank {
                    LightOverviewUiState.NO_VALUE
                }
            },

            activeProgramChannels = if (isLoading) {
                ""
            } else {
                snapshot.activeProgramChannels.ifBlank {
                    LightOverviewUiState.NO_VALUE
                }
            },

            activeProgramStatusLabel = if (isLoading) {
                ""
            } else if (snapshot.isProgramEnabled) {
                "Active"
            } else {
                "Off"
            },

            isProgramEnabled = snapshot.isProgramEnabled,

            healthLabel = snapshot.healthLabel.ifBlank {
                if (isLoading) {
                    "Waiting for device data"
                } else {
                    LightOverviewUiState.NO_VALUE
                }
            },

            temperatureLabel = snapshot.temperatureC?.let {
                "$it°C"
            } ?: LightOverviewUiState.NO_VALUE,

            fanLabel = snapshot.fanLabel.ifBlank {
                LightOverviewUiState.NO_VALUE
            },

            deviceTimeLabel = snapshot.deviceTime.ifBlank {
                LightOverviewUiState.NO_VALUE
            },

            firmwareLabel = snapshot.firmwareLabel.ifBlank {
                LightOverviewUiState.NO_VALUE
            }
        )
    }

    private fun percentLabel(
        value: Int?
    ): String {
        return value
            ?.coerceIn(
                minimumValue = 0,
                maximumValue = 100
            )
            ?.let {
                "$it%"
            }
            ?: LightOverviewUiState.NO_VALUE
    }
}