package com.aqua.aqualight.ui.tabs.aquarium.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.aqua.aqualight.application.aquarium.AquariumIdGenerator

class LivestockPhotoFormViewModel(private val state: SavedStateHandle) : ViewModel() {
    val ownerUid: String? get() = state["livestock.owner"]
    val recordId: Long? get() = state["livestock.record"]

    fun initialize(ownerUid: String, editingId: Long) {
        if (this.ownerUid != null) return
        require(ownerUid.isNotBlank())
        state["livestock.owner"] = ownerUid
        state["livestock.record"] = editingId.takeIf { it > 0L } ?: AquariumIdGenerator.newLong()
    }
}
