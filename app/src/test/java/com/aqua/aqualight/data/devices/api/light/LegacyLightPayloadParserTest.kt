package com.aqua.aqualight.data.devices.api.light

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LegacyLightPayloadParserTest {

    @Test
    fun `legacy lp parser keeps 24 hour endpoint`() {
        val payload = """
            {
              "Time": { "TimeCurrent": "12:00" },
              "LPWMChanelLED": {
                "Data": {
                  "0": { "Name": "White", "GPIO_PWM": "0", "Regime": "Auto" }
                }
              },
              "LLight": {
                "Data": {
                  "0": {
                    "GPIO_PWM": "0",
                    "LP": [["08:00", 0.0], ["12:00", 0.8], ["24:00", 0.0]]
                  }
                }
              }
            }
        """.trimIndent()

        val state = LegacyLightPayloadParser()
            .parseDeviceState(payload)
            .getOrNull()

        assertNotNull(state)
        assertEquals(
            listOf(8 * 60, 12 * 60, 24 * 60),
            state!!.scheduleChannels.single().points.map { point -> point.minuteOfDay }
        )
    }
}
