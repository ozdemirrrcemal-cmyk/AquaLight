package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.databinding.FragmentLivestockHealthObservationBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun discardLivestockObservationPhotos(
    uris: MutableList<String?>,
    binding: FragmentLivestockHealthObservationBinding?,
    scope: CoroutineScope,
    rollbackPendingMedia: suspend (String?) -> Unit
) {
    val pending = uris.filterNotNull()
    uris.indices.forEach { uris[it] = null }
    binding?.renderObservationPhotoSlots(uris)
    scope.launch {
        pending.forEach { uri -> rollbackPendingMedia(uri) }
    }
}
