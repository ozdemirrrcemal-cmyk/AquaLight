package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeModulesParserTest {

    @Test
    fun `parses all commercial module profiles without collapsing timer api and engine`() {
        MODULE_FIXTURES.forEach { fixture ->
            val parsed = DeviceRuntimeModulesParser.parseDeviceStatus(
                validStatus(fixture = fixture)
            ).getOrThrow()

            assertEquals(fixture.family, parsed.family)
            assertEquals(fixture.modules, parsed.modules)
            assertEquals(fixture.modules.timerApi, parsed.modules.exposesStandaloneTimerApi)
            assertEquals(fixture.modules.timerEngine, parsed.modules.usesInternalTimerEngine)
        }

        val dosing = MODULE_FIXTURES.single { fixture -> fixture.family == DeviceFamily.DOSING }
        assertFalse(dosing.modules.timerApi)
        assertTrue(dosing.modules.timerEngine)
    }

    @Test
    fun `rejects missing or unknown status and module keys`() {
        val missingRoot = validStatus(DEFAULT_FIXTURE).apply { remove("runtime") }
        val unknownRoot = validStatus(DEFAULT_FIXTURE).put("legacy", true)
        val missingModule = validStatus(DEFAULT_FIXTURE).apply {
            getJSONObject("modules").remove("timerEngine")
        }
        val unknownModule = validStatus(DEFAULT_FIXTURE).apply {
            getJSONObject("modules").put("timer", true)
        }

        assertTrue(DeviceRuntimeModulesParser.parseDeviceStatus(missingRoot).isFailure)
        assertTrue(DeviceRuntimeModulesParser.parseDeviceStatus(unknownRoot).isFailure)
        assertTrue(DeviceRuntimeModulesParser.parseDeviceStatus(missingModule).isFailure)
        assertTrue(DeviceRuntimeModulesParser.parseDeviceStatus(unknownModule).isFailure)
    }

    @Test
    fun `rejects type coercion and non integral uptime`() {
        val stringBoolean = validStatus(DEFAULT_FIXTURE).apply {
            getJSONObject("modules").put("light", "true")
        }
        val numericBoolean = validStatus(DEFAULT_FIXTURE).apply {
            getJSONObject("modules").put("light", 1)
        }
        val stringUptime = validStatus(DEFAULT_FIXTURE).put("uptimeMs", "100")
        val fractionalUptime = validStatus(DEFAULT_FIXTURE).put("uptimeMs", 1.5)

        assertTrue(DeviceRuntimeModulesParser.parseDeviceStatus(stringBoolean).isFailure)
        assertTrue(DeviceRuntimeModulesParser.parseDeviceStatus(numericBoolean).isFailure)
        assertTrue(DeviceRuntimeModulesParser.parseDeviceStatus(stringUptime).isFailure)
        assertTrue(DeviceRuntimeModulesParser.parseDeviceStatus(fractionalUptime).isFailure)
    }

    @Test
    fun `requires authenticated booted status and exact websocket runtime contract`() {
        val unauthenticated = validStatus(DEFAULT_FIXTURE).put("authenticated", false)
        val wrongState = validStatus(DEFAULT_FIXTURE).put("state", "ready")
        val wrongTransport = validStatus(DEFAULT_FIXTURE).apply {
            getJSONObject("runtime").put("transport", "http")
        }
        val wrongSchema = validStatus(DEFAULT_FIXTURE).apply {
            getJSONObject("runtime").put("wsSchema", "aql.ws.v0")
        }
        val wrongPath = validStatus(DEFAULT_FIXTURE).apply {
            getJSONObject("runtime").put("wsPath", "/ws")
        }
        val wrongPort = validStatus(DEFAULT_FIXTURE).apply {
            getJSONObject("runtime").put("wsPort", 81)
        }

        listOf(
            unauthenticated,
            wrongState,
            wrongTransport,
            wrongSchema,
            wrongPath,
            wrongPort
        ).forEach { invalid ->
            assertTrue(DeviceRuntimeModulesParser.parseDeviceStatus(invalid).isFailure)
        }
    }

    @Test
    fun `requires exact commercial product envelope values`() {
        val unknownFamily = validStatus(DEFAULT_FIXTURE).apply {
            getJSONObject("product").put("family", "LIGHT")
        }
        val paddedModel = validStatus(DEFAULT_FIXTURE).apply {
            getJSONObject("product").put("model", " rgb_pro_slim")
        }
        val emptyDisplayName = validStatus(DEFAULT_FIXTURE).apply {
            getJSONObject("product").put("displayName", "")
        }

        assertTrue(DeviceRuntimeModulesParser.parseDeviceStatus(unknownFamily).isFailure)
        assertTrue(DeviceRuntimeModulesParser.parseDeviceStatus(paddedModel).isFailure)
        assertTrue(DeviceRuntimeModulesParser.parseDeviceStatus(emptyDisplayName).isFailure)
    }

    private fun validStatus(fixture: ModuleFixture): JSONObject = JSONObject()
        .put("state", "booted")
        .put("authenticated", true)
        .put("uptimeMs", UPTIME_MS)
        .put(
            "product",
            JSONObject()
                .put("productKey", fixture.productKey)
                .put("family", fixture.family.wireValue)
                .put("model", fixture.model)
                .put("displayName", fixture.displayName)
        )
        .put(
            "runtime",
            JSONObject()
                .put("transport", "websocket")
                .put("wsSchema", AqlWsContract.SCHEMA)
                .put("wsPath", AqlWsContract.DEFAULT_PATH)
                .put("wsPort", WS_PORT)
        )
        .put("modules", fixture.modules.toJson())

    private fun DeviceRuntimeModules.toJson(): JSONObject = JSONObject()
        .put("light", light)
        .put("cooling", cooling)
        .put("temperature", temperature)
        .put("timerApi", timerApi)
        .put("timerEngine", timerEngine)
        .put("dosing", dosing)
        .put("network", network)
        .put("discovery", discovery)
        .put("firmware", firmware)
        .put("system", system)

    private data class ModuleFixture(
        val productKey: String,
        val family: DeviceFamily,
        val model: String,
        val displayName: String,
        val modules: DeviceRuntimeModules
    )

    private companion object {
        const val UPTIME_MS = 123_456L
        const val WS_PORT = 80

        val DEFAULT_FIXTURE = ModuleFixture(
            productKey = "LIGHT_RGB_PRO_SLIM",
            family = DeviceFamily.LIGHT,
            model = "rgb_pro_slim",
            displayName = "RGB Pro Slim",
            modules = DeviceRuntimeModules(
                light = true,
                cooling = false,
                temperature = false,
                timerApi = false,
                timerEngine = false,
                dosing = false,
                network = true,
                discovery = true,
                firmware = true,
                system = true
            )
        )

        val MODULE_FIXTURES = listOf(
            DEFAULT_FIXTURE,
            ModuleFixture(
                productKey = "LIGHT_WRGB_PRO_ELITE",
                family = DeviceFamily.LIGHT,
                model = "wrgb_pro_elite_120",
                displayName = "WRGB Pro Elite 120",
                modules = DEFAULT_FIXTURE.modules.copy(cooling = true, temperature = true)
            ),
            ModuleFixture(
                productKey = "TIMER_RELAY_PRO_2",
                family = DeviceFamily.TIMER,
                model = "relay_pro_2",
                displayName = "Relay Pro 2",
                modules = DEFAULT_FIXTURE.modules.copy(
                    light = false,
                    timerApi = true,
                    timerEngine = true
                )
            ),
            ModuleFixture(
                productKey = "DOSING_DOSE_PRO_2",
                family = DeviceFamily.DOSING,
                model = "dose_pro_2",
                displayName = "Dose Pro 2",
                modules = DEFAULT_FIXTURE.modules.copy(
                    light = false,
                    timerEngine = true,
                    dosing = true
                )
            ),
            ModuleFixture(
                productKey = "COOLING_COOL_PRO_1F",
                family = DeviceFamily.COOLING,
                model = "cool_pro_1f",
                displayName = "Cool Pro 1 Fan",
                modules = DEFAULT_FIXTURE.modules.copy(
                    light = false,
                    cooling = true,
                    temperature = true
                )
            )
        )
    }
}
