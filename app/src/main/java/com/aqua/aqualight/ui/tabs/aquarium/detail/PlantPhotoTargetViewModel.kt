package com.aqua.aqualight.ui.tabs.aquarium.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** Keeps the selected record fixed throughout camera/gallery/crop and saved-state recreation. */
class PlantPhotoTargetViewModel(private val state: SavedStateHandle) : ViewModel() {
    val ownerUid: String? get() = state["photoOwnerUid"]
    val plantId: Long? get() = state["photoPlantId"]
    val isInProgress: Boolean get() = state["photoInProgress"] ?: false

    fun select(ownerUid: String, plantId: Long): Boolean {
        if (isInProgress) return false
        require(ownerUid.isNotBlank() && plantId > 0L)
        state["photoOwnerUid"] = ownerUid
        state["photoPlantId"] = plantId
        return true
    }

    fun begin(): Boolean {
        if (isInProgress || ownerUid == null || plantId == null) return false
        state["photoInProgress"] = true
        return true
    }

    fun finish() {
        state["photoInProgress"] = false
    }
}
