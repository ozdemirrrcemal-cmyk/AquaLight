package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareStrictContractResidueTest {

    @Test
    fun `phase parsing has no trimming alias or unknown fallback`() {
        assertNull(DeviceFirmwareOtaPhase.fromWireExact(" failed "))
        assertNull(DeviceFirmwareOtaPhase.fromWireExact("FAILED"))
        assertNull(DeviceFirmwareOtaPhase.fromWireExact("unknown"))
    }

    @Test
    fun `status parsing rejects unknown phase instead of retaining compatibility state`() {
        val result = DeviceFirmwareStatusParser.parseOtaSnapshotExact(
            idleSnapshot().put("phase", "unknown")
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `status parsing rejects extra compatibility fields`() {
        val result = DeviceFirmwareStatusParser.parseOtaSnapshotExact(
            idleSnapshot().put("legacyProgress", 0)
        )

        assertTrue(result.isFailure)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsent command cannot exist without structured failure`() {
        DeviceFirmwareCommandResult(sent = false)
    }

    private fun idleSnapshot(): JSONObject = JSONObject()
        .put("phase", "idle")
        .put("active", false)
        .put("restartRequired", false)
        .put("restartScheduled", false)
        .put("allowInsecureHttp", false)
        .put("startedAtMs", 0L)
        .put("finishedAtMs", 0L)
        .put("bytesWritten", 0L)
        .put("contentLength", 0L)
        .put("progressPermille", 0)
        .put("progressPercent", 0.0)
        .put("targetVersion", "")
        .put("sha256Expected", "")
        .put("sha256Actual", "")
        .put("lastError", "")
        .put("lastErrorField", "")
        .put("urlScheme", "")
        .put("httpStatus", 0)
}
