package com.aqua.aqualight.application.devices.groups

/** Immutable selection rules shared by clicks, drops and repository reconciliation. */
data class LightGroupSelection(
    val deviceUids: List<String> = emptyList(),
    val compatibility: LightGroupCompatibility? = null
) {
    fun add(uid: String, devices: List<TankControlGroupDevice>): LightGroupSelection {
        val key = devices.firstOrNull { it.deviceUid == uid }?.compatibility
        val compatible = compatibility == null || compatibility == key
        if (uid in deviceUids || key == null || !compatible) return this
        return LightGroupSelection(deviceUids + uid, compatibility ?: key)
    }

    fun remove(uid: String): LightGroupSelection {
        val remaining = deviceUids.filterNot { it == uid }
        return if (remaining.isEmpty()) LightGroupSelection() else copy(deviceUids = remaining)
    }

    /** Unknown metadata is temporary. Confirmed disappearance or identity change removes selection. */
    fun reconcile(devices: List<TankControlGroupDevice>): LightGroupSelection {
        val byUid = devices.associateBy { it.deviceUid }
        val remaining = deviceUids.distinct().filter { uid ->
            val item = byUid[uid]
            item != null && (item.compatibility == null || item.compatibility == compatibility)
        }
        return if (remaining.isEmpty()) LightGroupSelection() else copy(deviceUids = remaining)
    }

    fun selected(devices: List<TankControlGroupDevice>): List<TankControlGroupDevice> {
        val byUid = devices.associateBy { it.deviceUid }
        return deviceUids.mapNotNull(byUid::get)
    }

    fun available(devices: List<TankControlGroupDevice>): List<TankControlGroupDevice> =
        devices.filter { item ->
            item.deviceUid !in deviceUids &&
                (compatibility == null || item.compatibility == null || item.compatibility == compatibility)
        }

    fun isReady(devices: List<TankControlGroupDevice>): Boolean =
        compatibility != null && deviceUids.size >= 2 && selected(devices).let { items ->
            items.size == deviceUids.size && items.all { it.compatibility == compatibility }
        }
}
