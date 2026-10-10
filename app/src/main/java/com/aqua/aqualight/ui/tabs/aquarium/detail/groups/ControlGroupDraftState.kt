package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import androidx.lifecycle.SavedStateHandle
import com.aqua.aqualight.application.devices.groups.LightGroupCompatibility
import com.aqua.aqualight.application.devices.groups.LightGroupSelection

/** Navigation draft only, stored using Bundle-compatible primitive lists. */
internal class ControlGroupDraftState(private val handle: SavedStateHandle) {
    var tankId: Long?
        get() = handle[TANK]
        set(value) { handle[TANK] = value }

    var selection: LightGroupSelection
        get() {
            val ids = handle.get<ArrayList<String>>(UIDS).orEmpty().filter { it.isNotBlank() }.distinct()
            val fields = handle.get<ArrayList<String>>(KEY).orEmpty()
            if (ids.isEmpty() || fields.size != FIELD_COUNT || fields.take(IDENTITY_FIELDS).any { it.isBlank() }) {
                return LightGroupSelection()
            }
            val channels = fields[CHANNELS].toIntOrNull()
            return if (channels == null || channels <= 0) LightGroupSelection() else LightGroupSelection(
                ids, LightGroupCompatibility(fields[PRODUCT_KEY], fields[PRODUCT_ID], fields[MODEL],
                    fields[HARDWARE], channels)
            )
        }
        set(value) {
            handle[UIDS] = ArrayList(value.deviceUids)
            handle[KEY] = value.compatibility?.let {
                arrayListOf(it.productKey, it.productId, it.model, it.hardwareRevision, it.channelCount.toString())
            } ?: arrayListOf<String>()
        }

    private companion object {
        const val TANK = "controlGroupTank"
        const val UIDS = "controlGroupDraftUids"
        const val KEY = "controlGroupDraftCompatibility"
        const val PRODUCT_KEY = 0
        const val PRODUCT_ID = 1
        const val MODEL = 2
        const val HARDWARE = 3
        const val CHANNELS = 4
        const val IDENTITY_FIELDS = 4
        const val FIELD_COUNT = 5
    }
}
