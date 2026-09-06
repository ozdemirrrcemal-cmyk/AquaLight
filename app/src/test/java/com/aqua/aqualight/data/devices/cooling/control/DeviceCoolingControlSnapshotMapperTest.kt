package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ResponseParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

    private fun statusJson(): JSONObject = JSONObject(
        requireNotNull(javaClass.classLoader?.getResourceAsStream(STATUS_FIXTURE)) {
            "Missing fixture resource: $STATUS_FIXTURE"
        }.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
    )

    private companion object {
        const val STATUS_FIXTURE = "aql_cooling_status_v1.json"
    }
}
