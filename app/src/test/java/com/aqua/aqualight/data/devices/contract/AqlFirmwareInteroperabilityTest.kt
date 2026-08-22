package com.aqua.aqualight.data.devices.contract

import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.expectedRuntimeModules
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingFanDisplayNamePayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingMode
import com.aqua.aqualight.data.devices.runtime.modules.device.DeviceNameSetRequest
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaStartPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightChannelRegimeSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManualChannelPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManualSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProgramApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProgramDeletePayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProgramPointPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRegime
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceManualRtcPayload
import com.aqua.aqualight.data.devices.runtime.modules.time.DevicePhoneSyncPayload
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerChannelConfig
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerChannelSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRegime
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerScheduleConfig
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AqlFirmwareInteroperabilityTest {

    private val interoperability by lazy {
        loadJsonFixture(INTEROPERABILITY_FIXTURE)
    }

    private val websocketGolden by lazy {
        loadJsonFixture(WEBSOCKET_FIXTURE)
    }

    private val productCatalog by lazy {
        loadJsonFixture(PRODUCT_CATALOG_FIXTURE)
    }

    @Test
    fun `firmware commands stay exact with complete feature owned request coverage`() {
        val commandAccess = websocketGolden.getJSONObject("commandAccess")
        val authenticated = commandAccess.getJSONArray("authenticated").asStringSet()
        val public = commandAccess.getJSONArray("public").asStringSet()

        assertEquals(44, authenticated.size)
        assertTrue(public.isEmpty())
        assertEquals(public, AqlWsContract.publicCommandKeys())
        assertEquals(authenticated, AqlWsContract.authenticatedCommandKeys())

        val disconnectedModules = interoperability
            .getJSONArray("androidDisconnectedModules")
            .asStringSet()
        assertTrue(disconnectedModules.isEmpty())

        authenticated.forEach { qualifiedName ->
            val module = qualifiedName.substringBefore('.')
            val action = qualifiedName.substringAfter('.')
            assertTrue(AqlWsContract.isAuthenticatedCommand(module, action))
            assertFalse(AqlWsContract.isAuthenticatedCommand(" $module", action))
            assertFalse(AqlWsContract.isAuthenticatedCommand(module, "$action "))
            assertFalse(AqlWsContract.isAuthenticatedCommand(module.uppercase(), action))
        }

        val coverage = interoperability.getJSONObject("requestCoverage")
        val payloadless = coverage.getJSONArray("payloadlessCommands").asStringSet()
        val payloadCommands = coverage.getJSONObject("payloadCommands")
        val payloadCommandNames = payloadCommands.keySetExact()
        val coreCoverage = payloadless + payloadCommandNames

        assertTrue(payloadless.intersect(payloadCommandNames).isEmpty())

        val dosingPin = loadJsonFixture(DOSING_PIN_FIXTURE)
        assertEquals(
            FIRMWARE_COMMIT,
            dosingPin.getJSONObject("firmware").getString("commit")
        )
        val dosingContract = dosingPin.getJSONObject("contract")
        assertTrue(dosingContract.getBoolean("productionWiring"))
        val dosingCommands = dosingContract
            .getJSONArray("authenticatedActions")
            .asStringSet()
        assertEquals(14, dosingCommands.size)
        assertEquals(
            authenticated.filterTo(linkedSetOf()) { command -> command.startsWith("dosing.") },
            dosingCommands
        )
        assertTrue(coreCoverage.intersect(dosingCommands).isEmpty())
        assertEquals(authenticated, coreCoverage + dosingCommands)

        val referencedSerializers = linkedSetOf<String>()
        payloadCommandNames.forEach { command ->
            referencedSerializers += payloadCommands.getJSONArray(command).asStringSet()
        }
        assertEquals(
            interoperability.getJSONObject("serializers").keySetExact(),
            referencedSerializers
        )
    }

    @Test
    fun `all firmware events are exact typed and fail closed`() {
        val expected = interoperability.getJSONArray("events").asStringSet()
        val typed = DeviceRuntimeTypedEvent.Type.values()
            .mapTo(linkedSetOf()) { type -> "${type.module}.${type.action}" }

        assertEquals(11, expected.size)
        assertEquals(expected, AqlWsEventContract.qualifiedNames())
        assertEquals(expected, typed)

        expected.forEach { qualifiedName ->
            val module = qualifiedName.substringBefore('.')
            val action = qualifiedName.substringAfter('.')
            assertTrue(AqlWsEventContract.isRegisteredEvent(module, action))
            assertFalse(AqlWsEventContract.isRegisteredEvent(" $module", action))
            assertFalse(AqlWsEventContract.isRegisteredEvent(module, "$action "))
            assertFalse(AqlWsEventContract.isRegisteredEvent(module.uppercase(), action))
        }
    }

    @Test
    fun `every Android request serializer covers its pinned firmware fields`() {
        val actual = actualSerializerFields()
        val serializerSpecs = interoperability.getJSONObject("serializers")

        assertEquals(serializerSpecs.keySetExact(), actual.keys)
        serializerSpecs.keySetExact().forEach { serializerName ->
            val expectedFields = serializerSpecs
                .getJSONObject(serializerName)
                .getJSONArray("fields")
                .asStringSet()
            assertEquals(serializerName, expectedFields, actual.getValue(serializerName))
        }

        val hardwareOwned = interoperability
            .getJSONArray("hardwareOwnedForbiddenRequestFields")
            .asStringSet()
        actual.forEach { (serializerName, fields) ->
            assertTrue(
                "$serializerName exposes hardware-owned request fields: " +
                    fields.intersect(hardwareOwned),
                fields.intersect(hardwareOwned).isEmpty()
            )
        }

        val immutableIdentity = interoperability
            .getJSONArray("immutableIdentityRequestFields")
            .asStringSet()
        val allowedOtaEcho = interoperability
            .getJSONArray("allowedOtaIdentityEchoFields")
            .asStringSet()
        actual.forEach { (serializerName, fields) ->
            val identityFields = fields.intersect(immutableIdentity)
            if (serializerName == "DeviceFirmwareOtaStartPayload") {
                assertEquals(allowedOtaEcho, identityFields)
            } else {
                assertTrue(
                    "$serializerName exposes immutable identity fields: $identityFields",
                    identityFields.isEmpty()
                )
            }
        }

        assertFalse(otaStartPayload().getBoolean("allowInsecureHttp"))
    }

    @Test
    fun `shared golden fixtures retain pinned firmware bytes`() {
        val fixtureSpecs = interoperability.getJSONObject("fixtures")
        fixtureSpecs.keySetExact().forEach { fixtureName ->
            val expectedSha = fixtureSpecs
                .getJSONObject(fixtureName)
                .getString("sha256")
            assertEquals(fixtureName, expectedSha, sha256(resourceBytes(fixtureName)))
        }

        assertTrue(
            fixtureSpecs
                .getJSONObject(WEBSOCKET_FIXTURE)
                .getBoolean("byteIdenticalWithFirmware")
        )
        assertTrue(
            fixtureSpecs
                .getJSONObject(COOLING_FIXTURE)
                .getBoolean("byteIdenticalWithFirmware")
        )
    }

    @Test
    fun `all four families and nine commercial SKUs match the firmware export`() {
        val fixtureProducts = productCatalog.getJSONArray("products").asObjectList()
        val fixtureProfiles = productCatalog.getJSONObject("profiles")
        val actualProducts = AqlCommercialDeviceCatalog.products.associateBy {
            product -> product.productKey.value
        }

        assertEquals(9, fixtureProducts.size)
        assertEquals(
            fixtureProducts.mapTo(linkedSetOf()) { it.getString("productKey") },
            actualProducts.keys
        )
        assertEquals(
            setOf("light", "timer", "dosing", "cooling"),
            actualProducts.values.mapTo(linkedSetOf()) { product -> product.family.wireValue }
        )

        fixtureProducts.forEach { expected ->
            val product = actualProducts.getValue(expected.getString("productKey"))
            val profile = fixtureProfiles.getJSONObject(expected.getString("profile"))

            assertProductIdentity(expected, product)
            assertProductLimits(expected.getJSONObject("limits"), product)
            assertProductCapabilities(profile.getJSONObject("capabilities"), product)
            assertProductProfileSets(profile, product)
            assertProductRuntimeModules(product)
        }
    }

    private fun assertProductIdentity(
        expected: JSONObject,
        product: AqlCommercialCatalogProduct
    ) {
        assertEquals(expected.getString("productId"), product.productId.value)
        assertEquals(expected.getString("family"), product.family.wireValue)
        assertEquals(expected.getString("line"), product.line.value)
        assertEquals(expected.getString("model"), product.model.value)
        assertEquals(expected.getString("displayName"), product.displayName)
        assertEquals(expected.getString("skuId"), product.skuId.value)
        assertEquals(expected.getString("skuCode"), product.skuCode.value)
        assertEquals(expected.getString("hardwareRevision"), product.hardwareRevision.value)
    }

    private fun assertProductLimits(
        expected: JSONObject,
        product: AqlCommercialCatalogProduct
    ) {
        assertEquals(expected.getInt("lightChannelCount"), product.limits.lightChannelCount)
        assertEquals(expected.getInt("fanOutputCount"), product.limits.fanOutputCount)
        assertEquals(
            expected.getInt("temperatureSensorCount"),
            product.limits.temperatureSensorCount
        )
        assertEquals(expected.getInt("timerChannelCount"), product.limits.timerChannelCount)
        assertEquals(expected.getInt("dosingChannelCount"), product.limits.dosingChannelCount)
    }

    private fun assertProductCapabilities(
        expected: JSONObject,
        product: AqlCommercialCatalogProduct
    ) {
        val actual = product.profile.capabilities
        assertEquals(expected.getBoolean("light"), actual.light)
        assertEquals(expected.getBoolean("manualLight"), actual.manualLight)
        assertEquals(expected.getBoolean("lightProgram"), actual.lightProgram)
        assertEquals(expected.getBoolean("lightPresets"), actual.lightPresets)
        assertEquals(expected.getBoolean("lightSimulation"), actual.lightSimulation)
        assertEquals(expected.getBoolean("fan"), actual.fan)
        assertEquals(expected.getBoolean("cooling"), actual.cooling)
        assertEquals(expected.getBoolean("temperature"), actual.temperature)
        assertEquals(expected.getBoolean("standaloneTimer"), actual.standaloneTimer)
        assertEquals(expected.getBoolean("dosing"), actual.dosing)
        assertEquals(expected.getBoolean("timeSync"), actual.timeSync)
        assertEquals(expected.getBoolean("ota"), actual.ota)
    }

    private fun assertProductProfileSets(
        expected: JSONObject,
        product: AqlCommercialCatalogProduct
    ) {
        assertEquals(
            expected.getJSONArray("supportedFeatures").asStringSet(),
            product.profile.supportedFeatures.mapTo(linkedSetOf()) { it.wireValue }
        )
        assertEquals(
            expected.getJSONArray("supportedScreens").asStringSet(),
            product.profile.supportedScreens.mapTo(linkedSetOf()) { it.wireValue }
        )
        assertEquals(
            expected.getJSONArray("expectedMenuFeatures").asStringSet(),
            product.profile.expectedMenuFeatureNames
        )
    }

    private fun assertProductRuntimeModules(product: AqlCommercialCatalogProduct) {
        val modules = product.expectedRuntimeModules()
        assertEquals(product.family == DeviceFamily.LIGHT, modules.light)
        assertEquals(product.profile.capabilities.cooling, modules.cooling)
        assertEquals(product.profile.capabilities.temperature, modules.temperature)
        assertEquals(product.family == DeviceFamily.TIMER, modules.timerApi)
        assertEquals(product.family == DeviceFamily.TIMER, modules.timerEngine)
        assertEquals(product.family == DeviceFamily.DOSING, modules.dosing)
        assertTrue(modules.network)
        assertTrue(modules.discovery)
        assertTrue(modules.firmware)
        assertTrue(modules.system)
    }

    private fun actualSerializerFields(): Map<String, Set<String>> =
        linkedMapOf<String, Set<String>>().apply {
            putAll(coolingSerializerFields())
            putAll(firmwareSerializerFields())
            putAll(lightSerializerFields())
            putAll(deviceAndTimeSerializerFields())
            putAll(timerSerializerFields())
        }

    private fun coolingSerializerFields(): Map<String, Set<String>> {
        val fan = DeviceCoolingFanDisplayNamePayload("fan-1", "Front fan")
        return linkedMapOf(
            "DeviceCoolingConfigApplyPayload" to DeviceCoolingConfigApplyPayload(
                mode = DeviceCoolingMode.AUTO,
                minTemperatureC = 24.0,
                maxTemperatureC = 27.0,
                fans = listOf(fan)
            ).toJson().keySetExact(),
            "DeviceCoolingFanDisplayNamePayload" to fan.toJson().keySetExact()
        )
    }

    private fun firmwareSerializerFields(): Map<String, Set<String>> = linkedMapOf(
        "DeviceFirmwareOtaStartPayload" to otaStartPayload().keySetExact()
    )

    private fun lightSerializerFields(): Map<String, Set<String>> {
        val percent = DeviceLightManualChannelPayload("red", percent = 50.0).toJson()
        val value = DeviceLightManualChannelPayload("green", value = 0.5).toJson()
        val pointByMillis = DeviceLightProgramPointPayload(timeMs = 0L, percent = 50.0).toJson()
        val pointByText = DeviceLightProgramPointPayload(time = "12:00", value = 0.5).toJson()

        return linkedMapOf(
            "DeviceLightChannelRegimeSetPayload" to DeviceLightChannelRegimeSetPayload(
                "red",
                DeviceLightRegime.AUTO
            ).toJson().keySetExact(),
            "DeviceLightManualChannelPayload" to unionKeys(percent, value),
            "DeviceLightManualSetPayload" to DeviceLightManualSetPayload(
                channels = listOf(DeviceLightManualChannelPayload("red", percent = 50.0))
            ).toJson().keySetExact(),
            "DeviceLightProgramApplyPayload" to DeviceLightProgramApplyPayload(
                channelKey = "red",
                points = listOf(DeviceLightProgramPointPayload(timeMs = 0L, percent = 50.0)),
                programIndex = 0
            ).toJson().keySetExact(),
            "DeviceLightProgramDeletePayload" to
                DeviceLightProgramDeletePayload(0).toJson().keySetExact(),
            "DeviceLightProgramPointPayload" to unionKeys(pointByMillis, pointByText),
            "DeviceLightTemperatureProtectionSetPayload" to
                DeviceLightTemperatureProtectionSetPayload(60.0).toJson().keySetExact()
        )
    }

    private fun deviceAndTimeSerializerFields(): Map<String, Set<String>> {
        val rtc = DeviceManualRtcPayload(
            year = 2026,
            month = 8,
            day = 2,
            weekday = 1,
            hour = 12,
            minute = 30,
            second = 15,
            timezoneId = "Europe/Istanbul",
            posixTimeZone = "<+03>-3",
            utcOffsetMinutes = 180
        ).toJson()

        return linkedMapOf(
            "DeviceManualRtcPayload" to rtc.keySetExact(),
            "DeviceManualRtcPayload.parts" to rtc.getJSONObject("parts").keySetExact(),
            "DeviceNameSetRequest" to DeviceNameSetRequest("Display tank").toJson().keySetExact(),
            "DevicePhoneSyncPayload" to DevicePhoneSyncPayload(
                epochMillis = 1_775_304_000_000L,
                timezoneId = "Europe/Istanbul",
                posixTimeZone = "<+03>-3",
                utcOffsetMinutes = 180
            ).toJson().keySetExact(),
            "DeviceTimeConfigApplyPayload" to DeviceTimeConfigApplyPayload(
                timezoneId = "Europe/Istanbul",
                posixTimeZone = "<+03>-3",
                utcOffsetMinutes = 180
            ).toJson().keySetExact()
        )
    }

    private fun timerSerializerFields(): Map<String, Set<String>> {
        val channel = DeviceTimerChannelConfig(
            channelKey = "relay-1",
            displayName = "Filter",
            regime = DeviceTimerRegime.AUTO
        )
        val schedule = DeviceTimerScheduleConfig(
            enabled = true,
            name = "Day",
            channelKey = "relay-1",
            weekdays = WEEKDAYS,
            startTimeMs = 1_000L,
            intervalOnMs = 1_000L,
            intervalOffMs = 1_000L,
            repeatCount = 1
        )

        return linkedMapOf(
            "DeviceTimerChannelConfig" to channel.toJson().keySetExact(),
            "DeviceTimerChannelSetPayload" to
                DeviceTimerChannelSetPayload(
                    "relay-1",
                    DeviceTimerRegime.AUTO
                ).toJson().keySetExact(),
            "DeviceTimerConfigApplyPayload" to DeviceTimerConfigApplyPayload(
                channels = listOf(channel),
                schedules = listOf(schedule)
            ).toJson().keySetExact(),
            "DeviceTimerScheduleConfig" to schedule.toJson().keySetExact()
        )
    }

    private fun otaStartPayload(): JSONObject = DeviceFirmwareOtaStartPayload(
        url = "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
            "releases/download/dosing_dose_pro_2-v6.0.1/" +
            "AquaLight-dosing_dose_pro_2-v6.0.1-ota.bin",
        version = "6.0.1",
        sha256 = "ab".repeat(32),
        expectedSize = 1_024,
        productKey = "LIGHT_WRGB_PRO_ELITE",
        productId = "com.aqualight.light.wrgb_pro_elite",
        model = "wrgb_pro_elite_120",
        hardwareRevision = "2.0"
    ).toJson()

    private fun loadJsonFixture(name: String): JSONObject = JSONObject(
        resourceBytes(name).toString(Charsets.UTF_8)
    )

    private fun resourceBytes(name: String): ByteArray = checkNotNull(
        javaClass.getResourceAsStream("/$name")
    ) { "Missing Stage 10 fixture: $name" }.use { stream -> stream.readBytes() }

    private fun JSONObject.keySetExact(): Set<String> {
        val result = linkedSetOf<String>()
        val iterator = keys()
        while (iterator.hasNext()) {
            result += iterator.next()
        }
        return result
    }

    private fun JSONArray.asStringSet(): Set<String> = (0 until length())
        .mapTo(linkedSetOf()) { index -> getString(index) }

    private fun JSONArray.asObjectList(): List<JSONObject> = (0 until length())
        .map { index -> getJSONObject(index) }

    private fun unionKeys(vararg objects: JSONObject): Set<String> = objects
        .flatMapTo(linkedSetOf()) { json -> json.keySetExact() }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val alphabet = "0123456789abcdef"
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(alphabet[value ushr 4])
                append(alphabet[value and 0x0f])
            }
        }
    }

    private companion object {
        const val INTEROPERABILITY_FIXTURE = "aql_firmware_interoperability_v1.json"
        const val WEBSOCKET_FIXTURE = "aql_ws_v1_golden.json"
        const val COOLING_FIXTURE = "aql_cooling_temperature_telemetry_v1.json"
        const val PRODUCT_CATALOG_FIXTURE = "aql_product_catalog_v1.json"
        const val DOSING_PIN_FIXTURE = "aql_android_dosing_v1_pin.json"
        const val FIRMWARE_COMMIT = "751cdc2e497531b8f754b59b4a2ae3828aaf9b52"

        val WEEKDAYS = listOf(true, false, false, false, false, false, false)
    }
}
