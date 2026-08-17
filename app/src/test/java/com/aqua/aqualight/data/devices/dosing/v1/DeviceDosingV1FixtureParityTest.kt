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
    fun `canonical and derived firmware fixtures remain byte identical to the reviewed revision`() {
        EXPECTED_SHA256.forEach { (name, expected) ->
            assertEquals(name, expected, sha256(resourceBytes(name)))
        }
    }

    @Test
    fun `pin records reviewed firmware sources fixtures fourteen actions and production wiring`() {
        val pin = resourceJson(PIN_FIXTURE)
        val firmware = pin.getJSONObject("firmware")
        val contract = pin.getJSONObject("contract")
        val actions = contract.getJSONArray("authenticatedActions")
        val derived = pin.getJSONObject("derivedFixtures")
            .getJSONObject(CHANNEL_STATUS_FIXTURE)

        assertEquals(FIRMWARE_COMMIT, firmware.getString("commit"))
        assertEquals(PINNED_SOURCE_BLOBS, firmware.getJSONObject("sources").stringMap())
        assertEquals(PINNED_FIXTURE_BLOBS, pin.getJSONObject("fixtures").stringMap())
        assertEquals(DERIVED_CHANNEL_STATUS_BLOB, derived.getString("blob"))
        assertEquals(STATUS_CODEC_PATH, derived.getString("derivedFrom"))
        assertEquals(PINNED_SOURCE_BLOBS.getValue(STATUS_CODEC_PATH), derived.getString("sourceBlob"))
        assertEquals(FIRMWARE_COMMIT, derived.getString("firmwareCommit"))
        assertEquals(DeviceDosingV1Contract.SCHEMA, contract.getString("schema"))
        assertEquals(DeviceDosingV1Contract.SCHEMA_VERSION, contract.getLong("schemaVersion"))
        assertTrue(contract.getBoolean("productionWiring"))
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
        assertEquals(
            "Trace Elements",
            fixture.getJSONObject("commands")
                .getJSONObject("confirm")
                .getJSONObject("request")
                .getString("displayName")
        )
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

    private fun JSONObject.stringMap(): Map<String, String> =
        keys().asSequence().associateWith { key -> getString(key) }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }

    private companion object {
        const val PIN_FIXTURE = "aql_android_dosing_v1_pin.json"
        const val CHANNEL_STATUS_FIXTURE = "aql_dosing_channel_status_v1.json"
        const val FIRMWARE_COMMIT = "e198a893d909c06c540452165cfe4e8c21fd6434"
        const val STATUS_CODEC_PATH = "src/modules/dosing/AqlDosingStatusCodec.hpp"
        const val DERIVED_CHANNEL_STATUS_BLOB = "aa6721ab881de34419c09e5769d70366af36d3d5"

        val PINNED_SOURCE_BLOBS = linkedMapOf(
            "src/api/v1/commands/AqlDosingCommands.hpp" to
                "5c0201f9a4e09f93f4dd54af8c1fba9dec167105",
            "src/api/v1/commands/AqlDosingProgressCommands.hpp" to
                "8700e785bdd2e747abea3b09eff97755e2addad0",
            "src/modules/dosing/AqlDosingProgramApiCodec.hpp" to
                "7f4eb5d41108a962bc9476fef6a3895327c1d26e",
            STATUS_CODEC_PATH to "77524043931c58bb90d3e84f628f64acca97d3a2",
            "src/modules/dosing/AqlDosingSchedulingMetadataCodec.hpp" to
                "cab751e2d508651d17550c48314707524053f995",
            "src/modules/dosing/AqlDosingRuntimeEvent.hpp" to
                "0b40b1eff35af48976f95fedba5a2854885f2439",
            "src/modules/dosing/AqlDosingRuntimeService.hpp" to
                "e5d811ad19cbc72c32a4c1f13ca1f23689bcf91a"
        )

        val PINNED_FIXTURE_BLOBS = linkedMapOf(
            "aql_dosing_calibration_v1.json" to
                "99b538f38cb30db2d5ec6baff16898a889ba3845",
            "aql_dosing_persistence_v1.json" to
                "a6f3a38c0b701493139858a76d674296640a0dd0",
            "aql_dosing_program_v1.json" to
                "809a64d12a577be7757d8570e400009c9636adad",
            "aql_dosing_progress_budget_v1.json" to
                "a9f71db11191d42d76e2951bb09f305229140399",
            "aql_dosing_scheduling_metadata_v1.json" to
                "ef1d2c344c12606bb6aaf322142af8179eae0869",
            "aql_dosing_status_budget_v1.json" to
                "f62cc6d2bf9644020498e9eaca80f1c90c4b87e0"
        )

        val EXPECTED_SHA256 = linkedMapOf(
            "aql_dosing_calibration_v1.json" to
                "3a6ca527de4b7dc9ead427e5a85b382d5e0069a93f46f258f4f6da3f06cf060d",
            CHANNEL_STATUS_FIXTURE to
                "ac48a094e5127e54a3a51a66a1487f81ecb5993241fa0ccb7f5669cef7af752e",
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
