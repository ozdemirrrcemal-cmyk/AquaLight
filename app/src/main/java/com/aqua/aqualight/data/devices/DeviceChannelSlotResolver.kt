package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceFanOutputSlot
import com.aqua.aqualight.application.devices.DeviceLightChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.DeviceTemperatureSensorSlot
import com.aqua.aqualight.application.devices.DeviceTimerChannelSlot
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceLimitSet

/**
 * Materializes the fixed commercial channel topology for one already validated catalog product.
 *
 * Product names and UI titles are never inspected. Exact productKey selects a firmware profile,
 * while the profile limits must still match before any slot can be published.
 */
internal object DeviceChannelSlotResolver {

    fun resolve(product: AqlCommercialCatalogProduct): DeviceChannelSlots {
        val shape = requireNotNull(SLOT_SHAPES[product.productKey.value]) {
            "No exact channel-slot contract exists for productKey=${product.productKey.value}."
        }
        require(product.family == shape.family) {
            "Channel-slot family differs from the exact commercial product contract."
        }
        require(product.limits == shape.limits) {
            "Channel-slot counts differ from the exact commercial product limits."
        }

        val timerNamesEditable =
            AqlDeviceFeatureKey.TIMER_CHANNEL_DISPLAY_NAME in product.profile.supportedFeatures
        val dosingNamesEditable =
            AqlDeviceFeatureKey.DOSING_CHANNEL_DISPLAY_NAME in product.profile.supportedFeatures
        val coolingFanNamesEditable = product.family == DeviceFamily.COOLING &&
            AqlDeviceFeatureKey.COOLING_FAN_DISPLAY_NAME in product.profile.supportedFeatures

        val slots = DeviceChannelSlots(
            lightChannels = shape.lightChannels.mapIndexed { index, definition ->
                DeviceLightChannelSlot(
                    index = DeviceSlotIndex(index),
                    wireKey = DeviceChannelWireKey(definition.wireKey),
                    defaultDisplayName = definition.defaultDisplayName
                )
            },
            timerChannels = sequentialTimerSlots(
                count = shape.limits.timerChannelCount,
                displayNameEditable = timerNamesEditable
            ),
            dosingChannels = sequentialDosingSlots(
                count = shape.limits.dosingChannelCount,
                displayNameEditable = dosingNamesEditable
            ),
            fanOutputs = sequentialFanSlots(
                count = shape.limits.fanOutputCount,
                displayNameEditable = coolingFanNamesEditable,
                route = shape.fanRoute
            ),
            temperatureSensors = sequentialTemperatureSlots(
                count = shape.limits.temperatureSensorCount,
                route = shape.temperatureRoute
            )
        )

        slots.all.mapTo(linkedSetOf()) { slot -> slot.route }.forEach { route ->
            require(DeviceRootRoutePolicy.authorize(product, route)) {
                "Channel slot route is not authorized by the exact family catalog contract: $route"
            }
        }
        return slots
    }

    private fun sequentialTimerSlots(
        count: Int,
        displayNameEditable: Boolean
    ): List<DeviceTimerChannelSlot> = List(count) { index ->
        val slotIndex = DeviceSlotIndex(index)
        DeviceTimerChannelSlot(
            index = slotIndex,
            wireKey = DeviceChannelWireKey("channel${slotIndex.position}"),
            defaultDisplayName = "Channel ${slotIndex.position}",
            displayNameEditable = displayNameEditable
        )
    }

    private fun sequentialDosingSlots(
        count: Int,
        displayNameEditable: Boolean
    ): List<DeviceDosingChannelSlot> = List(count) { index ->
        val slotIndex = DeviceSlotIndex(index)
        DeviceDosingChannelSlot(
            index = slotIndex,
            wireKey = DeviceChannelWireKey("channel${slotIndex.position}"),
            defaultDisplayName = "Channel ${slotIndex.position}",
            displayNameEditable = displayNameEditable
        )
    }

    private fun sequentialFanSlots(
        count: Int,
        displayNameEditable: Boolean,
        route: DeviceRootRoute?
    ): List<DeviceFanOutputSlot> {
        if (count == 0) return emptyList()
        val exactRoute = requireNotNull(route) {
            "A product with fan outputs must declare one exact family fan route."
        }
        return List(count) { index ->
            val slotIndex = DeviceSlotIndex(index)
            DeviceFanOutputSlot(
                index = slotIndex,
                wireKey = DeviceChannelWireKey("fan${slotIndex.position}"),
                defaultDisplayName = "Cooling Fan ${slotIndex.position}",
                displayNameEditable = displayNameEditable,
                route = exactRoute
            )
        }
    }

