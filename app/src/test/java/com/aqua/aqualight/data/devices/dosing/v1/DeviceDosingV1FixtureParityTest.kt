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
    fun `program apply fixture matches calendar day hourly handler contract`() {
        val fixture = resourceJson("aql_dosing_program_v1.json")
        val successFields = fixture
            .getJSONObject("programApply")
            .getJSONArray("successFields")
        val hourly = fixture.getJSONObject("examples").getJSONObject("hourly24")
        val config = hourly.getJSONObject("config")
        val invariants = hourly.getJSONObject("invariants")

        assertEquals(
            listOf("operation", "channelKey", "saved", "event", "channel"),
            List(successFields.length(), successFields::getString)
        )
        assertEquals(15, config.getInt("minuteOfHour"))
        assertFalse(config.has("startTimeMs"))
        assertEquals(24, invariants.getInt("occurrenceCount"))
        assertEquals(900_000L, invariants.getJSONObject("first").getLong("timeMs"))
        assertEquals(83_700_000L, invariants.getJSONObject("last").getLong("timeMs"))
        assertEquals(0, invariants.getJSONObject("first").getInt("programDayOffset"))
        assertEquals(0, invariants.getJSONObject("last").getInt("programDayOffset"))
        assertTrue(invariants.getBoolean("allOccurrencesStayOnSelectedCalendarDay"))
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
        const val FIRMWARE_COMMIT = "dc89a37262ba982c577db0812eeb8f94ffd18e12"
        const val STATUS_CODEC_PATH = "src/modules/dosing/AqlDosingStatusCodec.hpp"
        const val DERIVED_CHANNEL_STATUS_BLOB = "ea7e8d0ae8a846441edd51e9d24a26fbf9da65db"

        val PINNED_SOURCE_BLOBS = linkedMapOf(
            "src/api/v1/commands/AqlDosingCommands.hpp" to
                "5c0201f9a4e09f93f4dd54af8c1fba9dec167105",
            "src/api/v1/commands/AqlDosingProgressCommands.hpp" to
                "8700e785bdd2e747abea3b09eff97755e2addad0",
            "src/modules/dosing/AqlDosingProgramApiCodec.hpp" to
                "33f4dead13c82f7966d58c1ba74054e181dab40c",
            STATUS_CODEC_PATH to "29a989b9f008a6ca39fb8ac7182915e87e794855",
            "src/modules/dosing/AqlDosingSchedulingMetadataCodec.hpp" to
                "cab751e2d508651d17550c48314707524053f995",
            "src/modules/dosing/AqlDosingRuntimeEvent.hpp" to
                "0b40b1eff35af48976f95fedba5a2854885f2439",
            "src/modules/dosing/AqlDosingRuntimeService.hpp" to
                "aa43e46ad16c8552c29e3526101f8e8474dee769"
        )

        val PINNED_FIXTURE_BLOBS = linkedMapOf(
            "aql_dosing_calibration_v1.json" to
                "bb7ce3da02db64e6c83ef755ca416a137e5bab60",
            "aql_dosing_persistence_v1.json" to
                "fcdb64581dd7e944f4bb9710e6ec9eaad40f8bb6",
            "aql_dosing_program_v1.json" to
                "ee09ae4231899511aa04ad970f78f3232b0db2e8",
            "aql_dosing_progress_budget_v1.json" to
                "a9f71db11191d42d76e2951bb09f305229140399",
            "aql_dosing_scheduling_metadata_v1.json" to
                "ef1d2c344c12606bb6aaf322142af8179eae0869",
            "aql_dosing_status_budget_v1.json" to
                "f62cc6d2bf9644020498e9eaca80f1c90c4b87e0"
        )

        val EXPECTED_SHA256 = linkedMapOf(
            "aql_dosing_calibration_v1.json" to
                "34b789082bc4d8417dc938184e60d97c183b52531224dc32e25fdcf46332bef5",
            CHANNEL_STATUS_FIXTURE to
                "5af13c967c57208be8ee774fbe614de493b29df4c9700df0a2d611a7f7abd9ce",
            "aql_dosing_persistence_v1.json" to
                "72f38f699c07f55774dbc9ff7de72bac06f6bd1868779cc3774ef241b813ef1e",
            "aql_dosing_program_v1.json" to
                "484b69b418b2225c62ebebbba5ace647742004096a1e942b970033708b30a65d",
            "aql_dosing_progress_budget_v1.json" to
                "42c84529351a2aee5b6d29ae2e2934fd427a96808bb294cbff54ac10287f482d",
            "aql_dosing_scheduling_metadata_v1.json" to
                "9aac5f74eb2ec437319b9b07f20446853d0fe2e9c2f5822be2314556a81d3a3e",
            "aql_dosing_status_budget_v1.json" to
                "193004782913ccaca93fa185e9b99f636218c5313ec7c53fb255f273beec948a"
        )
    }
}
