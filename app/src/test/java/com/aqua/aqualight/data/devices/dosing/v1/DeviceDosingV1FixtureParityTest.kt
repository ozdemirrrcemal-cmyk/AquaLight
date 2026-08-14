package com.aqua.aqualight.data.devices.dosing.v1

import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("MagicNumber")
class DeviceDosingV1FixtureParityTest {
    @Test
    fun `canonical firmware fixtures remain byte identical to the pinned revision`() {
        EXPECTED_SHA256.forEach { (name, expected) ->
            assertEquals(name, expected, sha256(resourceBytes(name)))
        }
    }

    @Test
    fun `pin records final firmware commit fourteen actions and no production wiring`() {
        val pin = resourceJson(PIN_FIXTURE)
        val firmware = pin.getJSONObject("firmware")
        val sources = firmware.getJSONObject("sources")
        val contract = pin.getJSONObject("contract")
        val actions = contract.getJSONArray("authenticatedActions")

        assertEquals(FIRMWARE_COMMIT, firmware.getString("commit"))
        assertEquals(7, sources.length())
        assertEquals(
            "915e0e53f858a7651ddfbc5ef401bcc101c954b8",
            sources.getString("src/modules/dosing/AqlDosingRuntimeService.hpp")
        )
        assertEquals(DeviceDosingV1Contract.SCHEMA, contract.getString("schema"))
        assertEquals(DeviceDosingV1Contract.SCHEMA_VERSION, contract.getLong("schemaVersion"))
        assertFalse(contract.getBoolean("productionWiring"))
        assertEquals(14, actions.length())
        assertEquals(
            DeviceDosingV1Contract.Action.ALL.map { action -> "dosing." + action },
            List(actions.length(), actions::getString)
        )
    }

    @Test
    fun `program apply fixture matches the final handler response`() {
        val fixture = resourceJson("aql_dosing_program_v1.json")
        val successFields = fixture
            .getJSONObject("programApply")
            .getJSONArray("successFields")

        assertEquals(
            listOf("operation", "channelKey", "saved", "event", "channel"),
            List(successFields.length(), successFields::getString)
        )
        assertEquals(36_900_000L, fixture
            .getJSONObject("examples")
            .getJSONObject("hourly24")
            .getJSONObject("config")
            .getLong("startTimeMs"))
    }

    @Test
    fun `calibration fixture uses only the final v1 fields`() {
        val fixture = resourceJson("aql_dosing_calibration_v1.json")
        val status = fixture.getJSONObject("statusShape").getJSONObject("calibration")

        assertEquals(DeviceDosingV1Contract.SCHEMA, fixture.getString("schema"))
        assertFalse(status.has("startedAtUptimeMs"))
        assertFalse(status.has("verificationDoseRemainingMs"))
        assertTrue(status.has("verificationDoseStarted"))
        assertTrue(status.has("verificationDoseComplete"))
    }

    @Test
    fun `worst case firmware fixtures retain required response headroom`() {
        val progress = resourceJson("aql_dosing_progress_budget_v1.json")
        val status = resourceJson("aql_dosing_status_budget_v1.json")
        val limit = DeviceDosingV1Contract.Limit.MAX_RESPONSE_BYTES
        val headroom = DeviceDosingV1Contract.Limit.MIN_RESPONSE_HEADROOM_BYTES

        assertTrue(progress.getInt("expectedSerializedBytes") + headroom <= limit)
        assertTrue(
            status.getJSONObject("globalStatus").getInt("expectedSerializedBytes") +
                headroom <= limit
        )
        assertTrue(
            status.getJSONObject("channelStatus").getInt("expectedSerializedBytes") +
                headroom <= limit
        )
        assertTrue(
            status.getJSONObject("channelStatus").getInt("expectedTimerSerializedBytes") +
                headroom <= limit
        )
    }

    private fun resourceJson(name: String): JSONObject =
        JSONObject(resourceBytes(name).toString(Charsets.UTF_8))

    private fun resourceBytes(name: String): ByteArray =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "Missing fixture resource: " + name
        }.use { input -> input.readBytes() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }

    private companion object {
        const val PIN_FIXTURE = "aql_android_dosing_v1_pin.json"
        const val FIRMWARE_COMMIT = "c77d191398b4bca1d24be99699d1a8fe17ac3dfb"
        val EXPECTED_SHA256 = linkedMapOf(
            "aql_dosing_calibration_v1.json" to
                "6b691f99d92e1740ea2efd98c3c20cc4cea309fd703f8db430241e2fca385fe9",
            "aql_dosing_persistence_v1.json" to
                "767a9e08e6cf71a221470958b616790f3ddd223dfd77033fde03708899fea880",
            "aql_dosing_program_v1.json" to
                "caa84b8484e47ad67b59748d9e18a5ff09b3266d2b439d463a250b283808c3b1",
            "aql_dosing_progress_budget_v1.json" to
                "42c84529351a2aee5b6d29ae2e2934fd427a96808bb294cbff54ac10287f482d",
            "aql_dosing_scheduling_metadata_v1.json" to
                "9aac5f74eb2ec437319b9b07f20446853d0fe2e9c2f5822be2314556a81d3a3e",
            "aql_dosing_status_budget_v1.json" to
                "193004782913ccaca93fa185e9b99f636218c5313ec7c53fb255f273beec948a"
        )
    }
}
