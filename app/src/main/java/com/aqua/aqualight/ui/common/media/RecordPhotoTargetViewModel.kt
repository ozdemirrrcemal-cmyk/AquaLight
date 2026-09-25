package com.aqua.aqualight.ui.common.media

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** Keeps the selected record fixed throughout camera/gallery/crop and saved-state recreation. */
class RecordPhotoTargetViewModel(private val state: SavedStateHandle) : ViewModel() {
    val ownerUid: String? get() = state["photoOwnerUid"]
    val recordId: Long? get() = state["photoRecordId"]
    val isInProgress: Boolean get() = state["photoInProgress"] ?: false

    fun select(ownerUid: String, recordId: Long): Boolean {
        if (isInProgress) return false
        require(ownerUid.isNotBlank() && recordId > 0L)
        state["photoOwnerUid"] = ownerUid
        state["photoRecordId"] = recordId
        return true
    }

    fun begin(): Boolean {
        if (isInProgress || ownerUid == null || recordId == null) return false
        state["photoInProgress"] = true
        return true
    }

    fun finish() {
        state["photoInProgress"] = false
    }
}
