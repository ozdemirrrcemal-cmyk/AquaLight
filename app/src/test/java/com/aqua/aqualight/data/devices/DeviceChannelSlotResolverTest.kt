package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceAddressableChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceLimitSet
import com.aqua.aqualight.data.devices.model.DeviceProductKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LongMethod", "LongParameterList", "MagicNumber")
class DeviceChannelSlotResolverTest {

    @Test
    fun `all nine products resolve exact firmware channel topology`() {
        assertEquals(EXPECTED.keys, AqlCommercialDeviceCatalog.products.mapTo(linkedSetOf()) {
            product -> product.productKey.value
        })

        AqlCommercialDeviceCatalog.products.forEach { product ->
            val expected = requireNotNull(EXPECTED[product.productKey.value])
            val slots = DeviceChannelSlotResolver.resolve(product)

            assertEquals(expected.lightKeys, slots.lightChannels.map { it.wireKey.value })
            assertEquals(expected.lightNames, slots.lightChannels.map { it.defaultDisplayName })
            assertTrue(slots.lightChannels.all { !it.displayNameEditable })
            assertTrue(slots.lightChannels.all { it.route == DeviceRootRoute.LIGHT_MANUAL })

            assertEquals(channelKeys(expected.timerCount), slots.timerChannels.map { it.wireKey.value })
            assertEquals(channelNames(expected.timerCount), slots.timerChannels.map { it.defaultDisplayName })
            assertTrue(slots.timerChannels.all { it.displayNameEditable == expected.timerEditable })
            assertTrue(slots.timerChannels.all { it.route == DeviceRootRoute.TIMER_CHANNELS })

            assertEquals(
                channelKeys(expected.dosingCount),
                slots.dosingChannels.map { it.wireKey.value }
            )
            assertEquals(
                channelNames(expected.dosingCount),
                slots.dosingChannels.map { it.defaultDisplayName }
            )
            assertTrue(slots.dosingChannels.all {
                it.displayNameEditable == expected.dosingEditable
            })
            assertTrue(slots.dosingChannels.all { it.route == DeviceRootRoute.DOSING_CHANNELS })

            assertEquals(fanKeys(expected.fanCount), slots.fanOutputs.map { it.wireKey.value })
            assertEquals(fanNames(expected.fanCount), slots.fanOutputs.map { it.defaultDisplayName })
            assertTrue(slots.fanOutputs.all { it.displayNameEditable == expected.fanEditable })
            assertTrue(slots.fanOutputs.all { it.route == expected.fanRoute })

            assertEquals(
                sensorNames(expected.temperatureCount),
                slots.temperatureSensors.map { it.defaultDisplayName }
            )
            assertEquals(
                sensorIds(expected.temperatureCount),
                slots.temperatureSensors.map { it.id.value }
            )
            assertTrue(slots.temperatureSensors.all { !it.displayNameEditable })
            assertTrue(slots.temperatureSensors.all { it.route == expected.temperatureRoute })

            assertEquals(product.limits.lightChannelCount, slots.lightChannels.size)
            assertEquals(product.limits.timerChannelCount, slots.timerChannels.size)
            assertEquals(product.limits.dosingChannelCount, slots.dosingChannels.size)
            assertEquals(product.limits.fanOutputCount, slots.fanOutputs.size)
            assertEquals(product.limits.temperatureSensorCount, slots.temperatureSensors.size)

            val addressableCount = slots.all.filterIsInstance<DeviceAddressableChannelSlot>().size
            assertEquals(slots.all.size - slots.temperatureSensors.size, addressableCount)
            assertEquals(slots.all.size, slots.all.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `resolver rejects unknown identity and limit drift`() {
        val relay = product("TIMER_RELAY_PRO_2")

        assertThrows(IllegalArgumentException::class.java) {
            DeviceChannelSlotResolver.resolve(
                relay.copy(productKey = DeviceProductKey("TIMER_RELAY_PRO_UNKNOWN"))
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            DeviceChannelSlotResolver.resolve(
                relay.copy(
                    limits = DeviceLimitSet(
                        lightChannelCount = 0,
                        fanOutputCount = 0,
                        temperatureSensorCount = 0,
                        timerChannelCount = 3,
                        dosingChannelCount = 0
                    )
                )
            )
        }
    }

    @Test
    fun `display name editability follows exact family feature tokens`() {
        val wrgb = DeviceChannelSlotResolver.resolve(product("LIGHT_WRGB_PRO_ELITE"))
        val timer = DeviceChannelSlotResolver.resolve(product("TIMER_RELAY_PRO_4"))
        val dosing = DeviceChannelSlotResolver.resolve(product("DOSING_DOSE_PRO_4"))
        val cooling = DeviceChannelSlotResolver.resolve(product("COOLING_COOL_PRO_3F"))

        assertFalse(wrgb.fanOutputs.any { it.displayNameEditable })
        assertTrue(timer.timerChannels.all { it.displayNameEditable })
        assertTrue(dosing.dosingChannels.all { it.displayNameEditable })
        assertTrue(cooling.fanOutputs.all { it.displayNameEditable })
    }

    private fun product(productKey: String): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { product ->
            product.productKey.value == productKey
        }

    private fun channelKeys(count: Int): List<String> =
        (1..count).map { position -> "channel$position" }

    private fun channelNames(count: Int): List<String> =
        (1..count).map { position -> "Channel $position" }

    private fun fanKeys(count: Int): List<String> =
        (1..count).map { position -> "fan$position" }

    private fun fanNames(count: Int): List<String> =
        (1..count).map { position -> "Cooling Fan $position" }

    private fun sensorNames(count: Int): List<String> =
        (1..count).map { position -> "Temperature Sensor $position" }

    private fun sensorIds(count: Int): List<String> =
        (1..count).map { position -> "temperature:$position" }

    private data class ExpectedSlots(
        val lightKeys: List<String> = emptyList(),
        val lightNames: List<String> = emptyList(),
        val timerCount: Int = 0,
        val dosingCount: Int = 0,
        val fanCount: Int = 0,
        val temperatureCount: Int = 0,
        val timerEditable: Boolean = false,
        val dosingEditable: Boolean = false,
        val fanEditable: Boolean = false,
        val fanRoute: DeviceRootRoute? = null,
        val temperatureRoute: DeviceRootRoute? = null
    )

    private companion object {
        val EXPECTED = linkedMapOf(
            "LIGHT_WRGB_PRO_ELITE" to ExpectedSlots(
                lightKeys = listOf("white", "red", "green", "blue"),
                lightNames = listOf("White", "Red", "Green", "Blue"),
                fanCount = 2,
                temperatureCount = 1,
                fanRoute = DeviceRootRoute.LIGHT_FAN_CONTROL,
                temperatureRoute = DeviceRootRoute.LIGHT_TEMPERATURE_PROTECTION
            ),
            "LIGHT_RGB_PRO_SLIM" to ExpectedSlots(
                lightKeys = listOf("red", "green", "blue"),
                lightNames = listOf("Red", "Green", "Blue")
            ),
            "TIMER_RELAY_PRO_2" to ExpectedSlots(
                timerCount = 2,
                timerEditable = true
            ),
            "TIMER_RELAY_PRO_4" to ExpectedSlots(
                timerCount = 4,
                timerEditable = true
            ),
            "DOSING_DOSE_PRO_2" to ExpectedSlots(
                dosingCount = 2,
                dosingEditable = true
            ),
            "DOSING_DOSE_PRO_4" to ExpectedSlots(
                dosingCount = 4,
                dosingEditable = true
            ),
            "COOLING_COOL_PRO_1F" to ExpectedSlots(
                fanCount = 1,
                temperatureCount = 1,
                fanEditable = true,
                fanRoute = DeviceRootRoute.COOLING_CONTROL,
                temperatureRoute = DeviceRootRoute.COOLING_TEMPERATURE
            ),
            "COOLING_COOL_PRO_2F" to ExpectedSlots(
                fanCount = 2,
                temperatureCount = 1,
                fanEditable = true,
                fanRoute = DeviceRootRoute.COOLING_CONTROL,
                temperatureRoute = DeviceRootRoute.COOLING_TEMPERATURE
            ),
            "COOLING_COOL_PRO_3F" to ExpectedSlots(
                fanCount = 3,
                temperatureCount = 1,
                fanEditable = true,
                fanRoute = DeviceRootRoute.COOLING_CONTROL,
                temperatureRoute = DeviceRootRoute.COOLING_TEMPERATURE
            )
        )
    }
}
