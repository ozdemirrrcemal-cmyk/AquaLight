package com.aqua.aqualight.data.devices.contract

import com.aqua.aqualight.data.care.smartcare.FertilizerDoseCatalog
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.expectedRuntimeModules
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelConfigPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelResetPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCustomPeriod
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCustomPeriodsProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDisplayNameMutation
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDistributedProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgram
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramMode
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingReservoirConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingTimerEvent
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingTimerProgramConfig
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AqlFirmwareInteroperabilityTest {

    @Test
    fun `android authenticated command matrix exactly matches commercial firmware`() {
        val ws = fixture("aql_ws_v1_golden.json")
        val authenticated = ws.getJSONObject("commandAccess")
            .getJSONArray("authenticated")
            .strings()
            .toSet()

        assertEquals(43, authenticated.size)
        assertEquals(authenticated, AqlWsContract.authenticatedCommandKeys())
        assertTrue("dosing.program.apply" in authenticated)
        assertTrue("dosing.channel.reset" in authenticated)
    }

    @Test
    fun `android is pinned to the commercial dosing firmware head`() {
        val interop = fixture("aql_firmware_interoperability_v1.json")
        val firmware = interop.getJSONObject("firmware")

        assertEquals("agent/dosing-program-commercialization", firmware.getString("branch"))
        assertEquals(
            "b5c63b029f74d3e458acef8169060e648db43265",
            firmware.getString("commit")
        )
        assertEquals(
            "9d030937518aae9fe2bb15f23938736a07e5024e",
            firmware.getJSONObject("commandNames").getString("blobSha")
        )
        assertEquals(
            "c18e355b1ab7553d1b5f251cf3e4662fd322ea33",
            firmware.getJSONObject("dosingCommands").getString("blobSha")
        )
    }

    @Test
    fun `dosing channel config serializer has no legacy schedule or save fields`() {
        val payload = DeviceDosingChannelConfigPayload(
            channelKey = "channel1",
            expectedRevision = 17,
            displayName = DeviceDosingDisplayNameMutation.Set("Nutrients"),
            reservoir = DeviceDosingReservoirConfig(
                trackingEnabled = true,
                capacityMl = 450.0
            )
        ).toJson()

        assertEquals(
            setOf("channelKey", "expectedRevision", "displayName", "reservoir"),
            payload.keySet()
        )
        assertEquals("channel1", payload.getString("channelKey"))
        assertEquals(17L, payload.getLong("expectedRevision"))
        assertFalse(payload.has("schedules"))
        assertFalse(payload.has("save"))
        assertFalse(payload.has("regime"))
        val reservoir = payload.getJSONObject("reservoir")
        assertEquals(setOf("trackingEnabled", "capacityMl"), reservoir.keySet())
    }

    @Test
    fun `display name clear is explicit json null`() {
        val payload = DeviceDosingChannelConfigPayload(
            channelKey = "channel1",
            expectedRevision = 18,
            displayName = DeviceDosingDisplayNameMutation.Clear
        ).toJson()

        assertTrue(payload.has("displayName"))
        assertTrue(payload.isNull("displayName"))
        assertFalse(payload.has("reservoir"))
    }

    @Test
    fun `program apply serializer mirrors all four final firmware modes`() {
        val weekdays = listOf(true, true, true, true, true, true, true)
        val programs = listOf(
            DeviceDosingProgram(
                enabled = true,
                weekdays = weekdays,
                mode = DeviceDosingProgramMode.SINGLE,
                missedDoseRecoveryEnabled = false,
                config = DeviceDosingDistributedProgramConfig(3.0, 36_000_000L)
            ),
            DeviceDosingProgram(
                enabled = true,
                weekdays = weekdays,
                mode = DeviceDosingProgramMode.HOURLY_24,
                missedDoseRecoveryEnabled = false,
                config = DeviceDosingDistributedProgramConfig(2.4, 36_900_000L)
            ),
            DeviceDosingProgram(
                enabled = true,
                weekdays = weekdays,
                mode = DeviceDosingProgramMode.CUSTOM_PERIODS,
                missedDoseRecoveryEnabled = true,
                config = DeviceDosingCustomPeriodsProgramConfig(
                    dailyDoseMl = 6.0,
                    periods = listOf(DeviceDosingCustomPeriod(36_000_000L, 39_600_000L, 3))
                )
            ),
            DeviceDosingProgram(
                enabled = true,
                weekdays = weekdays,
                mode = DeviceDosingProgramMode.TIMER,
                missedDoseRecoveryEnabled = false,
                config = DeviceDosingTimerProgramConfig(
                    events = listOf(
                        DeviceDosingTimerEvent(36_000_000L, 1.0),
                        DeviceDosingTimerEvent(50_400_000L, 5.0)
                    )
                )
            )
        )

        assertEquals(
            setOf("single", "hourly24", "customPeriods", "timer"),
            programs.map { it.mode.wireValue }.toSet()
        )

        programs.forEachIndexed { index, program ->
            val payload = DeviceDosingProgramApplyPayload(
                channelKey = "channel1",
                expectedRevision = 20L + index,
                program = program
            ).toJson()
            assertEquals(setOf("channelKey", "expectedRevision", "program"), payload.keySet())
            val programJson = payload.getJSONObject("program")
            assertEquals(
                setOf("enabled", "weekdays", "mode", "missedDoseRecoveryEnabled", "config"),
                programJson.keySet()
            )
            assertFalse(programJson.has("intervalOnMs"))
            assertFalse(programJson.has("intervalOffMs"))
            assertFalse(programJson.has("repeatCount"))
        }
    }

    @Test
    fun `channel reset serializer is revision guarded and channel scoped`() {
        val payload = DeviceDosingChannelResetPayload("channel3", 42L).toJson()
        assertEquals(setOf("channelKey", "expectedRevision"), payload.keySet())
        assertEquals("channel3", payload.getString("channelKey"))
        assertEquals(42L, payload.getLong("expectedRevision"))
    }

    @Test
    fun `program fixture modes and weekday order match Android contract`() {
        val programFixture = fixture("aql_dosing_program_v1.json")
        assertEquals(DeviceDosingRuntimeContract.SCHEMA, programFixture.getString("schema"))
        assertEquals(
            DeviceDosingProgramMode.entries.map { it.wireValue },
            programFixture.getJSONArray("supportedModes").strings()
        )
        assertEquals(
            listOf(
                "monday",
                "tuesday",
                "wednesday",
                "thursday",
                "friday",
                "saturday",
                "sunday"
            ),
            programFixture.getJSONArray("weekdayOrder").strings()
        )
    }

    @Test
    fun `dose pro products never expose standalone timer runtime`() {
        AqlCommercialDeviceCatalog.products
            .filter { it.family == DeviceFamily.DOSING }
            .forEach { product ->
                val modules = product.expectedRuntimeModules()
                assertTrue(modules.dosing)
                assertFalse(modules.timerApi)
                assertFalse(modules.timerEngine)
                assertFalse(product.profile.capabilities.standaloneTimer)
                assertEquals(0, product.profile.limits.timerChannelCount)
            }
    }

    @Test
    fun `all commercial products still resolve their exact generated runtime module contract`() {
        AqlCommercialDeviceCatalog.products.forEach { product ->
            val expected = product.expectedRuntimeModules()
            when (product.family) {
                DeviceFamily.LIGHT -> assertTrue(expected.light)
                DeviceFamily.COOLING -> assertTrue(expected.cooling)
                DeviceFamily.TIMER -> {
                    assertTrue(expected.timerApi)
                    assertTrue(expected.timerEngine)
                }
                DeviceFamily.DOSING -> {
                    assertTrue(expected.dosing)
                    assertFalse(expected.timerEngine)
                }
                DeviceFamily.UNKNOWN -> error("Commercial catalog cannot contain unknown family")
            }
        }
    }

    @Test
    fun `smart fertilizer catalog keys remain semantic not firmware wire identities`() {
        FertilizerDoseCatalog.entries.forEach { entry ->
            assertTrue(entry.key.isNotBlank())
            assertFalse(entry.key.startsWith("channel"))
        }
    }

    @Test
    fun `firmware scheduling metadata is live source of dosing limits`() {
        val metadata = fixture("aql_dosing_scheduling_metadata_v1.json")
        assertEquals("aqualight.dosing.v1", metadata.getString("contract"))
        assertEquals("firmware", metadata.getJSONObject("publication").getString("sourceOfTruth"))
        assertTrue(metadata.getJSONObject("publication").getBoolean("live"))
        assertEquals(0.001, metadata.getJSONObject("limits").getDouble("amountResolutionMl"), 0.0)
    }

    @Test
    fun `legacy dosing request types are absent from production model source`() {
        val modelSource = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/dosing/models/" +
                "DeviceDosingModels.kt"
        )
        listOf(
            "DeviceDosingScheduleConfig",
            "DeviceDosingScheduleStatus",
            "DeviceDosingConfigApplyPayload",
            "DeviceDosingChannelDosingConfig"
        ).forEach { legacy -> assertFalse(modelSource.contains(legacy)) }
        listOf(
            "DeviceDosingProgramApplyPayload",
            "DeviceDosingChannelResetPayload",
            "DeviceDosingSchedulingMetadata",
            "DeviceDosingUsageToday"
        ).forEach { canonical -> assertTrue(modelSource.contains(canonical)) }
    }

    private fun fixture(name: String): JSONObject = JSONObject(
        File(repositoryRoot(), "protocol/fixtures/$name").readText()
    )

    private fun source(relativePath: String): String =
        File(repositoryRoot(), relativePath).readText()

    private fun repositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }

    private fun org.json.JSONArray.strings(): List<String> =
        List(length()) { index -> getString(index) }

    private fun JSONObject.keySet(): Set<String> = keys().asSequence().toSet()
}
