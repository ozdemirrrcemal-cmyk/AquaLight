package com.aqua.aqualight.application.devices

@JvmInline
value class DeviceSlotIndex(val zeroBased: Int) {
    init {
        require(zeroBased >= 0) { "Device slot index must not be negative." }
    }

    val position: Int
        get() = zeroBased + 1
}

@JvmInline
value class DeviceChannelWireKey(val value: String) {
    init {
        require(CHANNEL_WIRE_KEY_PATTERN.matches(value)) {
            "Device channel wire key must use the exact lowercase firmware format."
        }
    }
}

@JvmInline
value class DeviceChannelSlotId(val value: String) {
    init {
        require(CHANNEL_SLOT_ID_PATTERN.matches(value)) {
            "Device channel slot id has an invalid stable format."
        }
    }
}

sealed interface DeviceChannelSlot {
    val id: DeviceChannelSlotId
    val index: DeviceSlotIndex
    val defaultDisplayName: String
    val displayNameEditable: Boolean
    val route: DeviceRootRoute
}

sealed interface DeviceAddressableChannelSlot : DeviceChannelSlot {
    val wireKey: DeviceChannelWireKey
}

data class DeviceLightChannelSlot(
    override val index: DeviceSlotIndex,
    override val wireKey: DeviceChannelWireKey,
    override val defaultDisplayName: String
) : DeviceAddressableChannelSlot {
    init {
        requireExactDisplayName(defaultDisplayName)
    }

    override val id: DeviceChannelSlotId = DeviceChannelSlotId("light:${wireKey.value}")
    override val displayNameEditable: Boolean = false
    override val route: DeviceRootRoute = DeviceRootRoute.LIGHT_MANUAL
}

data class DeviceTimerChannelSlot(
    override val index: DeviceSlotIndex,
    override val wireKey: DeviceChannelWireKey,
    override val defaultDisplayName: String,
    override val displayNameEditable: Boolean
) : DeviceAddressableChannelSlot {
    init {
        requireExactDisplayName(defaultDisplayName)
    }

    override val id: DeviceChannelSlotId = DeviceChannelSlotId("timer:${wireKey.value}")
    override val route: DeviceRootRoute = DeviceRootRoute.TIMER_CHANNELS
}

data class DeviceDosingChannelSlot(
    override val index: DeviceSlotIndex,
    override val wireKey: DeviceChannelWireKey,
    override val defaultDisplayName: String,
    override val displayNameEditable: Boolean
) : DeviceAddressableChannelSlot {
    init {
        requireExactDisplayName(defaultDisplayName)
    }

    override val id: DeviceChannelSlotId = DeviceChannelSlotId("dosing:${wireKey.value}")
    override val route: DeviceRootRoute = DeviceRootRoute.DOSING_CHANNELS
}

data class DeviceFanOutputSlot(
    override val index: DeviceSlotIndex,
    override val wireKey: DeviceChannelWireKey,
    override val defaultDisplayName: String,
    override val displayNameEditable: Boolean,
    override val route: DeviceRootRoute
) : DeviceAddressableChannelSlot {
    init {
        requireExactDisplayName(defaultDisplayName)
        require(
            route == DeviceRootRoute.LIGHT_FAN_CONTROL ||
                route == DeviceRootRoute.COOLING_CONTROL
        ) { "Fan output slots require an exact light or cooling family route." }
    }

    override val id: DeviceChannelSlotId = DeviceChannelSlotId("fan:${wireKey.value}")
}

data class DeviceTemperatureSensorSlot(
    override val index: DeviceSlotIndex,
    override val defaultDisplayName: String,
    override val route: DeviceRootRoute
) : DeviceChannelSlot {
    init {
        requireExactDisplayName(defaultDisplayName)
        require(
            route == DeviceRootRoute.LIGHT_TEMPERATURE_PROTECTION ||
                route == DeviceRootRoute.COOLING_TEMPERATURE
        ) { "Temperature sensor slots require an exact light or cooling family route." }
    }

    override val id: DeviceChannelSlotId = DeviceChannelSlotId(
        "temperature:${index.position}"
    )
    override val displayNameEditable: Boolean = false
}

data class DeviceChannelSlots(
    val lightChannels: List<DeviceLightChannelSlot>,
    val timerChannels: List<DeviceTimerChannelSlot>,
    val dosingChannels: List<DeviceDosingChannelSlot>,
    val fanOutputs: List<DeviceFanOutputSlot>,
    val temperatureSensors: List<DeviceTemperatureSensorSlot>
) {
    val all: List<DeviceChannelSlot> = buildList {
        addAll(lightChannels)
        addAll(timerChannels)
        addAll(dosingChannels)
        addAll(fanOutputs)
        addAll(temperatureSensors)
    }

    init {
        requireContiguous(lightChannels, "lightChannels")
        requireContiguous(timerChannels, "timerChannels")
        requireContiguous(dosingChannels, "dosingChannels")
        requireContiguous(fanOutputs, "fanOutputs")
        requireContiguous(temperatureSensors, "temperatureSensors")

        val slotIds = all.map { slot -> slot.id.value }
        require(slotIds.size == slotIds.toSet().size) {
            "Device channel slot ids must be unique inside one product."
        }

        val wireKeys = all.filterIsInstance<DeviceAddressableChannelSlot>()
            .map { slot -> slot.wireKey.value }
        require(wireKeys.size == wireKeys.toSet().size) {
            "Addressable channel wire keys must be unique inside one product."
        }
    }

    companion object {
        val EMPTY = DeviceChannelSlots(
            lightChannels = emptyList(),
            timerChannels = emptyList(),
            dosingChannels = emptyList(),
            fanOutputs = emptyList(),
            temperatureSensors = emptyList()
        )
    }
}

private fun requireExactDisplayName(value: String) {
    require(value.isNotEmpty()) { "Device slot default display name must not be empty." }
    require(!value.first().isWhitespace() && !value.last().isWhitespace()) {
        "Device slot default display name must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) {
        "Device slot default display name must not contain control characters."
    }
}

private fun requireContiguous(
    slots: List<DeviceChannelSlot>,
    label: String
) {
    slots.forEachIndexed { expectedIndex, slot ->
        require(slot.index.zeroBased == expectedIndex) {
            "$label must use contiguous zero-based slot indexes."
        }
    }
}

private val CHANNEL_WIRE_KEY_PATTERN = Regex("^[a-z][a-z0-9]*$")
private val CHANNEL_SLOT_ID_PATTERN = Regex("^[a-z]+:[a-z0-9]+$")
