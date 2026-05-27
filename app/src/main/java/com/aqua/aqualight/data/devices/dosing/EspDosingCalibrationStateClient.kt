package com.aqua.aqualight.data.devices.dosing

data class EspDosingChannelCalibrationState(
    val channelIndex: Int,
    val ye: Long,
    val dimension: String,
    val calibratedOnDevice: Boolean
)

object EspDosingCalibrationStateClient {

    suspend fun readChannelCalibrationState(
        deviceIp: String,
        channelIndex: Int
    ): EspDosingChannelCalibrationState? {
        val safeChannelIndex =
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )

        val requestJson =
            """
            {
              "LPWMChanelTimer": {
                "Data": {
                  "$safeChannelIndex": {
                    "YE": 0,
                    "Dimension": 0
                  }
                }
              }
            }
            """.trimIndent()

        val root =
            EspDosingHttpClient.getJson(
                deviceIp = deviceIp,
                requestJson = requestJson
            ) ?: return null

        val channelJson =
            root.optJSONObject("LPWMChanelTimer")
                ?.optJSONObject("Data")
                ?.optJSONObject(safeChannelIndex.toString())
                ?: return null

        val ye =
            channelJson.optLong(
                "YE",
                -1L
            )

        val dimension =
            channelJson.optString(
                "Dimension",
                ""
            )

        return EspDosingChannelCalibrationState(
            channelIndex = safeChannelIndex,
            ye = ye,
            dimension = dimension,
            calibratedOnDevice = ye >= 1L
        )
    }
}