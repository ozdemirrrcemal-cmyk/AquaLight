package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingHistoryContractTest {

    @Test
    fun `history payload emits canonical range`() {
        val payload = DeviceCoolingHistoryGetPayload(DeviceCoolingHistoryRange.DAYS_7).toJson()

        assertEquals(setOf("range"), payload.keys().asSequence().toSet())
        assertEquals("7d", payload.getString("range"))
    }

    @Test
    fun `history parser accepts samples summary and daily aggregates`() {
        val parsed = DeviceCoolingHistoryParser.parse(validHistory())

        assertEquals(DeviceCoolingHistoryRange.HOURS_24, parsed.range)
        assertEquals(1_000L, parsed.generatedAtMs)
        assertEquals(23.4, parsed.summary.minimumTemperatureC!!, 0.0001)
        assertEquals(24.8, parsed.summary.averageTemperatureC!!, 0.0001)
        assertEquals(27.1, parsed.summary.maximumTemperatureC!!, 0.0001)
        assertEquals(3, parsed.samples.size)
        assertEquals(24.6, parsed.samples.last().temperatureC, 0.0001)
        assertEquals(1, parsed.days.size)
        assertEquals(24.7, parsed.days.single().averageTemperatureC, 0.0001)
    }

    @Test
    fun `history parser accepts fully empty summary without inventing values`() {
        val history = validHistory()
        history.put(
            "summary",
            JSONObject()
                .put("minTemperatureC", JSONObject.NULL)
                .put("avgTemperatureC", JSONObject.NULL)
                .put("maxTemperatureC", JSONObject.NULL)
        )

        val parsed = DeviceCoolingHistoryParser.parse(history)

        assertNull(parsed.summary.minimumTemperatureC)
        assertNull(parsed.summary.averageTemperatureC)
        assertNull(parsed.summary.maximumTemperatureC)
    }

    @Test
    fun `history parser rejects partial summary unordered samples and invalid aggregates`() {
        val partialSummary = validHistory().put(
            "summary",
            JSONObject()
                .put("minTemperatureC", 23.0)
                .put("avgTemperatureC", JSONObject.NULL)
                .put("maxTemperatureC", 27.0)
        )
        val unordered = validHistory().put(
            "samples",
            JSONArray()
                .put(sample(2_000L, 24.0))
                .put(sample(1_000L, 24.2))
        )
        val invalidDay = validHistory().put(
            "days",
            JSONArray().put(
                JSONObject()
                    .put("dayStartAtMs", 100L)
                    .put("minTemperatureC", 26.0)
                    .put("avgTemperatureC", 25.0)
                    .put("maxTemperatureC", 27.0)
            )
        )

        assertTrue(runCatching { DeviceCoolingHistoryParser.parse(partialSummary) }.isFailure)
        assertTrue(runCatching { DeviceCoolingHistoryParser.parse(unordered) }.isFailure)
        assertTrue(runCatching { DeviceCoolingHistoryParser.parse(invalidDay) }.isFailure)
    }

    private fun validHistory(): JSONObject = JSONObject()
        .put("range", "24h")
        .put("generatedAtMs", 1_000L)
        .put(
            "summary",
            JSONObject()
                .put("minTemperatureC", 23.4)
                .put("avgTemperatureC", 24.8)
                .put("maxTemperatureC", 27.1)
        )
        .put(
            "samples",
            JSONArray()
                .put(sample(100L, 24.1))
                .put(sample(200L, 25.2))
                .put(sample(300L, 24.6))
        )
        .put(
            "days",
            JSONArray().put(
                JSONObject()
                    .put("dayStartAtMs", 50L)
                    .put("minTemperatureC", 23.9)
                    .put("avgTemperatureC", 24.7)
                    .put("maxTemperatureC", 26.2)
            )
        )

    private fun sample(sampledAtMs: Long, temperatureC: Double): JSONObject = JSONObject()
        .put("sampledAtMs", sampledAtMs)
        .put("temperatureC", temperatureC)
}
