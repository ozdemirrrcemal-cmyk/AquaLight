package com.aqua.aqualight.ui.tabs.aquarium.detail

import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentTankLivestockFormBinding
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.ui.common.media.TankRecordPhotoFragment
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A photo is only a draft until the form's save action commits fields and media together. */
abstract class TankLivestockPhotoFormFragment : TankRecordPhotoFragment(
    R.layout.fragment_tank_livestock_form, AppMediaScope.LIVESTOCK,
    R.string.aquarium_livestock_photo_title, R.string.aquarium_livestock_photo_crop_title
) {
    protected val photoDraft: LivestockPhotoFormViewModel by viewModels()

    protected fun setupLivestockPhoto(binding: FragmentTankLivestockFormBinding, editingId: Long) {
        photoDraft.initialize(requireContext().requireAppContainer()
            .authenticatedOwnerIdentity.requireOwnerUid(), editingId)
        if (editingId <= 0L) mediaFlow.initializeSelection(null)
        setupPhotoSourceResultListener()
        binding.livestockPhotoButton.setOnClickListener {
            if (mediaFlow.selection.value.initialized) {
                showRecordPhotoSource(requireNotNull(photoDraft.recordId), requireNotNull(photoDraft.ownerUid))
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mediaFlow.selection.collect { selection ->
                    binding.ivLifeIconPreview.bindRecordPhoto(selection.selectedUri)
                }
            }
        }
    }

    override suspend fun onPhotoSelected(photoUri: String?) = Unit

    protected suspend fun saveLivestockPhoto(
        viewModel: AquariumTankViewModel,
        livestock: AquariumLivestock,
        isNew: Boolean
    ) {
        val selection = mediaFlow.selection.value
        check(!photoTarget.isInProgress && selection.initialized) { "Photo selection is not ready." }
        runCatching {
            viewModel.saveLivestockWithPhoto(
                photoTankId, livestock.copy(photoUri = selection.selectedUri),
                requireNotNull(photoDraft.ownerUid), isNew, selection.hasPendingChange
            )
            mediaFlow.commitSelection(deletePersistedMedia = false)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            mediaFlow.rollbackSelection()
        }.getOrThrow()
    }
}