    private fun sequentialTemperatureSlots(
        count: Int,
        route: DeviceRootRoute?
    ): List<DeviceTemperatureSensorSlot> {
        if (count == 0) return emptyList()
        val exactRoute = requireNotNull(route) {
            "A product with temperature sensors must declare one exact family temperature route."
        }
        return List(count) { index ->
            val slotIndex = DeviceSlotIndex(index)
            DeviceTemperatureSensorSlot(
                index = slotIndex,
                defaultDisplayName = "Temperature Sensor ${slotIndex.position}",
                route = exactRoute
            )
        }
    }
}

private data class FixedAddressableSlot(
    val wireKey: String,
    val defaultDisplayName: String
)

private data class CommercialSlotShape(
    val family: DeviceFamily,
    val limits: DeviceLimitSet,
    val lightChannels: List<FixedAddressableSlot> = emptyList(),
    val fanRoute: DeviceRootRoute? = null,
    val temperatureRoute: DeviceRootRoute? = null
)

private val SLOT_SHAPES: Map<String, CommercialSlotShape> = mapOf(
    "LIGHT_WRGB_PRO_ELITE" to CommercialSlotShape(
        family = DeviceFamily.LIGHT,
        limits = DeviceLimitSet(FOUR_SLOTS, TWO_SLOTS, ONE_SLOT, NO_SLOTS, NO_SLOTS),
        lightChannels = listOf(
            FixedAddressableSlot("white", "White"),
            FixedAddressableSlot("red", "Red"),
            FixedAddressableSlot("green", "Green"),
            FixedAddressableSlot("blue", "Blue")
        ),
        fanRoute = DeviceRootRoute.LIGHT_FAN_CONTROL,
        temperatureRoute = DeviceRootRoute.LIGHT_TEMPERATURE_PROTECTION
    ),
    "LIGHT_RGB_PRO_SLIM" to CommercialSlotShape(
        family = DeviceFamily.LIGHT,
        limits = DeviceLimitSet(THREE_SLOTS, NO_SLOTS, NO_SLOTS, NO_SLOTS, NO_SLOTS),
        lightChannels = listOf(
            FixedAddressableSlot("red", "Red"),
            FixedAddressableSlot("green", "Green"),
            FixedAddressableSlot("blue", "Blue")
        )
    ),
    "TIMER_RELAY_PRO_2" to CommercialSlotShape(
        family = DeviceFamily.TIMER,
        limits = DeviceLimitSet(NO_SLOTS, NO_SLOTS, NO_SLOTS, TWO_SLOTS, NO_SLOTS)
    ),
    "TIMER_RELAY_PRO_4" to CommercialSlotShape(
        family = DeviceFamily.TIMER,
        limits = DeviceLimitSet(NO_SLOTS, NO_SLOTS, NO_SLOTS, FOUR_SLOTS, NO_SLOTS)
    ),
    "DOSING_DOSE_PRO_2" to CommercialSlotShape(
        family = DeviceFamily.DOSING,
        limits = DeviceLimitSet(NO_SLOTS, NO_SLOTS, NO_SLOTS, NO_SLOTS, TWO_SLOTS)
    ),
    "DOSING_DOSE_PRO_4" to CommercialSlotShape(
        family = DeviceFamily.DOSING,
        limits = DeviceLimitSet(NO_SLOTS, NO_SLOTS, NO_SLOTS, NO_SLOTS, FOUR_SLOTS)
    ),
    "COOLING_COOL_PRO_1F" to CommercialSlotShape(
        family = DeviceFamily.COOLING,
        limits = DeviceLimitSet(NO_SLOTS, ONE_SLOT, ONE_SLOT, NO_SLOTS, NO_SLOTS),
        fanRoute = DeviceRootRoute.COOLING_CONTROL,
        temperatureRoute = DeviceRootRoute.COOLING_TEMPERATURE
    ),
    "COOLING_COOL_PRO_2F" to CommercialSlotShape(
        family = DeviceFamily.COOLING,
        limits = DeviceLimitSet(NO_SLOTS, TWO_SLOTS, ONE_SLOT, NO_SLOTS, NO_SLOTS),
        fanRoute = DeviceRootRoute.COOLING_CONTROL,
        temperatureRoute = DeviceRootRoute.COOLING_TEMPERATURE
    ),
    "COOLING_COOL_PRO_3F" to CommercialSlotShape(
        family = DeviceFamily.COOLING,
        limits = DeviceLimitSet(NO_SLOTS, THREE_SLOTS, ONE_SLOT, NO_SLOTS, NO_SLOTS),
        fanRoute = DeviceRootRoute.COOLING_CONTROL,
        temperatureRoute = DeviceRootRoute.COOLING_TEMPERATURE
    )
)

private const val NO_SLOTS = 0
private const val ONE_SLOT = 1
private const val TWO_SLOTS = 2
private const val THREE_SLOTS = 3
private const val FOUR_SLOTS = 4
