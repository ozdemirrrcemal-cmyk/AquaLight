package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model

data class LightManualUiState(
    val isLoading: Boolean = false,
    val isOnline: Boolean = true,

    val masterPercent: Int = 78,
    val redPercent: Int = 80,
    val greenPercent: Int = 84,
    val bluePercent: Int = 79,
    val whitePercent: Int = 65,

    val isApplyToProgramEnabled: Boolean = false
) {

    val safeMasterPercent: Int
        get() = masterPercent.coerceIn(0, MAX_PERCENT)

    val safeRedPercent: Int
        get() = redPercent.coerceIn(0, MAX_PERCENT)

    val safeGreenPercent: Int
        get() = greenPercent.coerceIn(0, MAX_PERCENT)

    val safeBluePercent: Int
        get() = bluePercent.coerceIn(0, MAX_PERCENT)

    val safeWhitePercent: Int
        get() = whitePercent.coerceIn(0, MAX_PERCENT)

    val masterLabel: String
        get() = formatPercent(safeMasterPercent)

    val redLabel: String
        get() = formatPercent(safeRedPercent)

    val greenLabel: String
        get() = formatPercent(safeGreenPercent)

    val blueLabel: String
        get() = formatPercent(safeBluePercent)

    val whiteLabel: String
        get() = formatPercent(safeWhitePercent)

    val effectiveAveragePercent: Int
        get() {
            val channelAverage =
                (safeRedPercent + safeGreenPercent + safeBluePercent + safeWhitePercent) / 4f

            return (channelAverage * (safeMasterPercent / 100f))
                .toInt()
                .coerceIn(0, MAX_PERCENT)
        }

    val powerStateLabel: String
        get() {
            return when {
                isLoading -> "SYNCING"
                !isOnline -> "OFFLINE"
                safeMasterPercent <= 0 || effectiveAveragePercent <= 5 -> "OFF"
                else -> "LIVE"
            }
        }

    val previewAppearanceLabel: String
        get() {
            return when {
                safeMasterPercent <= 0 || effectiveAveragePercent <= 5 -> {
                    "Estimated appearance: Lights off"
                }

                safeBluePercent > safeRedPercent + 20 -> {
                    "Estimated appearance: Cool blue display"
                }

                safeRedPercent > safeBluePercent + 20 -> {
                    "Estimated appearance: Warm evening tone"
                }

                safeWhitePercent >= 70 && effectiveAveragePercent >= 70 -> {
                    "Estimated appearance: Bright daylight"
                }

                effectiveAveragePercent >= 45 -> {
                    "Estimated appearance: Neutral daylight"
                }

                else -> {
                    "Estimated appearance: Soft low light"
                }
            }
        }

    companion object {
        const val MAX_PERCENT = 100

        fun preview(): LightManualUiState {
            return LightManualUiState()
        }

        private fun formatPercent(
            value: Int
        ): String {
            return "${value.coerceIn(0, MAX_PERCENT)}%"
        }
    }
}