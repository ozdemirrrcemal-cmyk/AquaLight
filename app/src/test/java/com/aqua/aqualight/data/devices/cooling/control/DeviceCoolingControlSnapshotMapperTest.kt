package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmCode
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingPowerSource
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingPwmOutputHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorKind
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorReadingHealth
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ResponseParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingControlSnapshotMapperTest {

    @Test
    fun `continuous firmware output remains exact across the application boundary`() {
        val status = DeviceCoolingV1ResponseParser.parseStatus(statusJson())
        val runtimePercent = status.control.targetPercent

        val snapshot = DeviceCoolingControlSnapshotMapper.map(
            DeviceCoolingRuntimeState(
                authoritative = true,
                status = status,
                config = status.config,
                telemetry = status.telemetry
            )
        )

        assertNotNull(snapshot)
        assertTrue(runtimePercent % 1.0 != 0.0)
        assertEquals(runtimePercent, snapshot?.targetFanPercent ?: 0.0, 0.0)
        assertEquals(runtimePercent, snapshot?.actualFanPercent ?: 0.0, 0.0)

        val telemetry = requireNotNull(snapshot?.telemetry)
        val fan = requireNotNull(telemetry.fan)
        assertEquals(DeviceCoolingPwmOutputHealth.OK, fan.pwmOutputHealth)
        assertEquals(runtimePercent, fan.targetPercent, 0.0)
        assertEquals(runtimePercent, fan.outputPercent ?: 0.0, 0.0)
        assertFalse(fan.rpmAvailable)
        assertNull(fan.rpm)
        assertEquals(
            listOf(DeviceCoolingSensorKind.WATER, DeviceCoolingSensorKind.AMBIENT),
            telemetry.sensors.map { sensor -> sensor.kind }
        )
        assertTrue(telemetry.sensors.all { sensor ->
            sensor.health == DeviceCoolingSensorReadingHealth.OK
        })
        assertEquals(DeviceCoolingPowerSource.ESTIMATED, telemetry.power?.source)
        assertTrue(telemetry.power?.available == true)
    }

    @Test
    fun `continuous status target remains exact before live telemetry arrives`() {
        val status = DeviceCoolingV1ResponseParser.parseStatus(statusJson())

        val snapshot = DeviceCoolingControlSnapshotMapper.map(
            DeviceCoolingRuntimeState(
                authoritative = true,
                status = status,
                config = status.config,
                telemetry = null
            )
        )

        assertNotNull(snapshot)
        assertEquals(status.control.targetPercent, snapshot?.targetFanPercent ?: 0.0, 0.0)
        assertNull(snapshot?.actualFanPercent)
    }

    @Test
    fun `unknown firmware alarm retains complete support diagnostics`() {
        val json = statusJson()
        json.getJSONArray("alarms").put(
            JSONObject()
                .put("code", "FUTURE_COOLING_FAULT")
                .put("severity", "WARNING")
                .put("active", true)
                .put("latched", true)
                .put("affectedKey", "future-component")
                .put("reason", "FUTURE_REASON")
        )
        json.getJSONObject("healthSummary")
            .put("activeAlarmCount", 1)
            .put("highestAlarmSeverity", "WARNING")
        val status = DeviceCoolingV1ResponseParser.parseStatus(json)

        val snapshot = requireNotNull(
            DeviceCoolingControlSnapshotMapper.map(
                DeviceCoolingRuntimeState(
                    authoritative = true,
                    status = status,
                    config = status.config,
                    telemetry = status.telemetry
                )
            )
        )
        val alarm = requireNotNull(snapshot.telemetry).alarms.single()

        assertEquals(DeviceCoolingAlarmCode.UNKNOWN, alarm.code)
        assertEquals("FUTURE_COOLING_FAULT", alarm.diagnosticCode)
        assertEquals("future-component", alarm.affectedKey)
        assertEquals("FUTURE_REASON", alarm.diagnosticReason)
        assertTrue(alarm.active)
        assertTrue(alarm.latched)
    }

    private fun statusJson(): JSONObject = JSONObject(
        requireNotNull(javaClass.classLoader?.getResourceAsStream(STATUS_FIXTURE)) {
            "Missing fixture resource: $STATUS_FIXTURE"
        }.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
    )

    private companion object {
        const val STATUS_FIXTURE = "aql_cooling_status_v1.json"
    }
}
